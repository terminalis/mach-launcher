package io.github.terminalis.machlauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherStateTest {
    @Test
    fun `focus starts on the dock and notifies only on a change`() {
        val state = LauncherState()
        var notified = 0
        state.addListener { notified++ }
        assertEquals(HomeArea.dock, state.focusedArea)

        state.setFocusedArea(HomeArea.dock)
        state.setFocusedArea(HomeArea.grid)

        assertEquals(1, notified)
        assertTrue(state.appGridFocused)
    }

    @Test
    fun `a focus request is consumed once, by its own app`() {
        val state = LauncherState()
        state.requestFocus("a")

        assertFalse(state.consumeFocusRequest("b"))
        assertTrue(state.consumeFocusRequest("a"))
        assertFalse(state.consumeFocusRequest("a"))
    }
}
