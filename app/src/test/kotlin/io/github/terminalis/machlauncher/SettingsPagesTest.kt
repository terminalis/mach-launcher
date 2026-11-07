package io.github.terminalis.machlauncher

import kotlin.random.Random
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val NETFLIX = "com.netflix.ninja"
private const val APPLE = "com.apple.atve.androidtv.appletv"

private fun app(packageName: String, name: String = packageName) =
    mapOf("packageName" to packageName, "name" to name, "version" to "1.2.3")

private fun program(packageName: String, name: String, id: Long) = mapOf<String, Any?>(
    "id" to id, "packageName" to packageName, "appName" to name, "title" to "Title $id", "description" to "",
    "posterUri" to "content://posters/$id", "intentUri" to "intent://$id", "progressPercent" to 0,
)

private class FakeNavigator : PanelNavigator {
    val pushed = ArrayList<PanelPage>()
    var pops = 0
    var aboutShown = false
    var homeChoiceShown = false
    var onPicked: ((String) -> Unit)? = null

    override fun push(page: PanelPage) {
        pushed.add(page)
    }

    override fun pop() {
        pops++
    }

    override fun showAbout() {
        aboutShown = true
    }

    override fun pickPhoto(onPicked: (String) -> Unit) {
        this.onPicked = onPicked
    }

