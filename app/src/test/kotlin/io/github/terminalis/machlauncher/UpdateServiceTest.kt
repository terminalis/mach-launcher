package io.github.terminalis.machlauncher

import java.io.File
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private fun asset(name: String) =
    JSONObject().put("name", name).put("browser_download_url", "https://example.com/$name")

private fun release(body: Any?, vararg names: String) = JSONObject()
    .put("tag_name", "1.2.0")
    .put("body", body ?: JSONObject.NULL)
    .put("assets", JSONArray(names.map { asset(it) }))

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateServiceTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun TestScope.service(url: String) =
        UpdateService(FakeChannel(), folder.root, url, StandardTestDispatcher(testScheduler))

    @Test
    fun `parseRelease takes the tag, the notes and the universal APK`() {
        val release = parseRelease(
            release(
                "Faster start\n",
                "mach-launcher-arm64-v8a-release.apk",
                "mach-launcher-universal-release.apk",
                "mach-launcher-armeabi-v7a-release.apk",
            ),
        )

        assertEquals(Release("1.2.0", "Faster start", "https://example.com/mach-launcher-universal-release.apk"), release)
    }

    @Test
    fun `parseRelease reads a missing body as no notes`() {
        assertEquals("", parseRelease(release(null, "mach-launcher-universal-release.apk")).notes)
    }

    @Test
    fun `parseRelease fails without the universal APK`() {
        assertThrows(NoSuchElementException::class.java) { parseRelease(release("", "mach-launcher-arm64-v8a-release.apk")) }
    }

    @Test
    fun `isNewerVersion compares dotted integers`() {
        assertTrue(isNewerVersion("1.0.10", "1.0.9"))
        assertTrue(isNewerVersion("2.0.0", "1.9.9"))
        assertFalse(isNewerVersion("1.0.0", "1.0.0"))
        assertFalse(isNewerVersion("1.0.9", "1.0.10"))
        assertTrue(isNewerVersion("1.0.1", "1.0"))
        assertFalse(isNewerVersion("1.0", "1.0.1"))
    }

    @Test
    fun `isNewerVersion throws on a malformed tag`() {
        assertThrows(NumberFormatException::class.java) { isNewerVersion("v1.0.1", "1.0.0") }
        assertThrows(NumberFormatException::class.java) { isNewerVersion("1.0.1-beta", "1.0.0") }
    }

    @Test
    fun `fetching reads the latest release and names the updater`() = runTest {
        val body = release("Notes", "mach-launcher-universal-release.apk").toString().toByteArray()
        LocalHttpServer(200, body).use { server ->
            val latest = service(server.url).fetchLatestRelease()

            assertEquals("1.2.0", latest.version)
            assertTrue(server.requestLines.any { it.equals("Accept: application/vnd.github+json", ignoreCase = true) })
            assertTrue(server.requestLines.any { it.equals("User-Agent: MachLauncher-Updater", ignoreCase = true) })
        }
    }

    @Test
    fun `a fetch that isn't a plain 200 throws`() = runTest {
        val body = release("Notes", "mach-launcher-universal-release.apk").toString().toByteArray()
        LocalHttpServer(202, body).use { server ->
            val error = runCatching { service(server.url).fetchLatestRelease() }.exceptionOrNull()

            assertTrue(error is IOException)
        }
    }

    @Test
    fun `the APK is downloaded under updates with its release name`() = runTest {
        val apk = byteArrayOf(1, 2, 3)
        LocalHttpServer(200, apk).use { server ->
            val path = service(server.url).downloadApk(Release("1.2.0", "", server.url))

            assertEquals(File(folder.root, "updates/mach-launcher-universal-release.apk").path, path)
            assertArrayEquals(apk, File(path).readBytes())
        }
    }
}
