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

private val TAB_TITLES = listOf(AppStrings.allApplications, AppStrings.favoriteApps, AppStrings.hiddenApplications)
private val TAB_GLYPHS = listOf(Glyphs.GRID, Glyphs.STAR_FILL, Glyphs.EYE_SLASH)

class SettingsPages(
    private val apps: AppsService,
    private val recommendations: RecommendationsService,
    private val settings: SettingsService,
    private val navigator: PanelNavigator,
    private val setPicture: (String) -> Unit,
) {
    fun first() = PanelPage(
        title = { AppStrings.interfaceTitle },
        rows = {
            listOf(
                PanelRow(AppStrings.applications, RowLeading.Glyph(Glyphs.GRID), RowTrailing.Chevron, focusFirst = true) { navigator.push(applications()) },
                PanelRow(AppStrings.recommendations, RowLeading.Glyph(Glyphs.FILM), RowTrailing.Chevron) { navigator.push(recommendations()) },
                PanelRow(AppStrings.background, RowLeading.Glyph(Glyphs.PHOTOS), RowTrailing.Chevron) { navigator.push(background()) },
                PanelRow(AppStrings.homeScreen, RowLeading.Glyph(Glyphs.HOUSE), RowTrailing.Chevron) { navigator.push(homeScreen()) },
                PanelRow(AppStrings.aboutLauncher, RowLeading.Glyph(Glyphs.INFO), dividerBefore = true) { navigator.showAbout() },
            )
        },
    )

    fun appRow(app: App, focusFirst: Boolean, onPress: (() -> Unit)? = null) = PanelRow(
        app.name,
        RowLeading.AppIcon(app.packageName),
        if (onPress == null) RowTrailing.Chevron else RowTrailing.None,
        focusFirst = focusFirst,
    ) { (onPress ?: { navigator.push(appDetails(app)) })() }

    fun applicationsFor(tab: Int): List<App> = apps.applications.filter {
        when (tab) {
            0 -> !it.hidden
            1 -> apps.isDocked(it)
            else -> it.hidden
        }
    }

    fun applications(): PanelPage {
        val tabs = PanelTabs(TAB_GLYPHS)
        return PanelPage(
            title = { TAB_TITLES[tabs.selected] },
            rows = { applicationsFor(tabs.selected).mapIndexed { i, app -> appRow(app, i == 0) } },
            emptyText = "No applications found",
            centred = true,
            tabs = tabs,
        )
    }

    fun appDetails(app: App) = PanelPage(
        title = { app.name },
        rows = { appActions(app, null) },
        appHeader = app,
        opensUnfocused = true,
    )

    fun appPanel(app: App, onMove: () -> Unit) = PanelPage(
        title = { app.name },
        rows = { appActions(app, onMove) },
        appHeader = app,
        showsPackage = true,
        opensUnfocused = true,
    )

    fun appActions(app: App, onMove: (() -> Unit)?): List<PanelRow> {
        val docked = apps.isDocked(app)
        fun entry(glyph: String, label: String, dividerBefore: Boolean = false, action: () -> Unit) =
            PanelRow(label, RowLeading.Glyph(glyph), dividerBefore = dividerBefore) {
                action()
                navigator.pop()
            }
        val rows = ArrayList<PanelRow>()
        if (onMove != null) rows.add(entry(Glyphs.MOVE, AppStrings.reorder, action = onMove))
        rows.add(entry(Glyphs.OPEN, AppStrings.open) { apps.launchApp(app) })
        if (docked || !apps.dockFull) {
            rows.add(
                entry(
                    if (docked) Glyphs.STAR_FILL else Glyphs.STAR,
                    if (docked) AppStrings.removeFromFavorites else AppStrings.addToFavorites,
                ) { apps.toggleDocked(app) },
            )
        }
        rows.add(
            entry(if (app.hidden) Glyphs.EYE else Glyphs.EYE_SLASH, if (app.hidden) AppStrings.show else AppStrings.hide) {
                apps.setHidden(app, !app.hidden)
            },
        )
        rows.add(entry(Glyphs.INFO, AppStrings.appInfo, dividerBefore = true) { apps.openAppInfo(app) })
        rows.add(entry(Glyphs.TRASH, AppStrings.uninstall) { apps.uninstallApp(app) })
        return rows
    }

    fun favoritePicker(onChosen: (App) -> Unit) = PanelPage(
        title = { AppStrings.addToFavorites },
        rows = { apps.favoriteChoices.mapIndexed { i, app -> appRow(app, i == 0) { onChosen(app) } } },
        centred = true,
    )

    fun recommendations() = PanelPage(
        title = { AppStrings.recommendations },
        rows = {
            val selected = settings.recommendationSources
            fun source(id: String, name: String, dividerBefore: Boolean = false): PanelRow {
                val on = id in selected
                return PanelRow(name, trailing = RowTrailing.Toggle(on), dividerBefore = dividerBefore) {
                    recommendations.setSources(if (on) selected - id else selected + id)
                }
            }
            val rows = ArrayList<PanelRow>()
            if (!recommendations.hasPermission) {
                rows.add(PanelRow(AppStrings.grantTvListings, RowLeading.Glyph(Glyphs.LOCK_OPEN), focusFirst = true) { recommendations.requestPermission() })
            }
            rows.add(
                PanelRow(
                    AppStrings.showAs,
                    trailing = RowTrailing.Label(recommendationsDisplayLabel(settings.recommendationsDisplay)),
                    dividerBefore = !recommendations.hasPermission,
                    focusFirst = recommendations.hasPermission,
                ) {
                    navigator.push(
                        choice(AppStrings.showAs, RecommendationsDisplay.entries, settings.recommendationsDisplay, ::recommendationsDisplayLabel) {
                            settings.setRecommendationsDisplay(it)
                        },
                    )
                },
            )
            rows.add(
                PanelRow(AppStrings.numberOfTitles, trailing = RowTrailing.Label("${settings.recommendationTitleCount}")) {
                    navigator.push(
                        choice(AppStrings.numberOfTitles, RECOMMENDATION_TITLE_COUNTS, settings.recommendationTitleCount, { "$it" }) {
                            recommendations.setTitleCount(it)
                        },
                    )
                },
            )
            rows.add(source(PLAY_NEXT_SOURCE, AppStrings.playNext, dividerBefore = true))
            for (app in recommendations.apps) rows.add(source(app.packageName, app.name))
            rows
        },
    )

    fun <T> choice(title: String, choices: List<T>, current: T, label: (T) -> String, onChoose: (T) -> Unit) = PanelPage(
        title = { title },
        rows = {
            choices.map { value ->
                PanelRow(label(value), trailing = if (value == current) RowTrailing.Check else RowTrailing.None, focusFirst = value == current) {
                    onChoose(value)
                    navigator.pop()
                }
            }
        },
    )

    fun background() = PanelPage(
        title = { AppStrings.background },
        rows = {
            val current = settings.backgroundChoice
            fun tile(value: BackgroundChoice, glyph: String, label: String, first: Boolean = false) =
                PanelRow(label, RowLeading.Glyph(glyph), if (current == value) RowTrailing.Check else RowTrailing.None, focusFirst = first) {
                    settings.setBackgroundChoice(value)
                }
            fun time(title: String, glyph: String, minutes: Int, set: (Int) -> Unit) =
                PanelRow(title, RowLeading.Glyph(glyph), RowTrailing.Label(formatMinutes(minutes)), indented = true) {
                    navigator.push(choice(title, (0 until 24 * 60 step 30).toList(), minutes, ::formatMinutes, set))
                }
            val rows = arrayListOf(
                tile(BackgroundChoice.dark, Glyphs.MOON, AppStrings.darkGradient, first = true),
                tile(BackgroundChoice.light, Glyphs.SUN, AppStrings.lightGradient),
                tile(BackgroundChoice.cloudsNight, Glyphs.CLOUD_MOON, AppStrings.cloudsByNight),
                tile(BackgroundChoice.cloudsDay, Glyphs.CLOUD_SUN, AppStrings.cloudsByDay),
                tile(BackgroundChoice.cloudsAuto, Glyphs.CLOCK, AppStrings.cloudsAuto),
            )
            if (current == BackgroundChoice.cloudsAuto) {
                rows.add(time(AppStrings.lightFrom, Glyphs.SUN, settings.lightFromMinutes, settings::setLightFromMinutes))
                rows.add(time(AppStrings.darkFrom, Glyphs.MOON_STARS, settings.darkFromMinutes, settings::setDarkFromMinutes))
            }
            rows.add(
                PanelRow(AppStrings.customImage, RowLeading.Glyph(Glyphs.PHOTO), if (current == BackgroundChoice.picture) RowTrailing.Check else RowTrailing.None) {
                    navigator.pickPhoto(setPicture)
                },
            )
            rows
        },
    )

    fun homeScreen() = PanelPage(
        title = { AppStrings.homeScreen },
        paragraphs = listOf(
            AppStrings.homeScreenIntro,
            AppStrings.homeScreenAutoStartStep,
            AppStrings.homeScreenAccessibilityStep,
            AppStrings.homeScreenWarning,
            AppStrings.homeScreenGoBack,
        ),
        rows = {
            listOf(
                PanelRow(AppStrings.resetHomeScreen, focusFirst = true) {
                    if (!apps.resetHomeScreen()) navigator.showHomeAppChoice()
                },
            )
        },
    )
}
