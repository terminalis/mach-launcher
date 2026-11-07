package io.github.terminalis.machlauncher

import kotlin.math.abs

const val SLIDERS_BUTTON = 2
private const val STATUS_BUTTONS = 3
private const val BANNER_BUTTONS = 2

sealed interface FocusTarget {
    data class Status(val index: Int) : FocusTarget
    data object Watch : FocusTarget
    data class Banner(val index: Int) : FocusTarget
    data class Card(val index: Int) : FocusTarget
    data class Dock(val index: Int) : FocusTarget
    data class Grid(val index: Int) : FocusTarget
}

enum class Direction { Left, Right, Up, Down }

class HomeMap(
    val dockTiles: Int,
    val gridTiles: Int,
    val hero: Boolean,
    val cards: Int,
    val banner: Boolean = false,
    val centreX: (FocusTarget) -> Float,
) {
    private fun nearest(from: FocusTarget, candidates: List<FocusTarget>): FocusTarget? {
        val x = centreX(from)
        return candidates.minByOrNull { abs(centreX(it) - x) }
    }

    private fun recommendations(from: FocusTarget): FocusTarget? = when {
        hero -> FocusTarget.Watch
        banner -> FocusTarget.Banner(if (from is FocusTarget.Status) 0 else BANNER_BUTTONS - 1)
        cards > 0 -> nearest(from, (0 until cards).map { FocusTarget.Card(it) })
        else -> null
    }

    private fun dock(from: FocusTarget) = nearest(from, (0 until dockTiles).map { FocusTarget.Dock(it) })

    private fun gridRow(from: FocusTarget, row: Int): FocusTarget? {
        val first = row * HOME_COLUMNS
        if (row < 0 || first >= gridTiles) return null
        return nearest(from, (first until minOf(first + HOME_COLUMNS, gridTiles)).map { FocusTarget.Grid(it) })
    }

    fun next(from: FocusTarget, direction: Direction): FocusTarget? = when (from) {
        is FocusTarget.Status -> when (direction) {
            Direction.Left -> if (from.index > 0) FocusTarget.Status(from.index - 1) else null
            Direction.Right -> if (from.index < STATUS_BUTTONS - 1) FocusTarget.Status(from.index + 1) else null
            Direction.Up -> null
            Direction.Down -> recommendations(from) ?: dock(from)
        }
        FocusTarget.Watch -> when (direction) {
            Direction.Up -> FocusTarget.Status(SLIDERS_BUTTON)
            Direction.Down -> dock(from)
            else -> null
        }
        is FocusTarget.Banner -> when (direction) {
            Direction.Up -> FocusTarget.Status(SLIDERS_BUTTON)
            Direction.Down -> if (from.index < BANNER_BUTTONS - 1) FocusTarget.Banner(from.index + 1) else dock(from)
            else -> null
        }
        is FocusTarget.Card -> when (direction) {
            Direction.Left -> if (from.index > 0) FocusTarget.Card(from.index - 1) else null
            Direction.Right -> if (from.index < cards - 1) FocusTarget.Card(from.index + 1) else null
            Direction.Up -> FocusTarget.Status(SLIDERS_BUTTON)
            Direction.Down -> dock(from)
        }
        is FocusTarget.Dock -> when (direction) {
            Direction.Left -> if (from.index > 0) FocusTarget.Dock(from.index - 1) else null
            Direction.Right -> if (from.index < dockTiles - 1) FocusTarget.Dock(from.index + 1) else null
            Direction.Up -> recommendations(from) ?: FocusTarget.Status(SLIDERS_BUTTON)
            Direction.Down -> gridRow(from, 0)
        }
        is FocusTarget.Grid -> {
            val row = from.index / HOME_COLUMNS
            when (direction) {
                Direction.Left -> if (from.index % HOME_COLUMNS > 0) FocusTarget.Grid(from.index - 1) else null
                Direction.Right ->
                    if (from.index % HOME_COLUMNS < HOME_COLUMNS - 1 && from.index + 1 < gridTiles) FocusTarget.Grid(from.index + 1) else null
                Direction.Up -> if (row == 0) dock(from) else gridRow(from, row - 1)
                Direction.Down -> gridRow(from, row + 1)
            }
        }
    }
}

fun areaOf(target: FocusTarget, previous: HomeArea): HomeArea = when (target) {
    is FocusTarget.Status -> previous
    FocusTarget.Watch, is FocusTarget.Card, is FocusTarget.Banner -> HomeArea.recommendations
    is FocusTarget.Dock -> HomeArea.dock
    is FocusTarget.Grid -> HomeArea.grid
}
