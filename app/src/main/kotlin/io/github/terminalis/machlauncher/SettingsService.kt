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

import android.content.SharedPreferences

private const val BACKGROUND_CHOICE_KEY = "background_choice"
private const val LIGHT_FROM_KEY = "clouds_light_from_minutes"
private const val DARK_FROM_KEY = "clouds_dark_from_minutes"
private const val RECOMMENDATIONS_DISPLAY_KEY = "recommendations_display"
private const val RECOMMENDATION_SOURCES_KEY = "recommendation_sources"
private const val RECOMMENDATION_TITLE_COUNT_KEY = "recommendation_title_count"
private const val RECOMMENDATION_PICKS_KEY = "recommendation_picks"
private const val RECOMMENDATION_DRAWN_AT_KEY = "recommendation_drawn_at"

enum class BackgroundChoice { dark, light, cloudsNight, cloudsDay, cloudsAuto, picture }

enum class RecommendationsDisplay { off, row, background }

val RECOMMENDATION_TITLE_COUNTS = listOf(3, 4, 5, 6, 7, 8, 9)

class SettingsService(private val preferences: SharedPreferences) : ChangeNotifier() {
    val backgroundChoice: BackgroundChoice
        get() = BackgroundChoice.entries.firstOrNull { it.name == preferences.getString(BACKGROUND_CHOICE_KEY, null) }
            ?: BackgroundChoice.cloudsDay

    val lightFromMinutes: Int get() = preferences.getInt(LIGHT_FROM_KEY, 7 * 60)

    val darkFromMinutes: Int get() = preferences.getInt(DARK_FROM_KEY, 19 * 60)

    val recommendationsDisplay: RecommendationsDisplay
        get() = RecommendationsDisplay.entries.firstOrNull { it.name == preferences.getString(RECOMMENDATIONS_DISPLAY_KEY, null) }
            ?: RecommendationsDisplay.off

    val recommendationSources: List<String> get() = preferences.getStringList(RECOMMENDATION_SOURCES_KEY) ?: emptyList()

    val recommendationTitleCount: Int get() = preferences.getInt(RECOMMENDATION_TITLE_COUNT_KEY, 3)

    val recommendationPicks: List<String> get() = preferences.getStringList(RECOMMENDATION_PICKS_KEY) ?: emptyList()

    val recommendationDrawnAt: Long?
        get() = if (preferences.contains(RECOMMENDATION_DRAWN_AT_KEY)) preferences.getLong(RECOMMENDATION_DRAWN_AT_KEY, 0) else null

    fun setBackgroundChoice(choice: BackgroundChoice) {
        preferences.edit().putString(BACKGROUND_CHOICE_KEY, choice.name).apply()
        notifyListeners()
    }

    fun setLightFromMinutes(minutes: Int) {
        preferences.edit().putInt(LIGHT_FROM_KEY, minutes).apply()
        notifyListeners()
    }

    fun setDarkFromMinutes(minutes: Int) {
        preferences.edit().putInt(DARK_FROM_KEY, minutes).apply()
        notifyListeners()
    }

    fun setRecommendationsDisplay(display: RecommendationsDisplay) {
        preferences.edit().putString(RECOMMENDATIONS_DISPLAY_KEY, display.name).apply()
        notifyListeners()
    }

    fun setRecommendationSourceSelected(source: String, selected: Boolean) {
        val sources = recommendationSources.toMutableSet()
        if (selected) sources.add(source) else sources.remove(source)
        preferences.edit().putStringList(RECOMMENDATION_SOURCES_KEY, sources.sorted()).apply()
        notifyListeners()
    }

    fun setRecommendationTitleCount(count: Int) {
        preferences.edit().putInt(RECOMMENDATION_TITLE_COUNT_KEY, count).apply()
        notifyListeners()
    }

    fun setRecommendationPicks(picks: List<String>, drawnAt: Long) {
        preferences.edit().putStringList(RECOMMENDATION_PICKS_KEY, picks).putLong(RECOMMENDATION_DRAWN_AT_KEY, drawnAt).apply()
    }
}
