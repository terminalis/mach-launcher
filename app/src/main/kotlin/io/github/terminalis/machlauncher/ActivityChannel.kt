package io.github.terminalis.machlauncher

import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ActivityChannel(private val activity: MainActivity) : AndroidChannel {
    override suspend fun getApplications(): List<Map<String, Any?>> =
        withContext(Dispatchers.IO) { activity.installedApps.getApplications() }

    override suspend fun getApplicationBanner(packageName: String): ByteArray =
        withContext(Dispatchers.IO) { activity.installedApps.getApplicationBanner(packageName) }

    override suspend fun getApplicationIcon(packageName: String): ByteArray =
        withContext(Dispatchers.IO) { activity.installedApps.getApplicationIcon(packageName) }

    override fun launchApp(packageName: String) {
        activity.launchApp(packageName)
    }

    override fun openSettings() {
        activity.openSettings()
    }

    override fun openHomeSettings() {
        activity.openHomeSettings()
    }

    override fun resetHomeScreen(): Boolean = activity.resetHomeScreen()

    override fun openAppInfo(packageName: String) {
        activity.openAppInfo(packageName)
    }

    override fun uninstallApp(packageName: String) {
        activity.uninstallApp(packageName)
    }

    override fun openInputPicker() {
        activity.openInputPicker()
    }

    override fun checkMediaPermissions(): Boolean = activity.checkMediaPermissions()

    override fun requestMediaPermissions() = activity.requestMediaPermissions()

    override suspend fun getMediaStoreImages(): List<String> =
        withContext(Dispatchers.IO) { activity.contentImages.getMediaStoreImages().map { it["path"] as String } }

    override fun installApk(apkPath: String): Boolean = activity.installApk(apkPath)

    override fun requestInstallUnknownAppsPermission() {
        activity.requestInstallUnknownAppsPermission()
    }

    override fun addAppsChangedListener(listener: (Map<String, Any?>) -> Unit) {
        activity.appChanges.start { listener(it) }
    }

    override fun checkTvListingsPermission(): Boolean = activity.checkTvListingsPermission()

    override fun requestTvListingsPermission() = activity.requestTvListingsPermission()

    override suspend fun getPrograms(): Programs = withContext(Dispatchers.IO) {
        val programs = activity.tvPrograms.getPrograms()
        Programs(programs.getValue("rows"), programs.getValue("playNext"))
    }

    override fun launchProgram(packageName: String, intentUri: String) {
        activity.tvPrograms.launchProgram(packageName, intentUri)
    }

    override suspend fun loadContentUriImage(contentUri: String): ByteArray =
        withContext(Dispatchers.IO) { activity.contentImages.loadContentUriImage(contentUri) }

    override fun isLandscape(image: ByteArray): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(image, 0, image.size, bounds)
        return bounds.outWidth > bounds.outHeight
    }
}
