package io.github.terminalis.machlauncher

import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val REDRAW_MS = 8 * 60 * 60 * 1000L
private const val IMAGE_TIMEOUT_MS = 10_000

const val PLAY_NEXT_SOURCE = "play_next"

data class RecommendationApp(val packageName: String, val name: String)

class Recommendation(data: Map<String, Any?>, val source: String) {
    val id = data["id"] as Long
    val packageName = data["packageName"] as String
    val appName = data["appName"] as String
    val title = data["title"] as String
    val description = (data["description"] as String).trim()
    val posterUri = data["posterUri"] as String
    val thumbnailUri = data["thumbnailUri"] as String?
    val intentUri = data["intentUri"] as String
    val progressPercent = data["progressPercent"] as Int
}

class RecommendationsService(
    private val channel: AndroidChannel,
    private val settingsService: SettingsService,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
    private val random: Random = Random.Default,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ChangeNotifier() {
    private val posters = HashMap<String, ByteArray>()
    private var drawJob: Job? = null
    private var refreshes = 0
    private var sources: List<String> = emptyList()
    private var titleCount = 0

    var hasPermission = true
        private set
    var apps: List<RecommendationApp> = emptyList()
        private set
    var picks: List<Recommendation> = emptyList()
        private set
    var activeIndex = 0
        private set

    init {
        refresh()
    }

    val showsPermissionBanner: Boolean get() = !hasPermission

    val hasTitles: Boolean get() = picks.isNotEmpty()

    val activeItem: Recommendation get() = picks[activeIndex]

    val activePoster: ByteArray? get() = if (hasTitles) posters[activeItem.posterUri] else null

    fun posterOf(item: Recommendation): ByteArray = posters.getValue(item.posterUri)

    fun setSources(chosen: List<String>) {
        val current = settingsService.recommendationSources
        for (source in current + chosen) {
            if ((source in current) != (source in chosen)) {
                settingsService.setRecommendationSourceSelected(source, source in chosen)
            }
        }
    }

    fun setTitleCount(count: Int) = settingsService.setRecommendationTitleCount(count)

    fun requestPermission() = channel.requestTvListingsPermission()

    fun applySettings() {
        if (settingsService.recommendationSources != sources || settingsService.recommendationTitleCount != titleCount) {
            refresh(redraw = true)
        }
    }

    fun refresh(redraw: Boolean = false) {
        val run = ++refreshes
        sources = settingsService.recommendationSources
        titleCount = settingsService.recommendationTitleCount
        hasPermission = channel.checkTvListingsPermission()
        if (!hasPermission) {
            notifyListeners()
            return
        }
        scope.launch { draw(run, redraw) }
    }

    private suspend fun draw(run: Int, redraw: Boolean) {
        val programs = channel.getPrograms()
        val byApp = programs.rows.map { Recommendation(it, it["packageName"] as String) }.groupBy { it.source }
        val bySource = mapOf(PLAY_NEXT_SOURCE to programs.playNext.map { Recommendation(it, PLAY_NEXT_SOURCE) }) + byApp
        apps = byApp.values.map { RecommendationApp(it.first().packageName, it.first().appName) }.sortedBy { it.name.lowercase() }

        val queues = LinkedHashMap<String, MutableList<Recommendation>>()
        for (source in sources) {
            val titles = bySource[source]
            if (!titles.isNullOrEmpty()) queues[source] = titles.toMutableList().apply { shuffle(random) }
        }

        val time = now()
        val drawnAt = settingsService.recommendationDrawnAt
        val due = redraw || drawnAt == null || time - drawnAt >= REDRAW_MS
        val drawnAtMs = if (due) time else drawnAt!!
        val saved = if (due) emptyList() else settingsService.recommendationPicks
        val restored = coroutineScope { saved.map { async { restore(it, queues) } }.awaitAll() }
        val drawn = fill(restored.filterNotNull().take(titleCount).toMutableList(), queues)
        if (run != refreshes) return
        if (due) drawn.shuffle(random)

        picks = drawn
        posters.keys.retainAll(drawn.map { it.posterUri }.toSet())
        if (due || activeIndex >= picks.size) activeIndex = 0
        drawJob?.cancel()
        drawJob = scope.launch {
            delay(drawnAtMs + REDRAW_MS - time)
            refresh()
        }
        notifyListeners()
        settingsService.setRecommendationPicks(drawn.map { "${it.source}|${it.id}" }, drawnAtMs)
    }

    private suspend fun fill(
        drawn: MutableList<Recommendation>,
        queues: Map<String, MutableList<Recommendation>>,
    ): MutableList<Recommendation> {
        val open = queues.values.toMutableList().apply { shuffle(random) }
        while (drawn.size < titleCount && open.isNotEmpty()) {
            val round = open.take(titleCount - drawn.size)
            val results = coroutineScope { round.map { async { nextLandscape(it) } }.awaitAll() }
            results.forEachIndexed { index, item ->
                if (item == null) open.removeAll { it === round[index] } else drawn.add(item)
            }
        }
        return drawn
    }

    private suspend fun restore(key: String, queues: Map<String, MutableList<Recommendation>>): Recommendation? {
        val separator = key.lastIndexOf('|')
        val queue = queues[key.substring(0, separator)] ?: return null
        val id = key.substring(separator + 1).toLongOrNull()
        val index = queue.indexOfFirst { it.id == id }
        if (index >= 0) queue.add(queue.removeAt(index))
        return nextLandscape(queue)
    }

    private suspend fun nextLandscape(queue: MutableList<Recommendation>): Recommendation? {
        while (queue.isNotEmpty()) {
            val item = queue.removeAt(queue.lastIndex)
            if (loadPoster(item)) return item
        }
        return null
    }

    private suspend fun loadPoster(item: Recommendation): Boolean {
        if (item.posterUri in posters) return true
        for (uri in listOfNotNull(item.posterUri, item.thumbnailUri)) {
            val bytes = loadImage(uri)
            if (bytes.isNotEmpty() && channel.isLandscape(bytes)) {
                posters[item.posterUri] = bytes
                return true
            }
        }
        return false
    }

    private suspend fun loadImage(uri: String): ByteArray {
        if (!uri.startsWith("http")) return channel.loadContentUriImage(uri)
        return withContext(io) {
            try {
                val connection = URL(uri).openConnection() as HttpURLConnection
                connection.connectTimeout = IMAGE_TIMEOUT_MS
                connection.readTimeout = IMAGE_TIMEOUT_MS
                if (connection.responseCode == HttpURLConnection.HTTP_OK) connection.inputStream.use { it.readBytes() } else ByteArray(0)
            } catch (_: Exception) {
                ByteArray(0)
            }
        }
    }

    fun step(by: Int) {
        activeIndex = (activeIndex + by).mod(picks.size)
        notifyListeners()
    }

    fun launch(item: Recommendation) = channel.launchProgram(item.packageName, item.intentUri)

    fun dispose() {
        drawJob?.cancel()
    }
}
