package io.github.terminalis.machlauncher

import java.io.File
import java.util.Calendar
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private fun at(hour: Int, minute: Int = 0, second: Int = 0) =
    Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 2, hour, minute, second) }.timeInMillis

@OptIn(ExperimentalCoroutinesApi::class)
class WallpaperServiceTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val pictureFile get() = File(folder.root, "wallpaper")
    private val photo get() = File(folder.root, "photo")
    private lateinit var preferences: FakePreferences

    private fun settings(choice: BackgroundChoice, display: RecommendationsDisplay = RecommendationsDisplay.off): SettingsService {
        preferences = FakePreferences(mapOf("background_choice" to choice.name, "recommendations_display" to display.name))
        return SettingsService(preferences)
    }

    private fun TestScope.service(
        choice: BackgroundChoice,
        display: RecommendationsDisplay = RecommendationsDisplay.off,
        now: () -> Long = System::currentTimeMillis,
    ) = WallpaperService(settings(choice, display), pictureFile, backgroundScope, now, StandardTestDispatcher(testScheduler))
        .also { runCurrent() }

    private fun shownAsset(service: WallpaperService) = (service.wallpaper as BundledWallpaper?)?.asset

    private fun shownPicture(service: WallpaperService) = (service.wallpaper as PictureWallpaper?)?.bytes

    @Test
    fun `the Dark and Light gradients follow the background choice`() = runTest {
        val dark = service(BackgroundChoice.dark)
        val light = service(BackgroundChoice.light)

        assertEquals(DARK_GRADIENT, dark.gradient)
        assertEquals(LIGHT_GRADIENT, light.gradient)
        assertNull(dark.wallpaper)
        assertFalse(dark.showsRecommendations)
    }

    @Test
    fun `isLightAt light window inside one day`() {
        assertFalse(isLightAt(6 * 60 + 59, 7 * 60, 19 * 60))
        assertTrue(isLightAt(7 * 60, 7 * 60, 19 * 60))
        assertTrue(isLightAt(18 * 60 + 59, 7 * 60, 19 * 60))
        assertFalse(isLightAt(19 * 60, 7 * 60, 19 * 60))
    }

    @Test
    fun `isLightAt light window across midnight`() {
        assertTrue(isLightAt(23 * 60, 22 * 60, 6 * 60))
        assertTrue(isLightAt(3 * 60, 22 * 60, 6 * 60))
        assertFalse(isLightAt(12 * 60, 22 * 60, 6 * 60))
    }

    @Test
    fun `Clouds by night and Clouds by day show their bundled photos`() = runTest {
        assertEquals(CLOUDS_BY_NIGHT, shownAsset(service(BackgroundChoice.cloudsNight)))
        assertEquals(CLOUDS_BY_DAY, shownAsset(service(BackgroundChoice.cloudsDay)))
        assertTrue(File("src/main/assets/$CLOUDS_BY_NIGHT").exists())
        assertTrue(File("src/main/assets/$CLOUDS_BY_DAY").exists())
    }

    @Test
    fun `the cloud photos resolve to their 1080p and 4K files`() {
        for (asset in listOf(CLOUDS_BY_NIGHT, CLOUDS_BY_DAY)) {
            val name = asset.substringAfterLast('/')
            for ((ratio, folder) in listOf(2f to "2.0x", 4f to "4.0x")) {
                val variant = bundledVariant(asset, ratio)
                assertEquals("wallpapers/$folder/$name", variant)
                assertTrue(File("src/main/assets/$variant").exists())
            }
        }
    }

    @Test
    fun `Clouds, auto shows the day photo between Light from and Dark from`() = runTest {
        val morning = service(BackgroundChoice.cloudsAuto, now = { at(8) })
        val evening = service(BackgroundChoice.cloudsAuto, now = { at(20) })

        assertEquals(CLOUDS_BY_DAY, shownAsset(morning))
        assertEquals(CLOUDS_BY_NIGHT, shownAsset(evening))
        morning.dispose()
        evening.dispose()
    }

    @Test
    fun `Clouds, auto switches within a minute of Dark from`() = runTest {
        var clock = at(18, 59, 30)
        val wallpaperService = service(BackgroundChoice.cloudsAuto, now = { clock })
        assertEquals(CLOUDS_BY_DAY, shownAsset(wallpaperService))

        clock = at(19)
        advanceTimeBy(31_000)
        runCurrent()

        assertEquals(CLOUDS_BY_NIGHT, shownAsset(wallpaperService))
        wallpaperService.dispose()
    }

    @Test
    fun `recommendations as background put the hero artwork behind Home instead of the picture`() = runTest {
        pictureFile.writeBytes(byteArrayOf(1))
        val wallpaperService = service(BackgroundChoice.picture, RecommendationsDisplay.background)

        assertNull(wallpaperService.wallpaper)
        assertTrue(wallpaperService.showsRecommendations)
    }

    @Test
    fun `recommendations as a row keep the stored picture behind Home`() = runTest {
        pictureFile.writeBytes(byteArrayOf(1))
        val wallpaperService = service(BackgroundChoice.picture, RecommendationsDisplay.row)

        assertArrayEquals(byteArrayOf(1), shownPicture(wallpaperService))
        assertFalse(wallpaperService.showsRecommendations)
    }

    @Test
    fun `picking a picture stores it, shows it and switches to Custom image`() = runTest {
        photo.writeBytes(byteArrayOf(3, 4))
        val wallpaperService = service(BackgroundChoice.dark)

        wallpaperService.setPicture(photo.path)

        assertArrayEquals(byteArrayOf(3, 4), pictureFile.readBytes())
        assertArrayEquals(byteArrayOf(3, 4), shownPicture(wallpaperService))
        assertEquals(BackgroundChoice.picture.name, preferences.getString("background_choice", null))
    }

    @Test
    fun `a second pick gives a new picture`() = runTest {
        val wallpaperService = service(BackgroundChoice.picture)
        photo.writeBytes(byteArrayOf(3, 4))
        wallpaperService.setPicture(photo.path)
        val first = wallpaperService.wallpaper

        photo.writeBytes(byteArrayOf(5, 6))
        wallpaperService.setPicture(photo.path)

        assertNotEquals(first, wallpaperService.wallpaper)
        assertArrayEquals(byteArrayOf(5, 6), shownPicture(wallpaperService))
    }
}
