package io.github.terminalis.machlauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OkPressTest {
    private var now = 1000L
    private val calls = ArrayList<String>()
    private val ok = OkPress({ now }, { calls.add("press") }, { calls.add("long") })

    @Test
    fun `a short OK press is a press, on release`() {
        ok.down()
        assertTrue(calls.isEmpty())
        ok.up()

        assertEquals(listOf("press"), calls)
    }

    @Test
    fun `holding OK for half a second is one long press and no press`() {
        ok.down()
        ok.down()
        assertTrue(calls.isEmpty())
        now += 550
        ok.down()
        ok.up()

        assertEquals(listOf("long"), calls)
    }

    @Test
    fun `a key repeat short of half a second stays a press`() {
        ok.down()
        now += 499
        ok.down()
        ok.up()

        assertEquals(listOf("press"), calls)
    }
}
