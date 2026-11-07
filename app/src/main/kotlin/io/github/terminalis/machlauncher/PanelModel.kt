package io.github.terminalis.machlauncher

object Glyphs {
    const val GRID = ""
    const val FILM = ""
    const val PHOTOS = ""
    const val HOUSE = ""
    const val INFO = ""
    const val CHEVRON_RIGHT = ""
    const val CHEVRON_LEFT = ""
    const val CHEVRON_UP = ""
    const val CHEVRON_DOWN = ""
    const val MOVE = ""
    const val OPEN = ""
    const val STAR_FILL = ""
    const val STAR = ""
    const val EYE = ""
    const val EYE_SLASH = ""
    const val TRASH = ""
    const val LOCK_OPEN = ""
    const val MOON = ""
    const val SUN = ""
    const val CLOUD_MOON = ""
    const val CLOUD_SUN = ""
    const val CLOCK = ""
    const val MOON_STARS = ""
    const val PHOTO = ""
    const val CHECK = ""
    const val APP = ""
    const val TV = ""
    const val REFRESH = ""
    const val DOWNLOAD = ""
}

sealed interface RowLeading {
    data object None : RowLeading
    data class Glyph(val glyph: String) : RowLeading
    data class AppIcon(val packageName: String) : RowLeading
}

sealed interface RowTrailing {
    data object None : RowTrailing
    data object Chevron : RowTrailing
    data object Check : RowTrailing
    data class Label(val text: String) : RowTrailing
    data class Toggle(val on: Boolean) : RowTrailing
}

class PanelRow(
    val label: String,
    val leading: RowLeading = RowLeading.None,
    val trailing: RowTrailing = RowTrailing.None,
    val indented: Boolean = false,
    val dividerBefore: Boolean = false,
    val focusFirst: Boolean = false,
    val onPress: () -> Unit = {},
)

class PanelTabs(val glyphs: List<String>) {
    var selected = 0
        private set

    fun step(by: Int) {
        selected = (selected + by).coerceIn(0, glyphs.size - 1)
    }
}

class PanelPage(
    val title: () -> String,
    val rows: () -> List<PanelRow>,
    val appHeader: App? = null,
    val showsPackage: Boolean = false,
    val paragraphs: List<String> = emptyList(),
    val emptyText: String? = null,
    val centred: Boolean = false,
    val tabs: PanelTabs? = null,
    val opensUnfocused: Boolean = false,
)

fun firstFocusedRow(direction: Direction, rowCount: Int) = if (direction == Direction.Up) rowCount - 1 else 0

interface PanelNavigator {
    fun push(page: PanelPage)
    fun pop()
    fun showAbout()
    fun pickPhoto(onPicked: (String) -> Unit)
    fun showHomeAppChoice()
}

fun formatMinutes(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

fun recommendationsDisplayLabel(display: RecommendationsDisplay): String = when (display) {
    RecommendationsDisplay.off -> AppStrings.off
    RecommendationsDisplay.row -> AppStrings.row
    RecommendationsDisplay.background -> AppStrings.background
}

fun moveTarget(index: Int, direction: Direction, count: Int, inDock: Boolean): Int? {
    val next = when {
        direction == Direction.Left -> index - 1
        direction == Direction.Right -> index + 1
        direction == Direction.Up && !inDock -> index - HOME_COLUMNS
        direction == Direction.Down && !inDock && index / HOME_COLUMNS < (count - 1) / HOME_COLUMNS ->
            minOf(index + HOME_COLUMNS, count - 1)
        else -> index
    }
    return if (next < 0 || next >= count || next == index) null else next
}
