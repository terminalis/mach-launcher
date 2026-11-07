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

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.SoundEffectConstants
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TILE_MARGIN = 72f
private const val PLUS_GLYPH = ""
private val OK_KEYS = setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BUTTON_A)

private enum class RecommendationsMode { none, banner, row, hero }

class HomeScreen(private val activity: MainActivity) : FrameLayout(activity) {
    private val d = resources.displayMetrics.density
    private val screenWidth = resources.displayMetrics.widthPixels
    private val screenHeight = resources.displayMetrics.heightPixels
    private val home = HomeGeometry(screenWidth / d, screenHeight / d)
    private val margin = px(TILE_MARGIN)
    private val tileWidth = px(home.tileWidth)
    private val tileHeight = px(home.tileHeight)

    private val regular = Typeface.createFromAsset(activity.assets, "fonts/Inter-Regular.ttf")
    private val bold = Typeface.createFromAsset(activity.assets, "fonts/Inter-Bold.ttf")
    private val extraBold = Typeface.createFromAsset(activity.assets, "fonts/Inter-ExtraBold.ttf")
    private val medium = Typeface.createFromAsset(activity.assets, "fonts/Inter-Medium.ttf")
    private val semiBold = Typeface.createFromAsset(activity.assets, "fonts/Inter-SemiBold.ttf")
    private val icons = Typeface.createFromAsset(activity.assets, "CupertinoIcons.ttf")
    private val fonts = PanelFonts(regular, medium, semiBold, bold, icons)

    private val scope = MainScope()
    private val bakeExecutor = Executors.newSingleThreadExecutor()
    private val bakeDispatcher = bakeExecutor.asCoroutineDispatcher()
    private val baker = GlassBaker(screenWidth, screenHeight, d)

    private val preferences = activity.getSharedPreferences("mach", Context.MODE_PRIVATE)
    private val channel = ActivityChannel(activity)
    private val settings = SettingsService(preferences)
    private val apps = AppsService(channel, preferences, scope)
    private val state = LauncherState()
    private val wallpapers = WallpaperService(settings, File(activity.filesDir, "wallpaper"), scope)
    private val recommendations = RecommendationsService(channel, settings, scope)
    private val heroBaker = HeroBaker<HeroTextures>(scope, bakeDispatcher, { baker.bakeHero(baker.decode(it)) }, ::onHeroTextures)

    private val updater = UpdateService(channel, activity.filesDir)
    private val panels: PanelHost = PanelHost(
        activity, d, fonts, scope, bakeDispatcher, channel, apps, updater,
        activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: "",
        PackageInfoCompat.getLongVersionCode(activity.packageManager.getPackageInfo(activity.packageName, 0)),
        ::launcherIcon, ::click, ::setCovered, recommendations::applySettings,
    )
    private val pages: SettingsPages = SettingsPages(apps, recommendations, settings, panels) { path ->
        scope.launch { wallpapers.setPicture(path) }
    }

    private val background = HomeBackgroundView(activity)
    private val content = FrameLayout(activity).apply {
        clipChildren = false
        clipToPadding = false
    }
    private val heroArea = FrameLayout(activity).apply {
        clipChildren = false
        clipToPadding = false
        visibility = View.GONE
    }
    private val hero = HeroBlock(activity, d, regular, bold, extraBold, icons)
    private val banner = ListingsBanner(activity, d, fonts)
    private val bannerArea = FrameLayout(activity).apply { visibility = View.GONE }
    private val rowClip = FrameLayout(activity).apply {
        clipChildren = true
        visibility = View.GONE
    }
    private val rowContent = FrameLayout(activity).apply {
        clipChildren = false
        clipToPadding = false
    }
    private val dockGlass = GlassPanel(activity, d, screenWidth, screenHeight)
    private val tiles = FrameLayout(activity).apply {
        clipChildren = false
        clipToPadding = false
    }
    private val statusBar = StatusBarView(activity, d, screenWidth, screenHeight, regular, bold, icons)
    private val loading = loadingView()
    private val plusGlyph = bakeGlyph(PLUS_GLYPH, 32 * d * 1.15f, icons, Color.WHITE, HOME_TEXT_SHADOWS.first(), d)

