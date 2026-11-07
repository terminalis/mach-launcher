package io.github.terminalis.machlauncher

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLayoutTest {
    @Test
    fun `a dock row is one sixth of the content width at 16 by 9`() {
        assertEquals(85.5f, appCardRowExtentFromContentWidth(912f), 0.001f)
    }

    @Test
    fun `a grid cell leaves room for the name label`() {
        assertEquals(152f / 109.5f, appCardGridAspectRatio(912f, 6), 0.001f)
    }
}
