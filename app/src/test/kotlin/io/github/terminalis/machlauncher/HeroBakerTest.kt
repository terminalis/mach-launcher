package io.github.terminalis.machlauncher

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HeroBakerTest {
    private val a = byteArrayOf(1)
    private val b = byteArrayOf(2)
    private val c = byteArrayOf(3)
    private val baked = ArrayList<Byte>()
    private val shown = ArrayList<Byte?>()

    private fun TestScope.baker() = HeroBaker<Byte>(
        backgroundScope,
        StandardTestDispatcher(testScheduler),
        { bytes -> bytes[0].also { baked.add(it) } },
        { shown.add(it) },
    )

    @Test
    fun `the active title bakes first, then every other title`() = runTest {
        baker().update(b, listOf(a, b, c))
        runCurrent()

        assertEquals(listOf<Byte>(2, 1, 3), baked)
        assertEquals(listOf<Byte?>(2), shown)
    }

    @Test
    fun `stepping to a baked title shows it without baking again`() = runTest {
        val baker = baker()
        baker.update(a, listOf(a, b))
        runCurrent()
        baked.clear()

        baker.update(b, listOf(a, b))
        runCurrent()

        assertEquals(emptyList<Byte>(), baked)
        assertEquals(listOf<Byte?>(1, 2), shown)
    }

    @Test
    fun `the last image stays until the next one is baked`() = runTest {
        val baker = baker()
        baker.update(a, listOf(a))
        runCurrent()

        baker.update(c, listOf(a, c))

        assertEquals(listOf<Byte?>(1), shown)
        runCurrent()
        assertEquals(listOf<Byte?>(1, 3), shown)
    }

    @Test
    fun `a title dropped while baking is not kept`() = runTest {
        lateinit var baker: HeroBaker<Byte>
        baker = HeroBaker(
            backgroundScope,
            StandardTestDispatcher(testScheduler),
            { bytes ->
                if (bytes === a && baked.isEmpty()) baker.update(c, listOf(c))
                bytes[0].also { baked.add(it) }
            },
            { shown.add(it) },
        )
        baker.update(a, listOf(a))
        runCurrent()

        baker.update(a, listOf(a, c))
        runCurrent()

        assertEquals(listOf<Byte>(1, 3, 1), baked)
    }

    @Test
    fun `no active title clears the hero`() = runTest {
        val baker = baker()
        baker.update(a, listOf(a))
        runCurrent()

        baker.update(null, emptyList())

        assertNull(shown.last())
    }
}