    private var dockApps: List<App> = emptyList()
    private var gridApps: List<App> = emptyList()
    private var dockFrames: List<ZoomFrame> = emptyList()
    private var gridFrames: List<TileFrame> = emptyList()
    private val tileImages = HashMap<App, Bitmap>()
    private var cards: List<CardFrame> = emptyList()
    private var rowKeys: List<String> = emptyList()
    private var rowJob: Job? = null
    private var mode = RecommendationsMode.none
    private var keySeen = false

    private var wallpaperKey: Any? = null
    private var wallpaperGlass: Bitmap? = null
    private var wallpaperBlur: Bitmap? = null
    private var heroTextures: HeroTextures? = null
    private var windowCleared = false

    private var focus: FocusTarget? = null
    private var scrollPx = 0f
    private var scrollTarget = 0f
    private var toolbarPx = px(TOOLBAR_HEIGHT).toFloat()
    private var rowPx = 0f
    private val animators = arrayOfNulls<ValueAnimator>(4)
    private val okPress = OkPress(System::currentTimeMillis, ::openFocused, ::openAppPanel)
    private var moving: App? = null

    init {
        clipChildren = false
        clipToPadding = false
        val full = LayoutParams.MATCH_PARENT
        addView(background, LayoutParams(full, full))
        addView(content, LayoutParams(full, px(home.contentHeight(0))))
        content.addView(heroArea, LayoutParams(full, px(home.recommendationsBottom)))
        heroArea.addView(hero, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START).apply {
            leftMargin = px(LAUNCHER_SECTION_HORIZONTAL_PADDING + APP_CARD_HORIZONTAL_PADDING)
        })
        content.addView(bannerArea, LayoutParams(full, px(home.recommendationsBottom)))
        bannerArea.addView(banner, LayoutParams(full, full).apply {
            leftMargin = px(LAUNCHER_SECTION_HORIZONTAL_PADDING + BANNER_MARGIN)
            rightMargin = px(LAUNCHER_SECTION_HORIZONTAL_PADDING + BANNER_MARGIN)
            topMargin = px(TOOLBAR_HEIGHT + BANNER_MARGIN)
            bottomMargin = px(BANNER_MARGIN)
        })
        content.addView(rowClip, LayoutParams(full, px(CARD_HEIGHT) + 2 * margin).apply { topMargin = px(home.cardTop) - margin })
        rowClip.addView(rowContent, LayoutParams(full, full))
        content.addView(dockGlass, LayoutParams(px(home.dockPanelWidth), px(home.dockPanelHeight)).apply {
            leftMargin = px(home.dockPanelLeft)
            topMargin = px(home.dockPanelTop)
        })
        content.addView(tiles, LayoutParams(full, full))
        addView(statusBar, LayoutParams(full, px(TOOLBAR_HEIGHT)))
        addView(loading, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        addView(panels, LayoutParams(full, full))

        apps.addListener {
            syncApps()
            panels.refresh()
        }
        wallpapers.addListener(::syncBackground)
        recommendations.addListener {
            syncRecommendations()
            panels.refresh()
        }
        settings.addListener {
            syncRecommendations()
            panels.refresh()
        }
        state.addListener(::syncArea)
        syncBackground()
        syncApps()
        syncRecommendations()
        scope.launch {
            while (true) {
                delay(millisToNextMinute(System.currentTimeMillis()))
                statusBar.tick(System.currentTimeMillis())
            }
        }
    }

    private fun px(dp: Float) = (dp * d).roundToInt()

    private fun launcherIcon(): Bitmap {
        val size = px(72f)
        val square = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        activity.getDrawable(R.mipmap.ic_launcher)!!.apply {
            setBounds(0, 0, size, size)
            draw(Canvas(square))
        }
        return roundedCover(square, size, size, 12 * d)
    }

