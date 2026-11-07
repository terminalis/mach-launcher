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
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextPaint
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

const val WIDE_PANEL = 380f
const val NARROW_PANEL = 300f
private const val DIALOG_FILL = 0xFF1E1E1E.toInt()
private const val PHOTO_COLUMNS = 5
private const val THUMB_WIDTH = 480
private const val FADE_MS = 150L
private const val PHOTO_FOCUS_MS = 90L
private const val NOTES_SCROLL_MS = 120L
private const val PILL_FOCUS = -1
private const val ABOUT_WIDTH = 804f
private const val MIN_DIALOG_WIDTH = 280f
private const val PILL_FOCUSED = 0xFFE5EBEB.toInt()
private const val PILL_INK = 0xFF1E1E1E.toInt()

private interface Overlay {
    val view: View
    fun handleKey(event: KeyEvent, direction: Direction?): Boolean
    fun refresh() = Unit
}

private class DialogButton(val label: String, val onPress: () -> Unit)

private class DialogPill(
    context: Context,
    private val d: Float,
    private val glyph: String,
    private val label: String,
    fonts: PanelFonts,
    val onPress: () -> Unit,
) : View(context) {
    var focused = false
        set(value) {
            field = value
            invalidate()
        }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glyphPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = fonts.icons
        textSize = 18 * d
        color = PILL_INK
    }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = fonts.medium
        textSize = 14 * d
        letterSpacing = 1.25f / 14f
        color = PILL_INK
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(((16 + 18 + 8 + 24) * d + textPaint.measureText(label)).toInt(), (40 * d).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()
        fill.color = if (focused) PILL_FOCUSED else Color.WHITE
        canvas.drawRoundRect(0f, 0f, width.toFloat(), h, h / 2f, h / 2f, fill)
        val gm = glyphPaint.fontMetrics
        canvas.drawText(glyph, 16 * d, h / 2f - (gm.ascent + gm.descent) / 2f, glyphPaint)
        val tm = textPaint.fontMetrics
        canvas.drawText(label, (16 + 18 + 8) * d, h / 2f - (tm.ascent + tm.descent) / 2f, textPaint)
    }
}

