package io.github.terminalis.machlauncher

import io.github.terminalis.machlauncher.Direction.Down
import io.github.terminalis.machlauncher.Direction.Left
import io.github.terminalis.machlauncher.Direction.Right
import io.github.terminalis.machlauncher.Direction.Up
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PanelModelTest {
    @Test
    fun `times read as two-digit hours and minutes`() {
        assertEquals("00:00", formatMinutes(0))
        assertEquals("07:30", formatMinutes(450))
        assertEquals("23:30", formatMinutes(1410))
    }

    @Test
    fun `With no row focused, Up lands on the last row and every other direction on the first`() {
        assertEquals(5, firstFocusedRow(Up, 6))
        assertEquals(0, firstFocusedRow(Down, 6))
        assertEquals(0, firstFocusedRow(Left, 6))
        assertEquals(0, firstFocusedRow(Right, 6))
    }

    @Test
    fun `Move on a dock tile goes Left and Right only`() {
        assertEquals(1, moveTarget(0, Right, 3, true))
        assertEquals(0, moveTarget(1, Left, 3, true))
        assertNull(moveTarget(1, Up, 3, true))
        assertNull(moveTarget(1, Down, 3, true))
    }

    @Test
    fun `Move stops at the ends`() {
        assertNull(moveTarget(0, Left, 3, true))
        assertNull(moveTarget(2, Right, 3, true))
    }

    @Test
    fun `Move Down on a grid tile moves it six places`() {
        assertEquals(6, moveTarget(0, Down, 13, false))
    }

    @Test
    fun `Move Down onto a short last row lands on its last tile`() {
        assertEquals(12, moveTarget(11, Down, 13, false))
    }

    @Test
    fun `Move Down on the last row stays put, even with tiles to its right`() {
        assertNull(moveTarget(12, Down, 14, false))
    }

    @Test
    fun `Move Up stops at the top row, and Down at the bottom row`() {
        assertNull(moveTarget(3, Up, 13, false))
        assertEquals(1, moveTarget(7, Up, 13, false))
        assertNull(moveTarget(12, Down, 13, false))
    }
}
