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

class Programs(val rows: List<Map<String, Any?>>, val playNext: List<Map<String, Any?>>)

interface AndroidChannel {
    suspend fun getApplications(): List<Map<String, Any?>>
    suspend fun getApplicationBanner(packageName: String): ByteArray
    suspend fun getApplicationIcon(packageName: String): ByteArray
    fun launchApp(packageName: String)
    fun openSettings()
    fun openHomeSettings()
    fun resetHomeScreen(): Boolean
    fun openAppInfo(packageName: String)
    fun uninstallApp(packageName: String)
    fun openInputPicker()
    fun checkMediaPermissions(): Boolean
    fun requestMediaPermissions()
    suspend fun getMediaStoreImages(): List<String>
    fun installApk(apkPath: String): Boolean
    fun requestInstallUnknownAppsPermission()
    fun addAppsChangedListener(listener: (Map<String, Any?>) -> Unit)
    fun checkTvListingsPermission(): Boolean
    fun requestTvListingsPermission()
    suspend fun getPrograms(): Programs
    fun launchProgram(packageName: String, intentUri: String)
    suspend fun loadContentUriImage(contentUri: String): ByteArray
    fun isLandscape(image: ByteArray): Boolean
}
