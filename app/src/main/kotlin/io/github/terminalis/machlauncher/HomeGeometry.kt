package io.github.terminalis.machlauncher

const val TOOLBAR_HEIGHT = 56f
const val CARD_WIDTH = 360f
const val CARD_HEIGHT = 208f
const val CARD_PITCH = CARD_WIDTH + 2 * APP_CARD_HORIZONTAL_PADDING
const val ROW_BOTTOM_PADDING = 16f
const val ROW_VERTICAL_PADDING = 12f
const val ROW_SLACK = 8f

private const val DOCK_TOP_PADDING = 10f
private const val DOCK_BOTTOM_PADDING = 16f
private const val DOCK_INNER_PADDING = 5f
private const val GRID_ROW_SPACING = 12f
private const val GRID_BOTTOM_PADDING = 8f
private const val LABEL_GAP = 8f
private const val KEEP_TOP_MARGIN = 24f
private const val KEEP_BOTTOM_MARGIN = 96f
private const val KEEP_MIN_MOVE = 24f

class HomeGeometry(val width: Float, val height: Float) {
    val pageHeight = height - TOOLBAR_HEIGHT
    val dockOffset = pageHeight - 150f
    private val contentWidth = width - 2 * LAUNCHER_SECTION_HORIZONTAL_PADDING
    private val slotWidth = contentWidth / HOME_COLUMNS
    private val rowExtent = appCardRowExtentFromContentWidth(contentWidth)

    val tileWidth = slotWidth - 2 * APP_CARD_HORIZONTAL_PADDING
    val tileHeight = rowExtent - 2 * APP_CARD_VERTICAL_PADDING

    val dockPanelTop = TOOLBAR_HEIGHT + dockOffset + DOCK_TOP_PADDING
    val dockPanelHeight = rowExtent + 2 * DOCK_INNER_PADDING
    val dockPanelWidth = contentWidth
    val dockPanelLeft = LAUNCHER_SECTION_HORIZONTAL_PADDING
    val dockTileTop = dockPanelTop + DOCK_INNER_PADDING + APP_CARD_VERTICAL_PADDING

    private val cellHeight = slotWidth / APP_CARD_ASPECT_RATIO + APP_NAME_LABEL_HEIGHT
    private val rowPitch = cellHeight + GRID_ROW_SPACING
    private val gridTop = dockPanelTop + dockPanelHeight + DOCK_BOTTOM_PADDING

    val recommendationsBottom = TOOLBAR_HEIGHT + dockOffset - ROW_BOTTOM_PADDING
    val cardTop = recommendationsBottom - ROW_VERTICAL_PADDING - CARD_HEIGHT

    fun tileLeft(column: Int) = LAUNCHER_SECTION_HORIZONTAL_PADDING + APP_CARD_HORIZONTAL_PADDING + slotWidth * column

    fun gridTileTop(index: Int) = gridTop + APP_CARD_VERTICAL_PADDING + rowPitch * (index / HOME_COLUMNS)

    fun gridLabelTop(index: Int) = gridTileTop(index) + tileHeight + LABEL_GAP

    fun gridRows(tiles: Int) = (tiles + HOME_COLUMNS - 1) / HOME_COLUMNS

    fun contentHeight(tiles: Int): Float {
        val rows = gridRows(tiles)
        val grid = if (rows == 0) 0f else rows * cellHeight + (rows - 1) * GRID_ROW_SPACING + GRID_BOTTOM_PADDING
        return gridTop - TOOLBAR_HEIGHT + grid + pageHeight
    }

    fun rowOffset(index: Int, cards: Int): Float {
        val widest = (2 * LAUNCHER_SECTION_HORIZONTAL_PADDING + CARD_PITCH * cards - width).coerceAtLeast(0f)
        return (LAUNCHER_SECTION_HORIZONTAL_PADDING + CARD_PITCH * index + CARD_PITCH / 2 - width / 2).coerceIn(0f, widest)
    }

    fun cardCentre(index: Int, offset: Float) =
        LAUNCHER_SECTION_HORIZONTAL_PADDING + CARD_PITCH * index + CARD_PITCH / 2 - offset

    fun keepInView(pixels: Float, index: Int, tiles: Int): Float? {
        val maxScroll = (contentHeight(tiles) - height).coerceAtLeast(0f)
        val boxTop = gridTileTop(index) - TOOLBAR_HEIGHT
        val boxBottom = boxTop + tileHeight + APP_NAME_LABEL_HEIGHT
        val top = (boxTop - TOOLBAR_HEIGHT - KEEP_TOP_MARGIN).coerceIn(0f, maxScroll)
        val bottom = (boxBottom - height + KEEP_BOTTOM_MARGIN).coerceIn(0f, maxScroll)
        return when {
            pixels - top >= KEEP_MIN_MOVE -> top
            bottom - pixels >= KEEP_MIN_MOVE -> bottom
            else -> null
        }
    }
}
