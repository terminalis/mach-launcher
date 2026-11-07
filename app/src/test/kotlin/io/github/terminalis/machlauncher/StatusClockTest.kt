package io.github.terminalis.machlauncher

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusClockTest {
    private val evening = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 30, 20, 9, 0) }.time
    private val morning = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 30, 8, 9, 0) }.time

    @Test
    fun `the date always shows in the fixed form`() {
        assertEquals("Fri 30 Oct", statusDate(evening))
        assertTrue(statusDate(morning).matches(Regex("^(Mon|Tue|Wed|Thu|Fri|Sat|Sun) \\d{1,2} [A-Z][a-z]{2}$")))
    }

    @Test
    fun `clock shows a two-digit hour and follows the 24-hour switch (24-hour true)`() {
        assertEquals("20:09", statusClock(evening, true))
        assertEquals("08:09", statusClock(morning, true))
    }

    @Test
    fun `clock shows a two-digit hour and follows the 24-hour switch (24-hour false)`() {
        assertEquals("08:09 PM", statusClock(evening, false))
        assertEquals("08:09 AM", statusClock(morning, false))
    }

    @Test
    fun `the clock ticks on the minute`() {
        assertEquals(60_000L, millisToNextMinute(120_000L))
        assertEquals(1L, millisToNextMinute(119_999L))
    }
}
