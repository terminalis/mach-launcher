package io.github.terminalis.machlauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsServiceTest {
    private val settingsService = SettingsService(FakePreferences())

    @Test
    fun `background is Clouds by day by default`() {
        assertEquals(BackgroundChoice.cloudsDay, settingsService.backgroundChoice)

        settingsService.setBackgroundChoice(BackgroundChoice.picture)

        assertEquals(BackgroundChoice.picture, settingsService.backgroundChoice)
    }

    @Test
    fun `Clouds, auto runs light from 07 00 and dark from 19 00 until changed`() {
        assertEquals(7 * 60, settingsService.lightFromMinutes)
        assertEquals(19 * 60, settingsService.darkFromMinutes)

        settingsService.setLightFromMinutes(6 * 60 + 30)
        settingsService.setDarkFromMinutes(20 * 60)

        assertEquals(6 * 60 + 30, settingsService.lightFromMinutes)
        assertEquals(20 * 60, settingsService.darkFromMinutes)
    }

    @Test
    fun `recommendations are off until shown as a row or background`() {
        assertEquals(RecommendationsDisplay.off, settingsService.recommendationsDisplay)

        settingsService.setRecommendationsDisplay(RecommendationsDisplay.row)
        assertEquals(RecommendationsDisplay.row, settingsService.recommendationsDisplay)

        settingsService.setRecommendationsDisplay(RecommendationsDisplay.background)
        assertEquals(RecommendationsDisplay.background, settingsService.recommendationsDisplay)
    }

    @Test
    fun `every source is off until switched on`() {
        assertTrue(settingsService.recommendationSources.isEmpty())

        settingsService.setRecommendationSourceSelected("com.netflix.ninja", true)
        settingsService.setRecommendationSourceSelected("play_next", true)
        assertEquals(listOf("com.netflix.ninja", "play_next"), settingsService.recommendationSources)

        settingsService.setRecommendationSourceSelected("com.netflix.ninja", false)
        assertEquals(listOf("play_next"), settingsService.recommendationSources)
    }

    @Test
    fun `sources are kept in order however they were switched on`() {
        settingsService.setRecommendationSourceSelected("play_next", true)
        settingsService.setRecommendationSourceSelected("com.netflix.ninja", true)

        assertEquals(listOf("com.netflix.ninja", "play_next"), settingsService.recommendationSources)
    }

    @Test
    fun `the hero holds three titles until changed`() {
        assertEquals(3, settingsService.recommendationTitleCount)

        settingsService.setRecommendationTitleCount(9)

        assertEquals(9, settingsService.recommendationTitleCount)
    }

    @Test
    fun `remembers the current picks and when they were drawn`() {
        assertTrue(settingsService.recommendationPicks.isEmpty())
        assertNull(settingsService.recommendationDrawnAt)

        settingsService.setRecommendationPicks(listOf("com.netflix.ninja|12"), 1000)

        assertEquals(listOf("com.netflix.ninja|12"), settingsService.recommendationPicks)
        assertEquals(1000L, settingsService.recommendationDrawnAt)
    }
}
