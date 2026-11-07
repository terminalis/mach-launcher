package io.github.terminalis.machlauncher

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

const val APK_ASSET_NAME = "mach-launcher-universal-release.apk"
private const val LATEST_RELEASE_URL = "https://api.github.com/repos/terminalis/mach-launcher/releases/latest"

data class Release(val version: String, val notes: String, val apkUrl: String)

fun parseRelease(release: JSONObject): Release {
    val assets = release.getJSONArray("assets")
    val apk = (0 until assets.length()).map { assets.getJSONObject(it) }.first { it.getString("name") == APK_ASSET_NAME }
    return Release(
        version = release.getString("tag_name"),
        notes = if (release.isNull("body")) "" else release.getString("body").trim(),
        apkUrl = apk.getString("browser_download_url"),
    )
}

fun isNewerVersion(latest: String, installed: String): Boolean {
    val a = latest.split(".").map { it.toInt() }
    val b = installed.split(".").map { it.toInt() }
    for (i in 0 until minOf(a.size, b.size)) {
        if (a[i] != b[i]) return a[i] > b[i]
    }
    return a.size > b.size
}

interface Updater {
    suspend fun fetchLatestRelease(): Release
    suspend fun downloadApk(release: Release): String
    fun installApk(apkPath: String): Boolean
    fun requestInstallUnknownAppsPermission()
}

interface UpdateDialogs {
    suspend fun <T> withProgress(label: String, task: suspend () -> T): T
    suspend fun release(title: String, message: String, notes: String = "", offerDownload: Boolean = false): Boolean
    suspend fun confirm(title: String, body: String, action: String): Boolean
}

suspend fun checkForUpdates(updater: Updater, dialogs: UpdateDialogs, installedVersion: String) {
    try {
        val release = dialogs.withProgress(AppStrings.updateCheck) { updater.fetchLatestRelease() }
        val newer = isNewerVersion(release.version, installedVersion)
        val download = dialogs.release(
            title = if (newer) AppStrings.updateAvailableTitle else AppStrings.updateNoUpdateTitle,
            message = if (newer) AppStrings.updateAvailableBody(release.version, installedVersion) else AppStrings.updateNoUpdateBody(installedVersion),
            notes = release.notes,
            offerDownload = newer,
        )
        if (!download) return
        val apkPath = dialogs.withProgress(AppStrings.updateDownloadButton) { updater.downloadApk(release) }
        val install = dialogs.confirm(
            AppStrings.updateReadyToInstallTitle,
            AppStrings.updateReadyToInstallBody(release.version),
            AppStrings.updateInstallButton,
        )
        if (!install || updater.installApk(apkPath)) return
        val openSettings = dialogs.confirm(
            AppStrings.updateInstallPermissionTitle,
            AppStrings.updateInstallPermissionBody,
            AppStrings.updateOpenPermissionSettingsButton,
        )
        if (openSettings) updater.requestInstallUnknownAppsPermission()
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        dialogs.release(AppStrings.updateCheck, AppStrings.updateErrorGeneric)
    }
}

class UpdateService(
    private val channel: AndroidChannel,
    private val directory: File,
    private val releaseUrl: String = LATEST_RELEASE_URL,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : Updater {
    override suspend fun fetchLatestRelease(): Release = withContext(io) {
        get(releaseUrl, "application/vnd.github+json") { parseRelease(JSONObject(it.bufferedReader().readText())) }
    }

    override suspend fun downloadApk(release: Release): String = withContext(io) {
        val file = File(directory, "updates/$APK_ASSET_NAME")
        file.parentFile!!.mkdirs()
        get(release.apkUrl, "application/octet-stream") { input -> file.outputStream().use { input.copyTo(it) } }
        file.path
    }

    override fun installApk(apkPath: String): Boolean = channel.installApk(apkPath)

    override fun requestInstallUnknownAppsPermission() = channel.requestInstallUnknownAppsPermission()

    private fun <T> get(url: String, accept: String, read: (InputStream) -> T): T {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.setRequestProperty("Accept", accept)
            connection.setRequestProperty("User-Agent", "MachLauncher-Updater")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("GET $url failed with status ${connection.responseCode}")
            }
            return connection.inputStream.use(read)
        } finally {
            connection.disconnect()
        }
    }
}
