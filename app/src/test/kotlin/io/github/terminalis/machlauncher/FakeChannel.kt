package io.github.terminalis.machlauncher

import kotlinx.coroutines.yield

private fun png(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

val landscapePng = png(
    137, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13, 73, 72, 68, 82, 0, 0, 0, 2, 0, 0, 0, 1, 8, 2, 0, 0, 0, 123, 64, 232,
    221, 0, 0, 0, 15, 73, 68, 65, 84, 120, 218, 99, 252, 207, 192, 192, 192, 192, 0, 0, 6, 8, 1, 1, 114, 60, 202, 50, 0,
    0, 0, 0, 73, 69, 78, 68, 174, 66, 96, 130,
)

val portraitPng = png(
    137, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13, 73, 72, 68, 82, 0, 0, 0, 1, 0, 0, 0, 2, 8, 2, 0, 0, 0, 22, 227, 33,
    112, 0, 0, 0, 16, 73, 68, 65, 84, 120, 218, 99, 248, 207, 192, 192, 196, 192, 192, 0, 0, 7, 9, 1, 2, 124, 209, 243,
    180, 0, 0, 0, 0, 73, 69, 78, 68, 174, 66, 96, 130,
)

class FakeChannel : AndroidChannel {
    var applications: List<Map<String, Any?>> = emptyList()
    var rows: List<Map<String, Any?>> = emptyList()
    var playNext: List<Map<String, Any?>> = emptyList()
    var permitted = true
    var image: suspend (String) -> ByteArray = { landscapePng }
    var programReads = 0
    var bannerReads = 0
    var resetResult = true
    val calls = ArrayList<String>()
    lateinit var sendEvent: (Map<String, Any?>) -> Unit

    override suspend fun getApplications(): List<Map<String, Any?>> {
        yield()
        return applications
    }

    override suspend fun getApplicationBanner(packageName: String): ByteArray {
        bannerReads++
        return ByteArray(0)
    }

    override suspend fun getApplicationIcon(packageName: String) = ByteArray(0)

    override fun launchApp(packageName: String) {
        calls.add("launchApp $packageName")
    }

    override fun openSettings() {
        calls.add("openSettings")
    }

    override fun openHomeSettings() {
        calls.add("openHomeSettings")
    }

    override fun resetHomeScreen() = resetResult

    override fun openAppInfo(packageName: String) {
        calls.add("openAppInfo $packageName")
    }

    override fun uninstallApp(packageName: String) {
        calls.add("uninstallApp $packageName")
    }

    override fun openInputPicker() {
        calls.add("openInputPicker")
    }

    override fun checkMediaPermissions() = true

    override fun requestMediaPermissions() = Unit

    override suspend fun getMediaStoreImages() = emptyList<String>()

    override fun installApk(apkPath: String) = true

    override fun requestInstallUnknownAppsPermission() = Unit

    override fun addAppsChangedListener(listener: (Map<String, Any?>) -> Unit) {
        sendEvent = listener
    }

    override fun checkTvListingsPermission() = permitted

    override fun requestTvListingsPermission() = Unit

    override suspend fun getPrograms(): Programs {
        programReads++
        yield()
        return Programs(rows, playNext)
    }

    override fun launchProgram(packageName: String, intentUri: String) {
        calls.add("launchProgram $packageName $intentUri")
    }

    override suspend fun loadContentUriImage(contentUri: String): ByteArray {
        yield()
        return image(contentUri)
    }

    override fun isLandscape(image: ByteArray) = sizeAt(image, 16) > sizeAt(image, 20)

    private fun sizeAt(image: ByteArray, offset: Int) =
        (0 until 4).fold(0) { value, index -> value * 256 + (image[offset + index].toInt() and 0xFF) }
}
