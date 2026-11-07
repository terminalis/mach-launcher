package io.github.terminalis.machlauncher

import io.github.terminalis.machlauncher.Direction.Down
import io.github.terminalis.machlauncher.Direction.Left
import io.github.terminalis.machlauncher.Direction.Right
import io.github.terminalis.machlauncher.Direction.Up
import io.github.terminalis.machlauncher.FocusTarget.Card
import io.github.terminalis.machlauncher.FocusTarget.Dock
import io.github.terminalis.machlauncher.FocusTarget.Grid
import io.github.terminalis.machlauncher.FocusTarget.Status
import io.github.terminalis.machlauncher.FocusTarget.Watch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeFocusTest {
    private val home = HomeGeometry(960f, 540f)

    private fun map(dock: Int = 6, grid: Int = 14, hero: Boolean = false, cards: Int = 0, rowOffset: Float = 0f) =
        HomeMap(dock, grid, hero, cards) { target ->
            when (target) {
                is Status -> 800f + 40f * target.index
                Watch -> 100f
                is FocusTarget.Banner -> 800f
                is Card -> home.cardCentre(target.index, rowOffset)
                is Dock -> home.tileLeft(target.index) + 64f
                is Grid -> home.tileLeft(target.index % HOME_COLUMNS) + 64f
            }
        }

    @Test
    fun `With Show as off, Up from the top of Home lands on the sliders icon`() {
        assertEquals(Status(SLIDERS_BUTTON), map().next(Dock(0), Up))
    }

    @Test
    fun `With Show as row, Up from the top of Home lands on the sliders icon`() {
        assertEquals(Status(SLIDERS_BUTTON), map(cards = 3).next(Card(0), Up))
    }

    @Test
    fun `With Show as background, Up from the top of Home lands on the sliders icon`() {
        assertEquals(Status(SLIDERS_BUTTON), map(hero = true).next(Watch, Up))
    }

    @Test
    fun `Up from the dock goes to the Watch button`() {
        assertEquals(Watch, map(hero = true).next(Dock(3), Up))
    }

    @Test
    fun `Coming into the row from below lands on the nearest card`() {
        assertEquals(Card(0), map(cards = 3).next(Dock(0), Up))
        assertEquals(Card(1), map(cards = 3).next(Dock(3), Up))
        assertEquals(Card(2), map(cards = 3).next(Dock(5), Up))
    }

    @Test
    fun `The nearest card follows the row's scroll`() {
        assertEquals(Card(4), map(cards = 9, rowOffset = home.rowOffset(4, 9)).next(Dock(3), Up))
    }

    @Test
    fun `Coming into the row from above lands on the nearest card`() {
        assertEquals(Card(2), map(cards = 3).next(Status(SLIDERS_BUTTON), Down))
    }

    @Test
    fun `Down from the status bar goes to the Watch button, or the dock with nothing above it`() {
        assertEquals(Watch, map(hero = true).next(Status(SLIDERS_BUTTON), Down))
        assertEquals(Dock(5), map().next(Status(SLIDERS_BUTTON), Down))
    }

    @Test
    fun `Left and Right step along the status bar without wrapping`() {
        assertEquals(Status(1), map().next(Status(2), Left))
        assertNull(map().next(Status(0), Left))
        assertNull(map().next(Status(2), Right))
        assertNull(map().next(Status(1), Up))
    }

    @Test
    fun `In the row, Left and Right step along the cards without wrapping`() {
        val row = map(cards = 3)
        assertEquals(Card(1), row.next(Card(0), Right))
        assertEquals(Card(0), row.next(Card(1), Left))
        assertNull(row.next(Card(0), Left))
        assertNull(row.next(Card(2), Right))
    }

    @Test
    fun `Down from the hero and the row goes to the nearest dock tile`() {
        assertEquals(Dock(0), map(hero = true).next(Watch, Down))
        assertEquals(Dock(2), map(cards = 3, rowOffset = 120f).next(Card(1), Down))
        assertEquals(Dock(4), map(cards = 3, rowOffset = 240f).next(Card(2), Down))
    }

    @Test
    fun `The dock moves along itself without wrapping, the plus tile included`() {
        val dock = map(dock = 3)
        assertEquals(Dock(2), dock.next(Dock(1), Right))
        assertNull(dock.next(Dock(2), Right))
        assertNull(dock.next(Dock(0), Left))
    }

    @Test
    fun `Down from the dock enters the grid in the nearest column`() {
        assertEquals(Grid(3), map().next(Dock(3), Down))
        assertEquals(Grid(1), map(grid = 2).next(Dock(5), Down))
        assertNull(map(grid = 0).next(Dock(1), Down))
    }

    @Test
    fun `The grid moves in rows of six without wrapping`() {
        val grid = map()
        assertEquals(Grid(7), grid.next(Grid(6), Right))
        assertNull(grid.next(Grid(5), Right))
        assertNull(grid.next(Grid(6), Left))
        assertNull(grid.next(Grid(13), Right))
        assertEquals(Grid(9), grid.next(Grid(3), Down))
        assertEquals(Grid(3), grid.next(Grid(9), Up))
    }

    @Test
    fun `Down onto a short last row lands on the nearest tile, and past it stops`() {
        assertEquals(Grid(13), map().next(Grid(11), Down))
        assertNull(map().next(Grid(13), Down))
    }

    @Test
    fun `Up from the first grid row goes back to the dock`() {
        assertEquals(Dock(4), map().next(Grid(4), Up))
        assertEquals(Dock(1), map(dock = 2).next(Grid(5), Up))
    }

    @Test
    fun `Up from the banner's Grant permission goes to the sliders icon`() {
        assertEquals(Status(SLIDERS_BUTTON), map().next(FocusTarget.Banner(0), Up))
    }

    @Test
    fun `Up from the banner's Check again goes to the sliders icon`() {
        assertEquals(Status(SLIDERS_BUTTON), map().next(FocusTarget.Banner(1), Up))
    }

    @Test
    fun `Down from the status bar lands on the banner's Grant permission`() {
        val home = HomeMap(6, 14, false, 0, banner = true) { 0f }
        assertEquals(FocusTarget.Banner(0), home.next(Status(SLIDERS_BUTTON), Down))
    }

    @Test
    fun `The banner sits between the dock and the status bar`() {
        val home = HomeMap(6, 14, false, 0, banner = true) { 0f }
        assertEquals(FocusTarget.Banner(1), home.next(Dock(3), Up))
        assertEquals(FocusTarget.Banner(1), home.next(FocusTarget.Banner(0), Down))
        assertEquals(Dock(0), home.next(FocusTarget.Banner(1), Down))
    }

    @Test
    fun `Focus on the status bar keeps the area it came from`() {
        assertEquals(HomeArea.dock, areaOf(Status(2), HomeArea.dock))
        assertEquals(HomeArea.recommendations, areaOf(Status(2), HomeArea.recommendations))
        assertEquals(HomeArea.recommendations, areaOf(Watch, HomeArea.dock))
        assertEquals(HomeArea.recommendations, areaOf(Card(1), HomeArea.dock))
        assertEquals(HomeArea.grid, areaOf(Grid(0), HomeArea.dock))
    }
}
