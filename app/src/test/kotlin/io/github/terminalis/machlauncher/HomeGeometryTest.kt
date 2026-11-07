package io.github.terminalis.machlauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeGeometryTest {
    private val home = HomeGeometry(960f, 540f)

    @Test
    fun `the dock sits where Mach draws it`() {
        assertEquals(334f, home.dockOffset)
        assertEquals(400f, home.dockPanelTop)
        assertEquals(95.5f, home.dockPanelHeight, 0.001f)
        assertEquals(414f, home.dockTileTop)
        assertEquals(36f, home.tileLeft(0))
        assertEquals(188f, home.tileLeft(1))
        assertEquals(128f, home.tileWidth)
        assertEquals(67.5f, home.tileHeight, 0.001f)
    }

    @Test
    fun `the grid rows sit where Mach draws them`() {
        assertEquals(520.5f, home.gridTileTop(0), 0.001f)
        assertEquals(520.5f, home.gridTileTop(5), 0.001f)
        assertEquals(642f, home.gridTileTop(6), 0.001f)
        assertEquals(763.5f, home.gridTileTop(12), 0.001f)
        assertEquals(596f, home.gridLabelTop(0), 0.001f)
    }

    @Test
    fun `the row sits just above the dock`() {
        assertEquals(374f, home.recommendationsBottom)
        assertEquals(154f, home.cardTop)
        assertTrue(home.cardTop + CARD_HEIGHT < home.dockPanelTop)
    }

    @Test
    fun `the row scrolls to keep the focused card centred, stopping at its ends`() {
        assertEquals(0f, home.rowOffset(0, 9))
        assertEquals(120f, home.rowOffset(1, 9))
        assertEquals(504f, home.rowOffset(2, 9))
        assertEquals(2544f, home.rowOffset(8, 9))
        assertEquals(240f, home.rowOffset(2, 3))
    }

    @Test
    fun `fourteen tiles fit, so moving among them doesn't scroll`() {
        for (index in 6 until 14) assertNull(home.keepInView(home.dockOffset, index, 14))
    }

    @Test
    fun `a tile below the screen scrolls into view, and scrolling back returns to it`() {
        val down = home.keepInView(home.dockOffset, 24, 30)!!
        assertEquals(598f, down, 0.001f)
        assertEquals(home.gridTileTop(6) - 2 * TOOLBAR_HEIGHT - 24f, home.keepInView(down + 300f, 6, 30)!!, 0.001f)
    }

    @Test
    fun `the page can scroll the last row clear of the bottom`() {
        assertEquals(1300f, home.contentHeight(14), 0.001f)
        assertEquals(334f + 121.5f + 484f, home.contentHeight(0), 0.001f)
    }
}
