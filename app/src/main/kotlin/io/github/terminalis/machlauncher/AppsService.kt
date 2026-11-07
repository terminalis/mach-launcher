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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

const val HOME_COLUMNS = 6
private const val HIDDEN_KEY = "hidden_apps"
private const val DOCK_KEY = "dock_apps"
private const val GRID_KEY = "grid_order"

class App(data: Map<String, Any?>, var hidden: Boolean) {
    val packageName = data["packageName"] as String
    val name = data["name"] as String
    val version = data["version"] as String
}

class AppsService(
    private val channel: AndroidChannel,
    private val preferences: SharedPreferences,
    private val scope: CoroutineScope,
) : ChangeNotifier() {
    private val apps = LinkedHashMap<String, App>()
    private val banners = HashMap<String, Deferred<ByteArray>>()
    private val icons = HashMap<String, Deferred<ByteArray>>()
    private val hiddenApps = preferences.getStringList(HIDDEN_KEY).orEmpty().toMutableSet()
    private val dockApps = preferences.getStringList(DOCK_KEY).orEmpty().toMutableList()
    private var gridOrder = preferences.getStringList(GRID_KEY)?.toMutableList()

    var initialized = false
        private set

    init {
        scope.launch {
            for (app in channel.getApplications()) put(app)
            dockApps.removeAll { it !in apps }
            channel.addAppsChangedListener(::onAppsChanged)
            initialized = true
            notifyListeners()
        }
    }

    val applications: List<App> get() = apps.values.sortedBy { it.name.lowercase() }

    val dock: List<App> get() = dockApps.mapNotNull { apps[it] }

    val dockFull: Boolean get() = dockApps.size >= HOME_COLUMNS

    val favoriteChoices: List<App> get() = applications.filter { !it.hidden && !isDocked(it) }

    fun isDocked(app: App): Boolean = app.packageName in dockApps

    val grid: List<App>
        get() {
            val visible = favoriteChoices
            val order = gridOrder ?: return visible
            return visible.sortedBy { app -> order.indexOf(app.packageName).let { if (it < 0) order.size else it } }
        }

    private fun onAppsChanged(event: Map<String, Any?>) {
        when (event["action"]) {
            "PACKAGE_CHANGED" -> {
                @Suppress("UNCHECKED_CAST")
                put(event["app"] as Map<String, Any?>)
            }
            "PACKAGE_REMOVED" -> {
                apps.remove(event["packageName"])
                dockApps.remove(event["packageName"])
            }
        }
        notifyListeners()
    }

    private fun put(data: Map<String, Any?>) {
        val app = App(data, data["packageName"] in hiddenApps)
        apps[app.packageName] = app
        banners.remove(app.packageName)
        icons.remove(app.packageName)
    }

    suspend fun getAppBanner(packageName: String): ByteArray =
        banners.getOrPut(packageName) { scope.async { channel.getApplicationBanner(packageName) } }.await()

    suspend fun getAppIcon(packageName: String): ByteArray =
        icons.getOrPut(packageName) { scope.async { channel.getApplicationIcon(packageName) } }.await()

    fun launchApp(app: App) = channel.launchApp(app.packageName)

    fun openAppInfo(app: App) = channel.openAppInfo(app.packageName)

    fun uninstallApp(app: App) = channel.uninstallApp(app.packageName)

    fun openSettings() = channel.openSettings()

    fun openHomeSettings() = channel.openHomeSettings()

    fun resetHomeScreen(): Boolean = channel.resetHomeScreen()

    fun openInputPicker() = channel.openInputPicker()

    fun toggleDocked(app: App) {
        if (!dockApps.remove(app.packageName)) dockApps.add(app.packageName)
        preferences.edit().putStringList(DOCK_KEY, dockApps).apply()
        notifyListeners()
    }

    fun setHidden(app: App, hidden: Boolean) {
        app.hidden = hidden
        if (hidden) hiddenApps.add(app.packageName) else hiddenApps.remove(app.packageName)
        dockApps.remove(app.packageName)
        preferences.edit().putStringList(HIDDEN_KEY, hiddenApps.toList()).putStringList(DOCK_KEY, dockApps).apply()
        notifyListeners()
    }

    fun move(app: App, newIndex: Int) {
        val order = if (isDocked(app)) dockApps else grid.map { it.packageName }.toMutableList().also { gridOrder = it }
        order.remove(app.packageName)
        order.add(newIndex, app.packageName)
        notifyListeners()
    }

    fun saveOrder() {
        val editor = preferences.edit().putStringList(DOCK_KEY, dockApps)
        gridOrder?.let { editor.putStringList(GRID_KEY, it) }
        editor.apply()
    }
}