    private fun loadingView() = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        addView(ProgressBar(activity).apply { indeterminateTintList = ColorStateList.valueOf(Color.WHITE) })
        addView(TextView(activity).apply {
            text = AppStrings.loading
            typeface = regular
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, 22 * d)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = px(16f) })
    }

    private fun syncBackground() {
        val key: Any = wallpapers.wallpaper ?: wallpapers.gradient
        if (key == wallpaperKey) return
        wallpaperKey = key
        scope.launch {
            val (cover, glass, blur) = withContext(bakeDispatcher) {
                val source = when (key) {
                    is BundledWallpaper -> activity.assets.open(bundledVariant(key.asset, d)).use { BitmapFactory.decodeStream(it) }
                    is PictureWallpaper -> baker.decode(key.bytes)
                    else -> baker.gradient(key as BackgroundGradient)
                }
                val cover = baker.screenCover(source).also { it.prepareToDraw() }
                Triple(cover, baker.glassFrom(cover), baker.gridFrom(cover))
            }
            if (key != wallpaperKey) return@launch
            background.showWallpaper(cover)
            wallpaperGlass = glass
            wallpaperBlur = blur
            if (heroTextures == null) applyGlass(glass, blur)
            clearWindowBackground()
        }
    }

    private fun clearWindowBackground() {
        if (windowCleared) return
        windowCleared = true
        post { activity.window.setBackgroundDrawable(null) }
    }

    private fun applyGlass(glass: Bitmap, blur: Bitmap) {
        dockGlass.switcher.switchTo(glass)
        statusBar.clockPanel.switcher.switchTo(glass)
        statusBar.buttonsPanel.switcher.switchTo(glass)
        background.setBlur(blur)
    }

    private fun onHeroTextures(textures: HeroTextures?) {
        heroTextures = textures
        background.showHero(textures?.hero)
        if (textures != null) {
            applyGlass(textures.dockBlur, textures.gridBlur)
        } else {
            val glass = wallpaperGlass
            val blur = wallpaperBlur
            if (glass != null && blur != null) applyGlass(glass, blur)
        }
    }

    private fun syncApps() {
        if (!apps.initialized) return
        loading.visibility = View.GONE
        val dock = apps.dock
        val grid = apps.grid
        if (dock == dockApps && grid == gridApps && dockFrames.isNotEmpty()) return
        dockApps = dock
        gridApps = grid
        tileImages.keys.retainAll((dock + grid).toSet())
        tiles.removeAllViews()
        content.layoutParams = content.layoutParams.apply { height = px(home.contentHeight(grid.size)) }

        val frames = ArrayList<ZoomFrame>()
        dock.forEachIndexed { i, app ->
            frames.add(addTile(app, home.tileLeft(i), home.dockTileTop))
        }
        if (dock.size < HOME_COLUMNS) {
            val plus = PlusContent(activity, d, plusGlyph)
            frames.add(place(PlusFrame(activity, d, margin, plus, tileWidth, tileHeight), home.tileLeft(dock.size), home.dockTileTop))
        }
        dockFrames = frames
        gridFrames = grid.mapIndexed { i, app ->
            addTile(app, home.tileLeft(i % HOME_COLUMNS), home.gridTileTop(i)).also { addLabel(app, it, i) }
        }
        val requested = dock.indexOfFirst { state.consumeFocusRequest(it.packageName) }
        val current = if (requested >= 0) FocusTarget.Dock(requested) else focus
        focus = null
        setFocus(settled(current))
        moving?.let { app -> tileOf(app)?.showMoving(MoveArrowsView(activity, d, icons, apps.isDocked(app))) }
    }

    private fun tileOf(app: App): TileFrame? {
        val docked = dockApps.indexOf(app)
        return if (docked >= 0) dockFrames[docked] as TileFrame else gridFrames.getOrNull(gridApps.indexOf(app))
    }

    private fun settled(target: FocusTarget?): FocusTarget = when (target) {
        null -> FocusTarget.Dock(0)
        is FocusTarget.Dock -> FocusTarget.Dock(target.index.coerceIn(0, dockFrames.size - 1))
        is FocusTarget.Grid -> if (gridFrames.isEmpty()) FocusTarget.Dock(0) else FocusTarget.Grid(target.index.coerceAtMost(gridFrames.size - 1))
        else -> target
    }

    private fun <T : View> place(frame: T, x: Float, y: Float): T {
        tiles.addView(frame, LayoutParams(tileWidth + 2 * margin, tileHeight + 2 * margin).apply {
            leftMargin = px(x) - margin
            topMargin = px(y) - margin
        })
        return frame
    }

    private fun addTile(app: App, x: Float, y: Float): TileFrame {
        val tile = TileContent(activity, d)
        val frame = place(TileFrame(activity, d, margin, tile, tileWidth, tileHeight), x, y)
        val image = tileImages[app]
        if (image != null) {
            tile.image = image
        } else {
            scope.launch {
                val banner = apps.getAppBanner(app.packageName)
                val icon = if (banner.isEmpty()) apps.getAppIcon(app.packageName) else banner
                val baked = withContext(bakeDispatcher) {
                    (if (banner.isEmpty()) null else bakeTile(banner, tileWidth, tileHeight, d))
                        ?: bakeFallbackTile(icon, app.name, tileWidth, tileHeight, d, regular)
                }
                tileImages[app] = baked
                tile.image = baked
            }
        }
        return frame
    }

    private fun addLabel(app: App, frame: TileFrame, index: Int) {
        scope.launch {
            val bitmap = withContext(bakeDispatcher) { bakeLabel(app.name, tileWidth, d, regular) }
            if (frame.parent == null) return@launch
            val label = TileLabelView(activity, bitmap)
            tiles.addView(label, LayoutParams(tileWidth + 2 * margin, bitmap.height).apply {
                leftMargin = px(home.tileLeft(index % HOME_COLUMNS)) - margin
                topMargin = px(home.gridLabelTop(index)) - (bitmap.height - px(16f)) / 2
            })
            frame.label = label
            if (focus == FocusTarget.Grid(index)) label.alpha = 1f
        }
    }

    private fun syncRecommendations() {
        val picks = recommendations.picks
        val display = settings.recommendationsDisplay
        mode = when {
            display == RecommendationsDisplay.off -> RecommendationsMode.none
            picks.isEmpty() -> if (recommendations.showsPermissionBanner) RecommendationsMode.banner else RecommendationsMode.none
            display == RecommendationsDisplay.row -> RecommendationsMode.row
            else -> RecommendationsMode.hero
        }
        if (mode == RecommendationsMode.hero) {
            hero.show(recommendations.activeItem, recommendations.activeIndex, picks.size)
            heroBaker.update(recommendations.activePoster, picks.map { recommendations.posterOf(it) })
        } else {
            heroBaker.update(null, emptyList())
        }
        heroArea.visibility = if (mode == RecommendationsMode.hero) View.VISIBLE else View.GONE
        bannerArea.visibility = if (mode == RecommendationsMode.banner) View.VISIBLE else View.GONE
        rowClip.visibility = if (mode == RecommendationsMode.row) View.VISIBLE else View.GONE
        val keys = picks.map { "${it.source}|${it.id}" }
        if (mode == RecommendationsMode.row && keys != rowKeys) {
            rowKeys = keys
            buildRow(picks)
        }
        if (mode == RecommendationsMode.banner && !keySeen) setFocus(FocusTarget.Banner(0))
        val current = focus
        val stale = (current == FocusTarget.Watch && mode != RecommendationsMode.hero) ||
            (current is FocusTarget.Banner && mode != RecommendationsMode.banner) ||
            (current is FocusTarget.Card && (mode != RecommendationsMode.row || current.index >= cards.size))
        if (stale) {
            setFocus(
                when {
                    current is FocusTarget.Banner && mode == RecommendationsMode.hero -> FocusTarget.Watch
                    current is FocusTarget.Banner && mode == RecommendationsMode.row -> FocusTarget.Card(0)
                    else -> FocusTarget.Dock(0)
                },
            )
        }
    }

    private fun buildRow(picks: List<Recommendation>) {
        rowJob?.cancel()
        rowContent.removeAllViews()
        rowPx = 0f
        rowContent.translationX = 0f
        val width = px(CARD_WIDTH)
        val height = px(CARD_HEIGHT)
        rowContent.layoutParams = rowContent.layoutParams.apply {
            this.width = px(2 * LAUNCHER_SECTION_HORIZONTAL_PADDING + CARD_PITCH * picks.size) + 2 * margin
        }
        val frames = picks.mapIndexed { i, _ ->
            val frame = CardFrame(activity, d, margin, RowCardView(activity, d), width, height)
            rowContent.addView(frame, LayoutParams(width + 2 * margin, height + 2 * margin).apply {
                leftMargin = px(LAUNCHER_SECTION_HORIZONTAL_PADDING + APP_CARD_HORIZONTAL_PADDING + CARD_PITCH * i) - margin
            })
            frame
        }
        cards = frames
        (focus as? FocusTarget.Card)?.let { frames.getOrNull(it.index)?.setFocusState(true) }
        rowJob = scope.launch {
            picks.forEachIndexed { i, pick ->
                val poster = recommendations.posterOf(pick)
                frames[i].card.art = withContext(bakeDispatcher) { CardArt(poster, pick, width, height, d, bold, regular) }
            }
        }
    }

    private fun syncArea() {
        val inGrid = state.appGridFocused
        background.setBlurShown(inGrid)
        animate(1, toolbarPx, if (inGrid) 0f else px(TOOLBAR_HEIGHT).toFloat(), 200, DecelerateInterpolator(1f)) {
            toolbarPx = it
            placeContent()
        }
        animate(2, statusBar.alpha, if (inGrid) 0f else 1f, 200, Ease.inOut) { statusBar.alpha = it }
    }

    private fun animate(slot: Int, from: Float, to: Float, ms: Long, ease: Interpolator, onValue: (Float) -> Unit) {
        animators[slot]?.cancel()
        animators[slot] = ValueAnimator.ofFloat(from, to).apply {
            duration = ms
            interpolator = ease
            addUpdateListener { onValue(it.animatedValue as Float) }
            start()
        }
    }

    private fun placeContent() {
        content.translationY = -scrollPx - (px(TOOLBAR_HEIGHT) - toolbarPx)
        dockGlass.invalidate()
    }

    private fun scrollTo(target: Float, ms: Long, ease: Interpolator) {
        if (target == scrollTarget) return
        scrollTarget = target
        animate(0, scrollPx, target, ms, ease) {
            scrollPx = it
            placeContent()
        }
    }

    private fun viewOf(target: FocusTarget): HomeFocusable? = when (target) {
        is FocusTarget.Status -> statusBar.buttons[target.index]
        FocusTarget.Watch -> hero.watch
        is FocusTarget.Banner -> if (target.index == 0) banner.grant else banner.check
        is FocusTarget.Card -> cards.getOrNull(target.index)
        is FocusTarget.Dock -> dockFrames.getOrNull(target.index)
        is FocusTarget.Grid -> gridFrames.getOrNull(target.index)
    }

    private fun setFocus(target: FocusTarget) {
        if (target == focus) return
        focus?.let { viewOf(it)?.setFocusState(false) }
        focus = target
        viewOf(target)?.setFocusState(true)
        state.setFocusedArea(areaOf(target, state.focusedArea))
        when (target) {
            is FocusTarget.Status -> Unit
            FocusTarget.Watch, is FocusTarget.Banner, is FocusTarget.Dock -> scrollTo(0f, 260, Ease.inOutCubic)
            is FocusTarget.Card -> {
                scrollTo(0f, 260, Ease.inOutCubic)
                animate(3, rowPx, px(home.rowOffset(target.index, cards.size)).toFloat(), 200, Ease.inOut) {
                    rowPx = it
                    rowContent.translationX = -it
                }
            }
            is FocusTarget.Grid -> {
                if (target.index < HOME_COLUMNS) {
                    scrollTo(px(home.dockOffset).toFloat(), 260, Ease.inOutCubic)
                } else {
                    home.keepInView(scrollTarget / d, target.index, gridFrames.size)?.let { scrollTo(px(it).toFloat(), 220, Ease.inOut) }
                }
            }
        }
    }

    private fun centreX(target: FocusTarget): Float = when (target) {
        is FocusTarget.Status -> viewCentre(statusBar.buttons[target.index])
        FocusTarget.Watch -> viewCentre(hero.watch)
        is FocusTarget.Banner -> viewCentre(if (target.index == 0) banner.grant else banner.check)
        is FocusTarget.Card -> home.cardCentre(target.index, rowPx / d)
        is FocusTarget.Dock -> home.tileLeft(target.index) + home.tileWidth / 2
        is FocusTarget.Grid -> home.tileLeft(target.index % HOME_COLUMNS) + home.tileWidth / 2
    }

    private fun viewCentre(view: View): Float {
        val location = IntArray(2)
        view.getLocationInWindow(location)
        return (location[0] + view.width / 2f) / d
    }

    private fun map() = HomeMap(
        dockFrames.size,
        gridFrames.size,
        mode == RecommendationsMode.hero,
        if (mode == RecommendationsMode.row) cards.size else 0,
        mode == RecommendationsMode.banner,
        ::centreX,
    )

    private fun click() = playSoundEffect(SoundEffectConstants.CLICK)

    private fun openFocused() {
        val target = focus ?: return
        click()
        viewOf(target)?.press { open(target) }
    }

    private fun open(target: FocusTarget) {
        when (target) {
            is FocusTarget.Status -> when (target.index) {
                0 -> apps.openInputPicker()
                1 -> apps.openSettings()
                else -> panels.openPanel(WIDE_PANEL, pages.first())
            }
            FocusTarget.Watch -> recommendations.launch(recommendations.activeItem)
            is FocusTarget.Banner -> if (target.index == 0) recommendations.requestPermission() else recommendations.refresh()
            is FocusTarget.Card -> recommendations.launch(recommendations.picks[target.index])
            is FocusTarget.Dock -> {
                val app = dockApps.getOrNull(target.index)
                if (app != null) apps.launchApp(app) else panels.openPanel(WIDE_PANEL, pages.favoritePicker(::addToDock))
            }
            is FocusTarget.Grid -> apps.launchApp(gridApps[target.index])
        }
    }

    private fun addToDock(app: App) {
        state.requestFocus(app.packageName)
        apps.toggleDocked(app)
        panels.pop()
    }

    private fun openAppPanel() {
        val app = when (val target = focus) {
            is FocusTarget.Dock -> dockApps.getOrNull(target.index)
            is FocusTarget.Grid -> gridApps.getOrNull(target.index)
            else -> null
        } ?: return
        panels.openPanel(NARROW_PANEL, pages.appPanel(app) { startMoving(app) })
    }

    private fun setCovered(on: Boolean) {
        val state = if (on) View.INVISIBLE else View.VISIBLE
        background.visibility = state
        content.visibility = state
        statusBar.visibility = state
    }

    private fun startMoving(app: App) {
        moving = app
        tileOf(app)?.showMoving(MoveArrowsView(activity, d, icons, apps.isDocked(app)))
    }

    private fun stopMoving() {
        moving?.let { tileOf(it)?.showMoving(null) }
        moving = null
        apps.saveOrder()
    }

    private fun moveTile(app: App, direction: Direction) {
        val inDock = apps.isDocked(app)
        val list = if (inDock) apps.dock else apps.grid
        val next = moveTarget(list.indexOf(app), direction, list.size, inDock) ?: return
        focus?.let { viewOf(it)?.setFocusState(false) }
        focus = if (inDock) FocusTarget.Dock(next) else FocusTarget.Grid(next)
        apps.move(app, next)
        if (!inDock) home.keepInView(scrollTarget / d, next, gridFrames.size)?.let { scrollTo(px(it).toFloat(), 220, Ease.inOut) }
    }

    fun handleKey(event: KeyEvent): Boolean {
        val direction = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> Direction.Left
            KeyEvent.KEYCODE_DPAD_RIGHT -> Direction.Right
            KeyEvent.KEYCODE_DPAD_UP -> Direction.Up
            KeyEvent.KEYCODE_DPAD_DOWN -> Direction.Down
            else -> null
        }
        val ok = event.keyCode in OK_KEYS
        val back = event.keyCode == KeyEvent.KEYCODE_BACK
        if (direction == null && !ok && !back) return false
        if (!keySeen) {
            keySeen = true
            banner.showFocus()
        }
        if (panels.active) return panels.handleKey(event, direction)
        val app = moving
        if (app != null) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && (ok || back)) {
                stopMoving()
            } else if (event.action == KeyEvent.ACTION_DOWN && direction != null) {
                click()
                moveTile(app, direction)
            }
            return true
        }
        if (back) return false
        if (ok) {
            if (event.action == KeyEvent.ACTION_DOWN) okPress.down() else okPress.up()
            return true
        }
        if (event.action != KeyEvent.ACTION_DOWN) return true
        val current = focus ?: return true
        click()
        if (current == FocusTarget.Watch && (direction == Direction.Left || direction == Direction.Right)) {
            recommendations.step(if (direction == Direction.Right) 1 else -1)
            return true
        }
        map().next(current, direction!!)?.let(::setFocus)
        return true
    }

    fun onBackToFront() {
        panels.onResumed()
        recommendations.refresh()
        statusBar.tick(System.currentTimeMillis())
    }

    fun dispose() {
        recommendations.dispose()
        wallpapers.dispose()
        activity.appChanges.stop()
        scope.cancel()
        bakeExecutor.shutdown()
    }
}
