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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun app(packageName: String, name: String = packageName) =
    mapOf("packageName" to packageName, "name" to name, "version" to "1.0")

private fun packages(apps: List<App>) = apps.map { it.packageName }

@OptIn(ExperimentalCoroutinesApi::class)
class AppsServiceTest {
    private val channel = FakeChannel()
    private lateinit var preferences: FakePreferences

    private fun TestScope.service(apps: List<Map<String, Any?>>, stored: Map<String, List<String>> = emptyMap()): AppsService {
        preferences = FakePreferences(stored.mapValues { it.value.joinToString("\n") })
        channel.applications = apps
        return AppsService(channel, preferences, backgroundScope).also { runCurrent() }
    }

    private fun stored(key: String) = preferences.getStringList(key)

    @Test
    fun `Apps and the grid are A to Z ignoring case`() = runTest {
        val service = service(listOf(app("e", "Explorer"), app("d", "distributor"), app("a", "Arc")))

        assertTrue(service.initialized)
        assertEquals(listOf("a", "d", "e"), packages(service.applications))
        assertEquals(listOf("a", "d", "e"), packages(service.grid))
    }

    @Test
    fun `The dock keeps its stored order and drops apps Android no longer lists`() = runTest {
        val service = service(listOf(app("a"), app("b"), app("c")), mapOf("dock_apps" to listOf("c", "gone", "a")))

        assertEquals(listOf("c", "a"), packages(service.dock))
        assertEquals(listOf("b"), packages(service.grid))
        assertTrue(service.isDocked(service.dock.first()))
    }

    @Test
    fun `An app Android no longer lists doesn't hold a dock place`() = runTest {
        val apps = (0 until 5).map { app("app.$it") }
        val service = service(apps, mapOf("dock_apps" to (0 until 5).map { "app.$it" } + "gone"))

        assertEquals(5, service.dock.size)
        assertFalse(service.dockFull)
    }

    @Test
    fun `Hiding a docked app with the dock full, adding another and showing the first keeps six in the dock`() = runTest {
        val apps = (0 until 7).map { app("app.$it") }
        val service = service(apps, mapOf("dock_apps" to (0 until 6).map { "app.$it" }))
        val first = service.dock.first()
        assertTrue(service.dockFull)

        service.setHidden(first, true)
        assertFalse(service.dockFull)
        service.toggleDocked(service.grid.single())
        service.setHidden(first, false)

        assertEquals(6, service.dock.size)
        assertTrue(service.dockFull)
        val visible = service.applications.filter { !it.hidden }
        assertEquals(packages(visible).toSet(), (packages(service.dock) + packages(service.grid)).toSet())
        assertEquals(listOf(first), service.grid)
    }

    @Test
    fun `Docking adds to the end of the dock, undocking returns the app to the grid, and both are stored`() = runTest {
        val service = service(listOf(app("a"), app("b"), app("c")), mapOf("dock_apps" to listOf("c")))

        service.toggleDocked(service.grid.first())
        assertEquals(listOf("c", "a"), packages(service.dock))
        assertEquals(listOf("c", "a"), stored("dock_apps"))

        service.toggleDocked(service.dock.first())
        assertEquals(listOf("a"), packages(service.dock))
        assertEquals(listOf("b", "c"), packages(service.grid))
        assertEquals(listOf("a"), stored("dock_apps"))
    }

    @Test
    fun `A hidden app stays listed, leaves the dock and the grid, and is stored`() = runTest {
        val service = service(listOf(app("a"), app("b")), mapOf("dock_apps" to listOf("a")))

        service.setHidden(service.dock.single(), true)

        assertTrue(service.applications.first().hidden)
        assertTrue(service.dock.isEmpty())
        assertEquals(listOf("b"), packages(service.grid))
        assertEquals(listOf("a"), stored("hidden_apps"))
        assertEquals(emptyList<String>(), stored("dock_apps"))
    }

    @Test
    fun `A stored hidden app stays hidden when it is updated`() = runTest {
        val service = service(listOf(app("a"), app("b")), mapOf("hidden_apps" to listOf("a")))
        assertEquals(listOf("b"), packages(service.grid))

        channel.sendEvent(mapOf("action" to "PACKAGE_CHANGED", "packageName" to "a", "app" to app("a")))

        assertTrue(service.applications.first().hidden)
        assertEquals(listOf("b"), packages(service.grid))
    }

    @Test
    fun `Moving a dock tile reorders the dock, and saveOrder stores it`() = runTest {
        val service = service(listOf(app("a"), app("b"), app("c")), mapOf("dock_apps" to listOf("a", "b", "c")))

        service.move(service.dock.first(), 1)

        assertEquals(listOf("b", "a", "c"), packages(service.dock))
        assertEquals(listOf("a", "b", "c"), stored("dock_apps"))
        service.saveOrder()
        assertEquals(listOf("b", "a", "c"), stored("dock_apps"))
    }

    @Test
    fun `The first grid move starts from A to Z, and apps installed later join the end`() = runTest {
        val service = service(listOf(app("c"), app("a"), app("b")))

        service.move(service.grid.last(), 0)
        service.saveOrder()
        channel.sendEvent(mapOf("action" to "PACKAGE_CHANGED", "packageName" to "aa", "app" to app("aa")))

        assertEquals(listOf("c", "a", "b"), stored("grid_order"))
        assertEquals(listOf("c", "a", "b", "aa"), packages(service.grid))
    }

    @Test
    fun `A removed app leaves the list and the dock`() = runTest {
        val service = service(listOf(app("a"), app("b")), mapOf("dock_apps" to listOf("a")))

        channel.sendEvent(mapOf("action" to "PACKAGE_REMOVED", "packageName" to "a"))

        assertEquals(listOf("b"), packages(service.applications))
        assertTrue(service.dock.isEmpty())
        assertFalse(service.dockFull)
    }

    @Test
    fun `A banner-less app's banner is fetched from the channel once, and again after an update`() = runTest {
        val service = service(listOf(app("a")))

        service.getAppBanner("a")
        service.getAppBanner("a")
        assertEquals(1, channel.bannerReads)

        channel.sendEvent(mapOf("action" to "PACKAGE_CHANGED", "packageName" to "a", "app" to app("a")))
        service.getAppBanner("a")
        assertEquals(2, channel.bannerReads)
    }

    @Test
    fun `Opening, app info, uninstall, settings and inputs go to the channel`() = runTest {
        val service = service(listOf(app("a")))
        val app = service.applications.single()

        service.launchApp(app)
        service.openAppInfo(app)
        service.uninstallApp(app)
        service.openSettings()
        service.openInputPicker()

        assertEquals(
            listOf("launchApp a", "openAppInfo a", "uninstallApp a", "openSettings", "openInputPicker"),
            channel.calls,
        )
    }

    @Test
    fun `The + picker lists the grid's apps A to Z ignoring case`() = runTest {
        val service = service(
            listOf(app("z", "Zoom"), app("d", "distributor"), app("a", "Arc"), app("h", "Hidden"), app("f", "Fav")),
            mapOf("hidden_apps" to listOf("h"), "dock_apps" to listOf("f")),
        )

        assertEquals(listOf("Arc", "distributor", "Zoom"), service.favoriteChoices.map { it.name })
    }
}