private class DialogOverlay(
    context: Context,
    private val d: Float,
    private val fonts: PanelFonts,
    title: String?,
    content: View,
    private val buttons: List<DialogButton>,
    private val notes: ScrollView? = null,
    private val cancelable: Boolean = true,
    private val onBack: () -> Unit = {},
    private val pill: DialogPill? = null,
    minWidthDp: Float = MIN_DIALOG_WIDTH,
    maxWidthDp: Float = 880f,
) : FrameLayout(context), Overlay {
    override val view: View get() = this
    private val labels = ArrayList<TextView>()
    private var focus = if (pill != null) PILL_FOCUS else buttons.size - 1
    private var notesFocused = false
    private var notesAnimator: ValueAnimator? = null

    init {
        setBackgroundColor(0x8A000000.toInt())
        val widest = px(maxWidthDp)
        val card = object : LinearLayout(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val limit = minOf(MeasureSpec.getSize(widthMeasureSpec), widest)
                super.onMeasure(MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST), heightMeasureSpec)
            }
        }.apply {
            orientation = LinearLayout.VERTICAL
            minimumWidth = px(minWidthDp)
            setPadding(px(24f), px(if (pill != null) 16f else 24f), px(24f), px(if (pill != null) 22f else 24f))
            background = GradientDrawable().apply {
                setColor(DIALOG_FILL)
                cornerRadius = 20 * d
            }
        }
        if (title != null) {
            card.addView(TextView(context).apply {
                text = title
                typeface = fonts.regular
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, 14 * d)
                exactText()
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = px(16f) })
        }
        val contentWidth = if (minWidthDp > MIN_DIALOG_WIDTH) LinearLayout.LayoutParams.MATCH_PARENT else LinearLayout.LayoutParams.WRAP_CONTENT
        card.addView(content, LinearLayout.LayoutParams(contentWidth, LinearLayout.LayoutParams.WRAP_CONTENT))
        if (buttons.isNotEmpty()) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
            }
            for (button in buttons) {
                val label = TextView(context).apply {
                    text = button.label
                    typeface = fonts.medium
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, 14 * d)
                    letterSpacing = 1.25f / 14f
                    gravity = Gravity.CENTER
                    minimumWidth = px(64f)
                    minimumHeight = px(40f)
                    setPadding(px(16f), 0, px(16f), 0)
                }
                labels.add(label)
                row.addView(label, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(px(8f), px(4f), 0, px(4f))
                })
            }
            card.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = px(24f) })
        }
        addView(card, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply {
            setMargins(px(40f), px(24f), px(40f), px(24f))
        })
        showFocus()
    }

    private fun px(dp: Float) = (dp * d).roundToInt()

    private fun scrollNotes(scroll: ScrollView, delta: Int): Boolean {
        val limit = ((scroll.getChildAt(0)?.height ?: 0) - scroll.height).coerceAtLeast(0)
        val target = (scroll.scrollY + delta).coerceIn(0, limit)
        if (target == scroll.scrollY) return false
        notesAnimator?.cancel()
        notesAnimator = ValueAnimator.ofInt(scroll.scrollY, target).apply {
            duration = NOTES_SCROLL_MS
            interpolator = Ease.out
            addUpdateListener { scroll.scrollY = it.animatedValue as Int }
            start()
        }
        return true
    }

    private fun showFocus() {
        labels.forEachIndexed { i, label ->
            label.background = if (i == focus && !notesFocused) GradientDrawable().apply {
                setColor(0x1AFFFFFF)
                cornerRadius = 20 * d
            } else null
        }
        pill?.focused = focus == PILL_FOCUS
        notes?.background = GradientDrawable().apply {
            setColor(0x4D333535)
            cornerRadius = 10 * d
            setStroke(px(if (notesFocused) 2f else 1f), if (notesFocused) Color.WHITE else 0x403F484A)
        }
    }

    override fun handleKey(event: KeyEvent, direction: Direction?): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return true
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (cancelable && event.repeatCount == 0) onBack()
            return true
        }
        if (direction == null) {
            if (event.repeatCount == 0 && !notesFocused) {
                if (focus == PILL_FOCUS) pill?.onPress?.invoke() else buttons.getOrNull(focus)?.onPress?.invoke()
            }
            return true
        }
        val scroll = notes
        when (direction) {
            Direction.Left -> if (!notesFocused && focus > 0) focus--
            Direction.Right -> if (!notesFocused && focus != PILL_FOCUS && focus < buttons.size - 1) focus++
            Direction.Up -> if (pill != null) {
                focus = PILL_FOCUS
            } else if (scroll != null) {
                if (notesFocused) scrollNotes(scroll, -px(60f)) else notesFocused = true
            }
            Direction.Down -> if (focus == PILL_FOCUS) {
                focus = buttons.size - 1
            } else if (scroll != null && notesFocused) {
                if (!scrollNotes(scroll, px(60f))) notesFocused = false
            }
        }
        showFocus()
        return true
    }
}

