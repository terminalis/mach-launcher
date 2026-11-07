package io.github.terminalis.machlauncher

import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private class FakeUpdater(private val release: Release?, private val installed: Boolean = true) : Updater {
    val calls = ArrayList<String>()

    override suspend fun fetchLatestRelease() = release ?: throw IOException("offline")

    override suspend fun downloadApk(release: Release): String {
        calls.add("download")
        return "/apk"
    }

    override fun installApk(apkPath: String): Boolean {
        calls.add("install $apkPath")
        return installed
    }

    override fun requestInstallUnknownAppsPermission() {
        calls.add("permission")
    }
}

private class FakeDialogs(private val download: Boolean = true, private val confirms: List<Boolean> = listOf(true, true)) : UpdateDialogs {
    val shown = ArrayList<String>()
    private var confirmed = 0

    override suspend fun <T> withProgress(label: String, task: suspend () -> T): T {
        shown.add("progress $label")
        return task()
    }

    override suspend fun release(title: String, message: String, notes: String, offerDownload: Boolean): Boolean {
        shown.add("release $title | $message | $notes | $offerDownload")
        return offerDownload && download
    }

    override suspend fun confirm(title: String, body: String, action: String): Boolean {
        shown.add("confirm $title")
        return confirms[confirmed++]
    }
}

class UpdateFlowTest {
    private val newer = Release("1.0.10", "Faster start", "https://example.com/apk")

    @Test
    fun `the release dialog offers the download with the notes`() = runTest {
        val dialogs = FakeDialogs(download = false)

        checkForUpdates(FakeUpdater(newer), dialogs, "1.0.9")

        assertEquals(
            listOf(
                "progress Check for updates",
                "release Update available | Version 1.0.10 is available (current: 1.0.9). | Faster start | true",
            ),
            dialogs.shown,
        )
    }

    @Test
    fun `the release dialog without an update has only OK`() = runTest {
        val dialogs = FakeDialogs()
        val updater = FakeUpdater(newer)

        checkForUpdates(updater, dialogs, "1.0.10")

        assertEquals("release No updates available | You are already on the latest version (1.0.10). | Faster start | false", dialogs.shown.last())
        assertEquals(emptyList<String>(), updater.calls)
    }

    @Test
    fun `a download then asks to install, and installing ends the flow`() = runTest {
        val dialogs = FakeDialogs()
        val updater = FakeUpdater(newer)

        checkForUpdates(updater, dialogs, "1.0.9")

        assertEquals(listOf("download", "install /apk"), updater.calls)
        assertEquals("confirm Ready to install", dialogs.shown.last())
    }

    @Test
    fun `without the installer permission it offers the permission settings`() = runTest {
        val dialogs = FakeDialogs()
        val updater = FakeUpdater(newer, installed = false)

        checkForUpdates(updater, dialogs, "1.0.9")

        assertEquals("confirm Installer permission required", dialogs.shown.last())
        assertEquals(listOf("download", "install /apk", "permission"), updater.calls)
    }

    @Test
    fun `declining the install stops there`() = runTest {
        val updater = FakeUpdater(newer)

        checkForUpdates(updater, FakeDialogs(confirms = listOf(false)), "1.0.9")

        assertEquals(listOf("download"), updater.calls)
    }

    @Test
    fun `a failed check says so`() = runTest {
        val dialogs = FakeDialogs()

        checkForUpdates(FakeUpdater(null), dialogs, "1.0.9")

        assertEquals("release Check for updates | Update check failed. Please try again. |  | false", dialogs.shown.last())
    }
}
