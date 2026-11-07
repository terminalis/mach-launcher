package io.github.terminalis.machlauncher

import java.util.Calendar
import kotlin.random.Random
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val NETFLIX = "com.netflix.ninja"
private const val APPLE = "com.apple.atve.androidtv.appletv"
private const val HBO = "com.wbd.stream"
private const val HOUR = 60 * 60 * 1000L

private val APP_NAMES = mapOf(NETFLIX to "Netflix", APPLE to "Apple TV", HBO to "HBO Max")

private fun program(packageName: String, id: Long) = mapOf<String, Any?>(
    "id" to id,
    "packageName" to packageName,
    "appName" to APP_NAMES[packageName],
    "title" to "Title $id",
    "description" to "About $id",
    "posterUri" to "content://posters/$id",
    "intentUri" to "intent://title/$id#Intent;end",
    "progressPercent" to 0,
)

private fun programs(packageName: String, vararg ids: Long) = ids.map { program(packageName, it) }

@OptIn(ExperimentalCoroutinesApi::class)
class RecommendationsServiceTest {
    private val settingsService = SettingsService(FakePreferences())
    private val channel = FakeChannel().apply { rows = programs(NETFLIX, 1, 2) + programs(APPLE, 3, 4) }
    private var now = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 30, 12, 0, 0) }.timeInMillis

    private fun switchOn(vararg sources: String) {
        for (source in sources) settingsService.setRecommendationSourceSelected(source, true)
    }

    private fun TestScope.start(seed: Int = 7) = RecommendationsService(
        channel, settingsService, backgroundScope, { now }, Random(seed), StandardTestDispatcher(testScheduler),
    ).also { runCurrent() }

    private fun perSource(service: RecommendationsService) = service.picks.groupingBy { it.source }.eachCount()

    private fun ids(service: RecommendationsService) = service.picks.map { it.id }

    @Test
    fun `without TV-listings access there are no titles and the banner shows`() = runTest {
        channel.permitted = false
        val service = start()

        assertFalse(service.hasTitles)
        assertTrue(service.showsPermissionBanner)
        service.dispose()
    }

    @Test
    fun `lists the apps with rows by name, A to Z`() = runTest {
        val service = start()

        assertEquals(listOf(RecommendationApp(APPLE, "Apple TV"), RecommendationApp(NETFLIX, "Netflix")), service.apps)
        service.dispose()
    }

    @Test
    fun `every source starts switched off, so there are no titles`() = runTest {
        val service = start()

        assertFalse(service.hasTitles)
        service.dispose()
    }

    @Test
    fun `one switched-on app gives all three titles, none twice`() = runTest {
        channel.rows = programs(NETFLIX, 1, 2, 3, 4, 5)
        switchOn(NETFLIX)
        val service = start()

        assertTrue(service.picks.all { it.packageName == NETFLIX })
        assertEquals(3, ids(service).toSet().size)
        service.dispose()
    }

    @Test
    fun `the hero holds the chosen number of titles, shared evenly`() = runTest {
        channel.rows = programs(NETFLIX, 1, 2, 3, 4, 5) + programs(APPLE, 6, 7, 8, 9, 10)
        switchOn(NETFLIX, APPLE)
        settingsService.setRecommendationTitleCount(5)
        val service = start()

        assertEquals(5, service.picks.size)
        assertEquals(listOf(2, 3), perSource(service).values.sorted())
        service.dispose()
    }

    @Test
    fun `with more switched-on sources than places each source gives at most one`() = runTest {
        channel.rows = programs(NETFLIX, 1, 2) + programs(APPLE, 3, 4) + programs(HBO, 5, 6)
        channel.playNext = programs(NETFLIX, 101, 102)
        switchOn(NETFLIX, APPLE, HBO, PLAY_NEXT_SOURCE)
        val service = start()

        assertEquals(3, service.picks.size)
        assertTrue(perSource(service).values.all { it == 1 })
        service.dispose()
    }

    @Test
    fun `a source short of titles leaves its places to the others`() = runTest {
        channel.rows = programs(NETFLIX, 1) + programs(APPLE, 3, 4, 5, 6)
        switchOn(NETFLIX, APPLE)
        settingsService.setRecommendationTitleCount(4)
        val service = start()

        assertEquals(mapOf(NETFLIX to 1, APPLE to 3), perSource(service))
        service.dispose()
    }

    @Test
    fun `with fewer landscape titles than asked the hero holds those`() = runTest {
        channel.rows = programs(NETFLIX, 1) + programs(APPLE, 3)
        switchOn(NETFLIX, APPLE)
        settingsService.setRecommendationTitleCount(5)
        val service = start()

        assertEquals(2, service.picks.size)
        service.dispose()
    }

    @Test
    fun `Play Next titles come from Android's Play Next list, labelled with their app`() = runTest {
        channel.rows = emptyList()
        channel.playNext = programs(NETFLIX, 101, 102, 103)
        switchOn(PLAY_NEXT_SOURCE)
        val service = start()

        assertEquals(setOf(101L, 102L, 103L), ids(service).toSet())
        assertTrue(service.picks.all { it.source == PLAY_NEXT_SOURCE })
        assertTrue(service.picks.all { it.appName == "Netflix" })
        service.dispose()
    }

    @Test
    fun `a title whose artwork doesn't load is not shown`() = runTest {
        channel.rows = programs(APPLE, 3, 4)
        channel.image = { if (it == "content://posters/3") ByteArray(0) else landscapePng }
        switchOn(APPLE)
        val service = start()

        assertEquals(listOf(4L), ids(service))
        assertEquals(listOf("$APPLE|4"), settingsService.recommendationPicks)
        service.dispose()
    }

    @Test
    fun `a service's portrait titles give way to its landscape one`() = runTest {
        channel.rows = programs(APPLE, 3, 4, 5, 6, 7, 8)
        channel.image = { if (it == "content://posters/8") landscapePng else portraitPng }
        switchOn(APPLE)
        val service = start()

        assertEquals(listOf(8L), ids(service))
        service.dispose()
    }

    @Test
    fun `a portrait poster gives way to a landscape thumbnail`() = runTest {
        channel.rows = listOf(program(APPLE, 3) + ("thumbnailUri" to "content://thumbnails/3"))
        channel.image = { if (it == "content://posters/3") portraitPng else landscapePng }
        switchOn(APPLE)
        val service = start()

        assertEquals(listOf(3L), ids(service))
        assertArrayEquals(landscapePng, service.activePoster)
        service.dispose()
    }

    @Test
    fun `no titles when no artwork loads`() = runTest {
        channel.image = { ByteArray(0) }
        switchOn(NETFLIX, APPLE)
        val service = start()

        assertFalse(service.hasTitles)
        assertFalse(service.showsPermissionBanner)
        service.dispose()
    }

    @Test
    fun `the same picks come back within 8 hours`() = runTest {
        switchOn(NETFLIX, APPLE)
        val first = start()
        val picked = ids(first)
        val drawnAt = settingsService.recommendationDrawnAt
        first.dispose()

        now += 7 * HOUR
        val second = start()

        assertEquals(picked, ids(second))
        assertEquals(drawnAt, settingsService.recommendationDrawnAt)
        second.dispose()
    }

    @Test
    fun `a saved title that left the rows is replaced from the same source`() = runTest {
        channel.rows = programs(NETFLIX, 1, 2, 3)
        switchOn(NETFLIX)
        val first = start()
        val picked = ids(first)
        first.dispose()

        channel.rows = programs(NETFLIX, picked[0], picked[1], 9)
        now += HOUR
        val second = start()

        assertEquals(listOf(picked[0], picked[1], 9L), ids(second))
        second.dispose()
    }

    @Test
    fun `a saved title with portrait artwork gives way to a landscape one from the same source`() = runTest {
        channel.rows = programs(NETFLIX, 1, 2, 3)
        switchOn(NETFLIX)
        val first = start()
        val picked = ids(first)
        first.dispose()

        channel.rows = programs(NETFLIX, 1, 2, 3, 9)
        channel.image = { if (it == "content://posters/${picked[0]}") portraitPng else landscapePng }
        now += HOUR
        val second = start()

        assertEquals(listOf(9L, picked[1], picked[2]), ids(second))
        second.dispose()
    }

    @Test
    fun `saved titles keep their places however the rows shuffle`() = runTest {
        switchOn(NETFLIX)
        for (seed in 0 until 20) {
            channel.rows = programs(NETFLIX, 1, 2, 3)
            channel.image = { landscapePng }
            now += 8 * HOUR
            val first = start(seed)
            val picked = ids(first)
            first.dispose()

            channel.rows = programs(NETFLIX, 1, 2, 3, 9)
            channel.image = { if (it == "content://posters/${picked[0]}") portraitPng else landscapePng }
            now += HOUR
            val second = start(seed + 100)

            assertEquals(listOf(9L, picked[1], picked[2]), ids(second))
            second.dispose()
        }
    }

    @Test
    fun `a new draw after 8 hours`() = runTest {
        switchOn(NETFLIX, APPLE)
        val first = start()
        first.dispose()

        now += 8 * HOUR
        val second = start()

        assertEquals(now, settingsService.recommendationDrawnAt)
        second.dispose()
    }

    @Test
    fun `a running service draws again when the 8 hours are up`() = runTest {
        switchOn(NETFLIX, APPLE)
        val service = start()

        now += 8 * HOUR
        advanceTimeBy(8 * HOUR)
        runCurrent()

        assertEquals(now, settingsService.recommendationDrawnAt)
        service.dispose()
    }

    private fun TestScope.closePanel(service: RecommendationsService) {
        service.applySettings()
        runCurrent()
    }

    @Test
    fun `switching a source on draws again`() = runTest {
        switchOn(NETFLIX)
        val service = start()

        service.setSources(listOf(NETFLIX, APPLE))
        closePanel(service)

        assertEquals(setOf(NETFLIX, APPLE), perSource(service).keys)
        service.dispose()
    }

    @Test
    fun `a changed source starts a fresh draw`() = runTest {
        switchOn(NETFLIX)
        val service = start()

        now += HOUR
        service.setSources(listOf(NETFLIX, APPLE))
        closePanel(service)

        assertEquals(now, settingsService.recommendationDrawnAt)
        service.dispose()
    }

    @Test
    fun `a draw waits for the panel to close`() = runTest {
        switchOn(NETFLIX)
        val service = start()

        service.setSources(listOf(NETFLIX, APPLE))
        advanceTimeBy(60_000)
        runCurrent()

        assertEquals(setOf(NETFLIX), perSource(service).keys)
        closePanel(service)
        assertEquals(setOf(NETFLIX, APPLE), perSource(service).keys)
        service.dispose()
    }

    @Test
    fun `switching back before the panel closes keeps the picks`() = runTest {
        switchOn(NETFLIX)
        val service = start()
        val before = service.picks

        service.setSources(listOf(NETFLIX, APPLE))
        service.setSources(listOf(NETFLIX))
        closePanel(service)

        assertEquals(before, service.picks)
        service.dispose()
    }

    @Test
    fun `changing the number of titles draws again`() = runTest {
        switchOn(NETFLIX, APPLE)
        val service = start()

        service.setTitleCount(4)
        closePanel(service)

        assertEquals(4, service.picks.size)
        service.dispose()
    }

    @Test
    fun `a newer draw wins over an older one still loading artwork`() = runTest {
        switchOn(NETFLIX)
        val service = start()
        val artwork = CompletableDeferred<ByteArray>()
        channel.image = { artwork.await() }

        service.setSources(listOf(NETFLIX, APPLE))
        closePanel(service)
        service.setSources(listOf(NETFLIX))
        closePanel(service)
        artwork.complete(landscapePng)
        runCurrent()

        assertEquals(setOf(NETFLIX), perSource(service).keys)
        service.dispose()
    }

    @Test
    fun `Left and Right wrap round`() = runTest {
        switchOn(NETFLIX, APPLE)
        val service = start()

        service.step(1)
        service.step(1)
        assertEquals(2, service.activeIndex)
        service.step(1)
        assertEquals(0, service.activeIndex)
        service.step(-1)
        assertEquals(2, service.activeIndex)
        service.dispose()
    }

    @Test
    fun `OK opens the title through its own link`() = runTest {
        switchOn(NETFLIX, APPLE)
        val service = start()

        service.launch(service.activeItem)

        assertEquals(listOf("launchProgram ${service.activeItem.packageName} ${service.activeItem.intentUri}"), channel.calls)
        service.dispose()
    }

    @Test
    fun `the launcher coming back to the front reads the rows again`() = runTest {
        val service = start()
        val reads = channel.programReads

        service.refresh()
        runCurrent()

        assertEquals(reads + 1, channel.programReads)
        service.dispose()
    }

    @Test
    fun `web artwork loads over HTTP, and a failed fetch is not shown`() = runTest {
        LocalHttpServer(200, landscapePng).use { good ->
            LocalHttpServer(404, ByteArray(0)).use { missing ->
                channel.rows = listOf(
                    program(APPLE, 3) + ("posterUri" to good.url),
                    program(APPLE, 4) + ("posterUri" to missing.url),
                )
                switchOn(APPLE)
                val service = start()

                assertEquals(listOf(3L), ids(service))
                assertArrayEquals(landscapePng, service.activePoster)
                service.dispose()
            }
        }
    }
}