private class PhotoOverlay(
    context: Context,
    private val d: Float,
    private val fonts: PanelFonts,
    private val scope: CoroutineScope,
    private val bakeDispatcher: CoroutineDispatcher,
    private val channel: AndroidChannel,
    private val onPicked: (String?) -> Unit,
) : FrameLayout(context), Overlay {
    override val view: View get() = this
    private val grid = RecyclerView(context)
    private val notice = TextView(context).apply {
        text = AppStrings.mediaPermissionRequired
        typeface = fonts.regular
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_PX, 14 * d)
        visibility = GONE
    }
    private var paths: List<String> = emptyList()
    private var focus = 0
    private var granted = true
    private val thumbnails = object : android.util.LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    private inner class Holder(card: FrameLayout, val image: ImageView, val fill: GradientDrawable) : RecyclerView.ViewHolder(card) {
        var fade: ValueAnimator? = null
    }

    private val adapter = object : RecyclerView.Adapter<Holder>() {
        override fun getItemCount() = paths.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val card = FrameLayout(context).apply { setPadding(px(4f), px(4f), px(4f), px(4f)) }
            val image = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
            card.addView(image, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            val width = cardWidth()
            card.layoutParams = RecyclerView.LayoutParams(width, width * 9 / 16).apply { setMargins(px(8f), px(8f), px(8f), px(8f)) }
            val fill = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 12 * d
            }
            card.background = fill
            return Holder(card, image, fill)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val path = paths[position]
            holder.fade?.cancel()
            holder.fill.alpha = if (position == focus) 255 else 0
            holder.image.tag = path
            val cached = thumbnails.get(path)
            holder.image.setImageBitmap(cached)
            if (cached != null) return
            scope.launch {
                val thumbnail = withContext(bakeDispatcher) { thumbnail(path) } ?: return@launch
                thumbnails.put(path, thumbnail)
                if (holder.image.tag == path) holder.image.setImageBitmap(thumbnail)
            }
        }
    }

    init {
        background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFF2A2A2F.toInt(), 0xFF161618.toInt()))
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(40f), px(24f), px(40f), px(16f))
        }
        column.addView(TextView(context).apply {
            text = AppStrings.selectImage
            typeface = fonts.semiBold
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, 20 * d)
            letterSpacing = 0.1f / 20f
            includeFontPadding = false
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(px(14f), px(13.5f), px(8f), px(11.5f))
        })
        column.addView(View(context).apply {
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0x1FFFFFFF, 0x05FFFFFF))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, px(1f)).apply { setMargins(px(8f), 0, px(8f), px(8f)) })
        val body = FrameLayout(context)
        grid.layoutManager = GridLayoutManager(context, PHOTO_COLUMNS)
        grid.adapter = adapter
        grid.itemAnimator = null
        grid.clipToPadding = false
        grid.setPadding(0, px(8f), 0, px(8f))
        body.addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
            leftMargin = -px(8f)
            rightMargin = -px(8f)
        })
        body.addView(notice, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        column.addView(body, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        column.addView(TextView(context).apply {
            text = AppStrings.mediaSelectionHint
            typeface = fonts.regular
            gravity = Gravity.CENTER
            setTextColor(0x8AFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_PX, 12 * d)
            letterSpacing = 0.25f / 12f
        })
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        load(ask = true)
    }

    private fun px(dp: Float) = (dp * d).roundToInt()

    private fun cardWidth() = (resources.displayMetrics.widthPixels - px(80f) - px(64f)) / PHOTO_COLUMNS

    private fun thumbnail(path: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = (bounds.outWidth / THUMB_WIDTH).coerceAtLeast(1)
        }
        val photo = BitmapFactory.decodeFile(path, options) ?: return null
        val width = cardWidth() - px(8f)
        return roundedCover(photo, width, (cardWidth() * 9 / 16) - px(8f), 8 * d)
    }

    private fun fadeFocus(position: Int, alpha: Int) {
        val holder = grid.findViewHolderForAdapterPosition(position) as Holder? ?: return
        holder.fade?.cancel()
        holder.fade = ValueAnimator.ofInt(holder.fill.alpha, alpha).apply {
            duration = PHOTO_FOCUS_MS
            interpolator = LinearInterpolator()
            addUpdateListener { holder.fill.alpha = it.animatedValue as Int }
            start()
        }
    }

    private fun load(ask: Boolean) {
        scope.launch {
            val allowed = channel.checkMediaPermissions()
            if (!allowed && ask) channel.requestMediaPermissions()
            paths = if (allowed) channel.getMediaStoreImages() else emptyList()
            granted = allowed
            notice.visibility = if (allowed) GONE else VISIBLE
            adapter.notifyDataSetChanged()
        }
    }

    override fun refresh() {
        if (!granted) load(ask = false)
    }

    override fun handleKey(event: KeyEvent, direction: Direction?): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return true
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.repeatCount == 0) onPicked(null)
            return true
        }
        if (direction == null) {
            if (event.repeatCount == 0) paths.getOrNull(focus)?.let { onPicked(it) }
            return true
        }
        val next = when (direction) {
            Direction.Left -> if (focus % PHOTO_COLUMNS > 0) focus - 1 else focus
            Direction.Right -> if (focus % PHOTO_COLUMNS < PHOTO_COLUMNS - 1) focus + 1 else focus
            Direction.Up -> focus - PHOTO_COLUMNS
            Direction.Down -> focus + PHOTO_COLUMNS
        }
        if (next in paths.indices && next != focus) {
            fadeFocus(focus, 0)
            focus = next
            fadeFocus(next, 255)
            val layout = grid.layoutManager as GridLayoutManager
            if (next !in layout.findFirstCompletelyVisibleItemPosition()..layout.findLastCompletelyVisibleItemPosition()) {
                grid.scrollToPosition(next)
            }
        }
        return true
    }
}

