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

enum class HomeArea { recommendations, dock, grid }

class LauncherState : ChangeNotifier() {
    var focusedArea = HomeArea.dock
        private set
    private var focusRequest: String? = null

    val appGridFocused: Boolean get() = focusedArea == HomeArea.grid

    fun setFocusedArea(area: HomeArea) {
        if (focusedArea == area) return
        focusedArea = area
        notifyListeners()
    }

    fun requestFocus(packageName: String) {
        focusRequest = packageName
    }

    fun consumeFocusRequest(packageName: String): Boolean {
        if (focusRequest != packageName) return false
        focusRequest = null
        return true
    }
}
