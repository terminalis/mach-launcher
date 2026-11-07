/*
 * FLauncher
 * Copyright (C) 2021  Étienne Fesser
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.terminalis.machlauncher

import java.io.File
import java.util.Calendar
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val CLOUDS_BY_NIGHT = "wallpapers/clouds-by-night.webp"
const val CLOUDS_BY_DAY = "wallpapers/clouds-by-day.webp"

private val BUNDLED_RATIOS = listOf(1f, 2f, 4f)
private const val LOW_RATIO_LIMIT = 2f

data class BackgroundGradient(val from: Int, val to: Int, val rotation: Float)

val DARK_GRADIENT = BackgroundGradient(0xFF152331.toInt(), 0xFF000000.toInt(), 1.6f)
val LIGHT_GRADIENT = BackgroundGradient(0xFFECE9E6.toInt(), 0xFFFFFFFF.toInt(), 7f)

sealed interface Wallpaper

data class BundledWallpaper(val asset: String) : Wallpaper

class PictureWallpaper(val bytes: ByteArray) : Wallpaper

fun isLightAt(minutes: Int, lightFrom: Int, darkFrom: Int): Boolean =
    if (lightFrom < darkFrom) minutes in lightFrom until darkFrom else minutes >= lightFrom || minutes < darkFrom

fun bundledVariant(asset: String, devicePixelRatio: Float): String {
    val lower = BUNDLED_RATIOS.lastOrNull { it <= devicePixelRatio }
    val upper = BUNDLED_RATIOS.firstOrNull { it >= devicePixelRatio }
    val ratio = when {
        lower == null -> upper!!
        upper == null -> lower
        lower == upper -> lower
        devicePixelRatio < LOW_RATIO_LIMIT || devicePixelRatio > (lower + upper) / 2 -> upper
        else -> lower
    }
    if (ratio == 1f) return asset
    val slash = asset.lastIndexOf('/')
    return "${asset.substring(0, slash)}/${ratio}x/${asset.substring(slash + 1)}"
}

class WallpaperService(
    private val settingsService: SettingsService,
    private val pictureFile: File,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ChangeNotifier() {
    private val onSettingsChanged: () -> Unit = { refresh() }
    private var clockJob: Job? = null
    private var picture: PictureWallpaper? = null

    var wallpaper: Wallpaper? = null
        private set

    init {
        settingsService.addListener(onSettingsChanged)
        refresh()
        scope.launch {
            val bytes = withContext(io) { if (pictureFile.exists()) pictureFile.readBytes() else null }
            if (bytes != null) {
                picture = PictureWallpaper(bytes)
                refresh()
            }
        }
    }

    val showsRecommendations: Boolean
        get() = settingsService.recommendationsDisplay == RecommendationsDisplay.background

    val gradient: BackgroundGradient
        get() = if (settingsService.backgroundChoice == BackgroundChoice.light) LIGHT_GRADIENT else DARK_GRADIENT

    suspend fun setPicture(path: String) {
        val bytes = withContext(io) {
            File(path).readBytes().also { pictureFile.writeBytes(it) }
        }
        picture = PictureWallpaper(bytes)
        settingsService.setBackgroundChoice(BackgroundChoice.picture)
    }

    private fun refresh() {
        clockJob?.cancel()
        val clock = Calendar.getInstance().apply { timeInMillis = now() }
        val choice = settingsService.backgroundChoice
        if (choice == BackgroundChoice.cloudsAuto) {
            clockJob = scope.launch {
                delay((60 - clock.get(Calendar.SECOND)) * 1000L)
                refresh()
            }
        }
        wallpaper = if (showsRecommendations) null else when (choice) {
            BackgroundChoice.dark, BackgroundChoice.light -> null
            BackgroundChoice.cloudsNight -> BundledWallpaper(CLOUDS_BY_NIGHT)
            BackgroundChoice.cloudsDay -> BundledWallpaper(CLOUDS_BY_DAY)
            BackgroundChoice.cloudsAuto -> {
                val minutes = clock.get(Calendar.HOUR_OF_DAY) * 60 + clock.get(Calendar.MINUTE)
                val light = isLightAt(minutes, settingsService.lightFromMinutes, settingsService.darkFromMinutes)
                BundledWallpaper(if (light) CLOUDS_BY_DAY else CLOUDS_BY_NIGHT)
            }
            BackgroundChoice.picture -> picture
        }
        notifyListeners()
    }

    fun dispose() {
        settingsService.removeListener(onSettingsChanged)
        clockJob?.cancel()
    }
}