class PanelHost(
    context: Context,
    private val d: Float,
    private val fonts: PanelFonts,
    private val scope: CoroutineScope,
    private val bakeDispatcher: CoroutineDispatcher,
    private val channel: AndroidChannel,
    private val apps: AppsService,
    private val updater: Updater,
    private val version: String,
    private val buildNumber: Long,
    private val icon: () -> Bitmap,
    private val click: () -> Unit,
    private val onCovered: (Boolean) -> Unit,
    private val onPanelClosed: () -> Unit,
) : FrameLayout(context), PanelNavigator {
    private val overlays = ArrayList<Overlay>()
    private val screenHeight = resources.displayMetrics.heightPixels
    private val iconCache = HashMap<String, Bitmap>()
    private val glass = listOf(WIDE_PANEL, NARROW_PANEL).associateWith { width ->
        lazy { sidePanelBitmap(px(width), screenHeight, d) }
    }

    init {
        scope.launch(Dispatchers.Default) { glass.values.forEach { it.value } }
    }

    val active: Boolean get() = overlays.isNotEmpty() || homeChoice != null
    private var homeChoice: View? = null

    private fun px(dp: Float) = (dp * d).roundToInt()

    private fun loadIcon(packageName: String, onLoaded: (Bitmap?) -> Unit) {
        iconCache[packageName]?.let {
            onLoaded(it)
            return
        }
        scope.launch {
            val bytes = apps.getAppIcon(packageName)
            val bitmap = withContext(bakeDispatcher) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
            if (bitmap != null) iconCache[packageName] = bitmap
            onLoaded(bitmap)
        }
    }

    private fun add(overlay: Overlay) {
        if (overlay is DialogOverlay) topPanel?.top?.showFocus(false)
        overlays.add(overlay)
        addView(overlay.view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        if (overlay is PanelOverlay) return
        overlay.view.alpha = 0f
        overlay.view.animate().alpha(1f).setDuration(FADE_MS).setInterpolator(Ease.outCubic).withLayer().withEndAction {
            if (overlay is PhotoOverlay) cover(true)
        }
    }

    private fun cover(on: Boolean) {
        overlays.filter { it !is PhotoOverlay }.forEach { it.view.visibility = if (on) INVISIBLE else VISIBLE }
        onCovered(on)
    }

    private fun remove(overlay: Overlay) {
        if (overlay is PhotoOverlay) cover(false)
        overlays.remove(overlay)
        if (overlays.lastOrNull() is PanelOverlay) topPanel?.top?.showFocus(true)
        if (overlay is PanelOverlay) {
            removeView(overlay.view)
            onPanelClosed()
            return
        }
        overlay.view.animate().alpha(0f).setDuration(FADE_MS).setInterpolator(Ease.outCubic).withLayer().withEndAction { removeView(overlay.view) }
    }

    private inner class PanelOverlay(width: Float, first: PanelPage) : Overlay {
        val panel: SidePanel = SidePanel(context, d, fonts, glass.getValue(width).value, ::loadIcon, click) { remove(this) }
        override val view: View get() = panel

        init {
            panel.open(first)
        }

        override fun refresh() = panel.top.refresh()

        override fun handleKey(event: KeyEvent, direction: Direction?): Boolean {
            if (event.action != KeyEvent.ACTION_DOWN) return true
            when {
                direction != null -> panel.top.move(direction)
                event.repeatCount != 0 -> Unit
                event.keyCode == KeyEvent.KEYCODE_BACK -> panel.pop()
                else -> panel.top.confirm()
            }
            return true
        }
    }

    fun openPanel(width: Float, first: PanelPage) = add(PanelOverlay(width, first))

    private val topPanel: SidePanel? get() = (overlays.lastOrNull { it is PanelOverlay } as PanelOverlay?)?.panel

    override fun push(page: PanelPage) {
        topPanel?.push(page)
    }

    override fun pop() {
        topPanel?.pop()
    }

    private fun body(text: String) = TextView(context).apply {
        this.text = text
        typeface = fonts.regular
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, 14 * d)
        exactText()
    }

    override fun showAbout() {
        lateinit var dialog: DialogOverlay
        val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val top = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        top.addView(ImageView(context).apply {
            setImageBitmap(roundedCover(icon(), px(72f), px(72f), 12 * d))
        }, LinearLayout.LayoutParams(px(72f), px(72f)))
        val names = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        names.addView(TextView(context).apply {
            text = "Mach Launcher"
            typeface = fonts.regular
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, 24 * d)
            includeFontPadding = false
        })
        names.addView(body("v$version ($buildNumber)").apply { letterSpacing = BODY_SPACING })
        names.addView(TextView(context).apply {
            text = "By terminalis"
            typeface = fonts.regular
            setTextColor(SMALL_TEXT_COLOR)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, 12 * d)
            letterSpacing = SMALL_SPACING
            includeFontPadding = false
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = px(18.5f) })
        top.addView(names, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = px(24f) })
        content.addView(top)
        content.addView(body(AppStrings.textAboutDialog("https://github.com/terminalis/mach-launcher")), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = px(25f) })
        val check = DialogPill(context, d, Glyphs.DOWNLOAD, AppStrings.updateCheck, fonts) {
            scope.launch { checkForUpdates(updater, UpdateDialogViews(), version) }
        }
        content.addView(check, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = px(20f)
            bottomMargin = px(4f)
        })
        val buttons = listOf(DialogButton("Close") { remove(dialog) })
        dialog = DialogOverlay(context, d, fonts, null, content, buttons, onBack = { remove(dialog) }, pill = check, minWidthDp = ABOUT_WIDTH)
        add(dialog)
    }

    private inner class UpdateDialogViews : UpdateDialogs {
        override suspend fun <T> withProgress(label: String, task: suspend () -> T): T {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(ProgressBar(context).apply {
                indeterminateTintList = ColorStateList.valueOf(Color.WHITE)
            }, LinearLayout.LayoutParams(px(20f), px(20f)))
            row.addView(body(label), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { leftMargin = px(16f) })
            val dialog = DialogOverlay(context, d, fonts, null, row, emptyList(), cancelable = false)
            add(dialog)
            try {
                return task()
            } finally {
                remove(dialog)
            }
        }

        override suspend fun release(title: String, message: String, notes: String, offerDownload: Boolean): Boolean =
            suspendCancellableCoroutine { result ->
                lateinit var dialog: DialogOverlay
                fun finish(value: Boolean) {
                    remove(dialog)
                    result.resume(value)
                }
                val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
                content.addView(body(message), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
                var scroll: ScrollView? = null
                if (notes.isNotEmpty()) {
                    content.addView(TextView(context).apply {
                        text = AppStrings.updateChangelog
                        typeface = fonts.bold
                        setTextColor(Color.WHITE)
                        setTextSize(TypedValue.COMPLEX_UNIT_PX, 14 * d)
                    }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = px(14f) })
                    val tallest = px(220f)
                    scroll = object : ScrollView(context) {
                        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(tallest, MeasureSpec.AT_MOST))
                        }
                    }.apply {
                        isVerticalScrollBarEnabled = true
                        setPadding(px(14f), px(10f), px(14f), px(10f))
                        addView(body(notes).apply { fixLineHeight(14 * 1.35f * d) })
                    }
                    content.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = px(8f) })
                }
                val buttons = if (offerDownload) {
                    listOf(DialogButton("Cancel") { finish(false) }, DialogButton(AppStrings.updateDownloadButton) { finish(true) })
                } else {
                    listOf(DialogButton("OK") { finish(false) })
                }
                dialog = DialogOverlay(
                    context, d, fonts, title, content, buttons, scroll, onBack = { finish(false) },
                    minWidthDp = if (notes.isEmpty()) 280f else 468f, maxWidthDp = 588f,
                )
                add(dialog)
            }

        override suspend fun confirm(title: String, body: String, action: String): Boolean =
            suspendCancellableCoroutine { result ->
                lateinit var dialog: DialogOverlay
                fun finish(value: Boolean) {
                    remove(dialog)
                    result.resume(value)
                }
                val buttons = listOf(DialogButton("Cancel") { finish(false) }, DialogButton(action) { finish(true) })
                dialog = DialogOverlay(context, d, fonts, title, body(body), buttons, onBack = { finish(false) })
                add(dialog)
            }
    }

    override fun pickPhoto(onPicked: (String) -> Unit) {
        lateinit var picker: PhotoOverlay
        picker = PhotoOverlay(context, d, fonts, scope, bakeDispatcher, channel) { path ->
            remove(picker)
            if (path != null) onPicked(path)
        }
        add(picker)
    }

    override fun showHomeAppChoice() {
        val black = View(context).apply { setBackgroundColor(Color.BLACK) }
        homeChoice = black
        addView(black, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        apps.openHomeSettings()
    }

    fun onResumed() {
        homeChoice?.let {
            removeView(it)
            homeChoice = null
        }
        overlays.forEach { it.refresh() }
    }

    fun refresh() = overlays.forEach { it.refresh() }

    fun handleKey(event: KeyEvent, direction: Direction?): Boolean = overlays.lastOrNull()?.handleKey(event, direction) ?: true
}