    override fun showHomeAppChoice() {
        homeChoiceShown = true
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsPagesTest {
    private val channel = FakeChannel().apply {
        applications = listOf(app("a", "Alpha"), app("b", "Beta"), app("c", "Gamma"))
        rows = listOf(program(NETFLIX, "Netflix", 1), program(APPLE, "Apple TV", 2))
    }
    private val preferences = FakePreferences()
    private val settings = SettingsService(preferences)
    private val navigator = FakeNavigator()
    private lateinit var apps: AppsService
    private lateinit var recommendations: RecommendationsService
    private val pictures = ArrayList<String>()

    private fun TestScope.pages(): SettingsPages {
        apps = AppsService(channel, preferences, backgroundScope)
        recommendations = RecommendationsService(channel, settings, backgroundScope, { 0L }, Random(7), StandardTestDispatcher(testScheduler))
        runCurrent()
        return SettingsPages(apps, recommendations, settings, navigator) { pictures.add(it) }
    }

    private fun labels(page: PanelPage) = page.rows().map { it.label }

    private fun PanelPage.row(label: String) = rows().first { it.label == label }

    @Test
    fun `first page lists the entries in the design's order`() = runTest {
        val page = pages().first()

        assertEquals("Interface", page.title())
        assertEquals(listOf("Applications", "Recommendations", "Background", "Home screen", "About Mach Launcher"), labels(page))
        assertTrue(page.rows().last().dividerBefore)
        assertEquals(RowTrailing.None, page.rows().last().trailing)
    }

    @Test
    fun `About opens its dialog`() = runTest {
        pages().first().row("About Mach Launcher").onPress()

        assertTrue(navigator.aboutShown)
    }

    @Test
    fun `All Applications tab displays unhidden apps`() = runTest {
        val pages = pages()
        apps.setHidden(apps.applications.first(), true)

        val page = pages.applications()

        assertEquals("All Apps", page.title())
        assertEquals(listOf("Beta", "Gamma"), labels(page))
    }

    @Test
    fun `Favorite and Hidden tabs list their own apps`() = runTest {
        val pages = pages()
        apps.toggleDocked(apps.applications[1])
        apps.setHidden(apps.applications[0], true)
        val page = pages.applications()

        page.tabs!!.step(1)
        assertEquals("Favorite Apps", page.title())
        assertEquals(listOf("Beta"), labels(page))

        page.tabs!!.step(1)
        assertEquals("Hidden Apps", page.title())
        assertEquals(listOf("Alpha"), labels(page))
    }

    @Test
    fun `The tab strip stops at its ends`() = runTest {
        val tabs = pages().applications().tabs!!

        tabs.step(-1)
        assertEquals(0, tabs.selected)
        tabs.step(1)
        tabs.step(1)
        tabs.step(1)
        assertEquals(2, tabs.selected)
    }

    @Test
    fun `Selecting an application opens AppDetailsPage`() = runTest {
        pages().applications().row("Alpha").onPress()

        assertEquals("Alpha", navigator.pushed.single().title())
        assertEquals("a", navigator.pushed.single().appHeader!!.packageName)
    }

    @Test
    fun `Choosing an entry on an app's page closes the page`() = runTest {
        val pages = pages()
        val page = pages.appDetails(apps.applications.first())

        page.row("Hide").onPress()

        assertEquals(1, navigator.pops)
        assertTrue(apps.applications.first().hidden)
    }

    @Test
    fun `The app panel and app details open with no row focused`() = runTest {
        val pages = pages()
        val app = apps.applications.first()

        assertTrue(pages.appPanel(app) {}.opensUnfocused)
        assertTrue(pages.appDetails(app).opensUnfocused)
    }

    @Test
    fun `shows the header and the entries in order`() = runTest {
        val pages = pages()
        val app = apps.applications.first()
        val page = pages.appPanel(app) {}

        assertEquals(app, page.appHeader)
        assertTrue(page.showsPackage)
        assertEquals(listOf("Move", "Open", "Add to Fav", "Hide", "Application info", "Uninstall"), labels(page))
    }

    @Test
    fun `An app's settings page has every entry but Move`() = runTest {
        val pages = pages()

        assertEquals(listOf("Open", "Add to Fav", "Hide", "Application info", "Uninstall"), labels(pages.appDetails(apps.applications.first())))
    }

    @Test
    fun `Add to Fav docks the app`() = runTest {
        val pages = pages()
        val app = apps.applications.first()

        pages.appPanel(app) {}.row("Add to Fav").onPress()

        assertTrue(apps.isDocked(app))
        assertEquals(1, navigator.pops)
    }

    @Test
    fun `Hide hides the app`() = runTest {
        val pages = pages()
        val app = apps.applications.first()

        pages.appPanel(app) {}.row("Hide").onPress()

        assertTrue(app.hidden)
    }

    @Test
    fun `hides Add to Fav when the dock is full`() = runTest {
        channel.applications = (0 until 7).map { app("app.$it") }
        val pages = pages()
        for (app in apps.applications.take(6)) apps.toggleDocked(app)

        assertFalse("Add to Fav" in labels(pages.appPanel(apps.grid.single()) {}))
        assertTrue("Remove from Fav" in labels(pages.appPanel(apps.dock.first()) {}))
    }

    @Test
    fun `Move runs and closes the panel`() = runTest {
        val pages = pages()
        var moved = false

        pages.appPanel(apps.applications.first()) { moved = true }.row("Move").onPress()

        assertTrue(moved)
        assertEquals(1, navigator.pops)
    }

    @Test
    fun `Open, App info and Uninstall go to the channel`() = runTest {
        val pages = pages()
        val page = pages.appPanel(apps.applications.first()) {}

        page.row("Open").onPress()
        page.row("Application info").onPress()
        page.row("Uninstall").onPress()

        assertEquals(listOf("launchApp a", "openAppInfo a", "uninstallApp a"), channel.calls)
    }

    @Test
    fun `The + picker lists the grid's apps and hands back the choice`() = runTest {
        val pages = pages()
        apps.setHidden(apps.applications.first(), true)
        val chosen = ArrayList<String>()

        val page = pages.favoritePicker { chosen.add(it.name) }
        assertEquals("Add to Fav", page.title())
        assertEquals(listOf("Beta", "Gamma"), labels(page))
        page.row("Gamma").onPress()

        assertEquals(listOf("Gamma"), chosen)
        assertEquals(RowTrailing.None, page.rows().first().trailing)
    }

    @Test
    fun `Show as comes first, then Number of titles, Play Next and the apps in the order given`() = runTest {
        assertEquals(listOf("Show as", "Number of titles", "Play Next", "Apple TV", "Netflix"), labels(pages().recommendations()))
    }

    @Test
    fun `Show as names the current choice`() = runTest {
        val pages = pages()
        settings.setRecommendationsDisplay(RecommendationsDisplay.row)

        assertEquals(RowTrailing.Label("Row"), pages.recommendations().row("Show as").trailing)
    }

    @Test
    fun `a source that is off switches on`() = runTest {
        val pages = pages()

        pages.recommendations().row("Netflix").onPress()

        assertEquals(listOf(NETFLIX), settings.recommendationSources)
    }

    @Test
    fun `Play Next switches on`() = runTest {
        val pages = pages()

        pages.recommendations().row("Play Next").onPress()

        assertEquals(listOf(PLAY_NEXT_SOURCE), settings.recommendationSources)
    }

    @Test
    fun `a source that is on switches off`() = runTest {
        settings.setRecommendationSourceSelected(NETFLIX, true)
        val pages = pages()
        val page = pages.recommendations()
        assertEquals(RowTrailing.Toggle(true), page.row("Netflix").trailing)

        page.row("Netflix").onPress()

        assertTrue(settings.recommendationSources.isEmpty())
    }

    @Test
    fun `asks for TV-listings access when missing`() = runTest {
        channel.permitted = false
        val page = pages().recommendations()

        assertEquals("Grant permission", page.rows().first().label)
        assertTrue(page.rows().first().focusFirst)
        assertTrue(page.row("Show as").dividerBefore)
    }

    @Test
    fun `Number of titles offers 3 to 9 and closes after a choice`() = runTest {
        val pages = pages()
        pages.recommendations().row("Number of titles").onPress()
        val page = navigator.pushed.single()

        assertEquals((3..9).map { "$it" }, labels(page))
        assertEquals(RowTrailing.Check, page.row("3").trailing)
        assertTrue(page.row("3").focusFirst)
        page.row("7").onPress()

        assertEquals(7, settings.recommendationTitleCount)
        assertEquals(1, navigator.pops)
    }

    @Test
    fun `Show as offers Off, Row and Background and closes after a choice`() = runTest {
        val pages = pages()
        pages.recommendations().row("Show as").onPress()
        val page = navigator.pushed.single()

        assertEquals(listOf("Off", "Row", "Background"), labels(page))
        page.row("Background").onPress()

        assertEquals(RecommendationsDisplay.background, settings.recommendationsDisplay)
        assertEquals(1, navigator.pops)
    }

    @Test
    fun `lists the six backgrounds in order with the current choice ticked`() = runTest {
        val page = pages().background()

        assertEquals(listOf("Dark gradient", "Light gradient", "Clouds by night", "Clouds by day", "Clouds, auto", "Custom image"), labels(page))
        assertEquals(RowTrailing.Check, page.row("Clouds by day").trailing)
        assertEquals(RowTrailing.None, page.row("Dark gradient").trailing)
    }

    @Test
    fun `Clouds, auto shows Light from and Dark from with their times`() = runTest {
        settings.setBackgroundChoice(BackgroundChoice.cloudsAuto)
        val page = pages().background()

        assertEquals(RowTrailing.Label("07:00"), page.row("Light from").trailing)
        assertEquals(RowTrailing.Label("19:00"), page.row("Dark from").trailing)
        assertTrue(page.row("Light from").indented)
        assertEquals("Custom image", page.rows().last().label)
    }

    @Test
    fun `Light from offers every half hour and closes after a choice`() = runTest {
        settings.setBackgroundChoice(BackgroundChoice.cloudsAuto)
        val pages = pages()
        pages.background().row("Light from").onPress()
        val page = navigator.pushed.single()

        assertEquals(48, labels(page).size)
        assertEquals("00:00", labels(page).first())
        assertEquals("23:30", labels(page).last())
        page.row("06:30").onPress()

        assertEquals(6 * 60 + 30, settings.lightFromMinutes)
        assertEquals(1, navigator.pops)
    }

    @Test
    fun `choosing a background sets it at once and keeps the page open`() = runTest {
        val pages = pages()
        val page = pages.background()

        for ((name, choice) in listOf(
            "Dark gradient" to BackgroundChoice.dark,
            "Light gradient" to BackgroundChoice.light,
            "Clouds by night" to BackgroundChoice.cloudsNight,
            "Clouds by day" to BackgroundChoice.cloudsDay,
        )) {
            page.row(name).onPress()
            assertEquals(choice, settings.backgroundChoice)
        }
        assertEquals(0, navigator.pops)
    }

    @Test
    fun `Custom image goes through the picker, then stores the chosen path`() = runTest {
        val pages = pages()

        pages.background().row("Custom image").onPress()
        navigator.onPicked!!("/photos/1.jpg")

        assertEquals(listOf("/photos/1.jpg"), pictures)
    }

    @Test
    fun `Reset Home screen hands Home back to the TV's own launcher`() = runTest {
        pages().homeScreen().row("Reset Home screen").onPress()

        assertFalse(navigator.homeChoiceShown)
    }

    @Test
    fun `Where Mach is still Home, Reset Home screen opens Android's Home app page`() = runTest {
        channel.resetResult = false
        pages().homeScreen().row("Reset Home screen").onPress()

        assertTrue(navigator.homeChoiceShown)
    }

    @Test
    fun `The Home screen page carries its five paragraphs`() = runTest {
        assertEquals(5, pages().homeScreen().paragraphs.size)
    }
}
