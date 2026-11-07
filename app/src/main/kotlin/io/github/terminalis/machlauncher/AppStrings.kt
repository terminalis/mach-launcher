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

object AppStrings {
    const val aboutLauncher = "About Mach Launcher"
    const val appInfo = "Application info"
    const val background = "Background"
    const val homeScreen = "Home screen"
    const val homeScreenIntro = "Mach takes over the Home button and the screen the TV starts on through an accessibility switch."
    const val homeScreenAutoStartStep = "1. Some TVs have an app that decides which apps may start by themselves. If yours does, allow Mach Launcher there."
    const val homeScreenAccessibilityStep = "2. Open Settings → Device Preferences → Accessibility, choose Mach Launcher and turn it on."
    const val homeScreenWarning = "The TV warns that the switch can collect what you type. It shows that warning for every accessibility app. Mach's switch is only told which app has come to the front: it can't read the screen or see what you press."
    const val homeScreenGoBack = "To go back to the TV's own Home screen, press Reset Home screen."
    const val resetHomeScreen = "Reset Home screen"
    const val darkGradient = "Dark gradient"
    const val lightGradient = "Light gradient"
    const val cloudsByNight = "Clouds by night"
    const val cloudsByDay = "Clouds by day"
    const val cloudsAuto = "Clouds, auto"
    const val lightFrom = "Light from"
    const val darkFrom = "Dark from"
    const val applications = "Applications"
    const val favoriteApps = "Favorite Apps"
    const val hide = "Hide"
    const val hiddenApplications = "Hidden Apps"
    const val loading = "Loading"
    const val allApplications = "All Apps"
    const val open = "Open"
    const val customImage = "Custom image"
    const val reorder = "Move"
    const val show = "Show"
    const val updateCheck = "Check for updates"
    const val updateNoUpdateTitle = "No updates available"
    fun updateNoUpdateBody(currentVersion: String) = "You are already on the latest version ($currentVersion)."
    const val updateAvailableTitle = "Update available"
    fun updateAvailableBody(latestVersion: String, currentVersion: String) = "Version $latestVersion is available (current: $currentVersion)."
    const val updateChangelog = "What's new"
    const val updateDownloadButton = "Download"
    const val updateReadyToInstallTitle = "Ready to install"
    fun updateReadyToInstallBody(latestVersion: String) = "The APK for version $latestVersion is downloaded. Start installation now?"
    const val updateInstallButton = "Install"
    const val updateInstallPermissionTitle = "Installer permission required"
    const val updateInstallPermissionBody = "Allow Mach Launcher to install unknown apps, then retry the update."
    const val updateOpenPermissionSettingsButton = "Open permission settings"
    const val updateErrorGeneric = "Update check failed. Please try again."
    fun textAboutDialog(repoUrl: String) = "Mach Launcher is an open-source launcher for Android TV.\n\nSource code available at $repoUrl."
    const val uninstall = "Uninstall"
    const val addToFavorites = "Add to Fav"
    const val removeFromFavorites = "Remove from Fav"
    const val selectImage = "Select Image"
    const val mediaSelectionHint = "D-pad: navigate  •  Select: set wallpaper  •  Back: cancel"
    const val mediaPermissionRequired = "Media access permission is required"
    const val watch = "Watch"
    const val resume = "Resume"
    const val interfaceTitle = "Interface"
    const val recommendations = "Recommendations"
    const val playNext = "Play Next"
    const val numberOfTitles = "Number of titles"
    const val showAs = "Show as"
    const val off = "Off"
    const val row = "Row"
    const val tvListingsPermissionTitle = "TV listings access needed"
    const val tvListingsPermissionBody = "Allow access to TV listings to show titles from your streaming apps."
    const val grantTvListings = "Grant permission"
    const val checkAgain = "Check again"
}
