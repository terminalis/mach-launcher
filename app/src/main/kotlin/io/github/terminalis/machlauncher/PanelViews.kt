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

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Layout
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.roundToInt

private const val FOCUS_MS = 90L
private const val ROW_ZOOM = 1.018f
private const val TAB_STRIP = -1
private const val NO_ROW = -2

class PanelFonts(val regular: Typeface, val medium: Typeface, val semiBold: Typeface, val bold: Typeface, val icons: Typeface)

const val BODY_SPACING = 0.25f / 14f
const val SMALL_SPACING = 0.4f / 12f
const val SMALL_TEXT_COLOR = 0xFFBABCBC.toInt()

fun TextView.exactText() {
    includeFontPadding = false
    paint.isSubpixelText = true
    paint.isLinearText = true
    breakStrategy = Layout.BREAK_STRATEGY_SIMPLE
    hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
}

fun TextView.fixLineHeight(px: Float) {
    exactText()
    val extra = px - paint.getFontMetrics(null)
    setLineSpacing(extra, 1f)
    setPadding(paddingLeft, paddingTop, paddingRight, extra.roundToInt().coerceAtLeast(0))
}

private fun rowHeight(row: PanelRow) = when {
    row.leading is RowLeading.Glyph -> 56f
    row.leading is RowLeading.AppIcon -> 54f
    row.trailing == RowTrailing.Check || row.trailing is RowTrailing.Toggle -> 46f
    else -> 39f
}

fun sidePanelBitmap(width: Int, height: Int, d: Float): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val r = 32 * d
    val w = width.toFloat()
    val h = height.toFloat()
    val shape = Path().apply {
        addRoundRect(RectF(0f, 0f, w, h), floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r), Path.Direction.CW)
    }
    canvas.drawPath(shape, Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        shader = LinearGradient(0f, 0f, w, h, 0xFF2A2A2F.toInt(), 0xFF161618.toInt(), Shader.TileMode.CLAMP)
    })
    canvas.drawPath(shape, Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        shader = RadialGradient(
            w, -0.1f * h, 1.4f * minOf(w, h),
            intArrayOf(0x33FFFFFF, 0x0AFFFFFF, 0x00FFFFFF), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP,
        )
    })
    canvas.drawPath(Path().apply {
        moveTo(w, 0.5f * d)
        lineTo(r, 0.5f * d)
        arcTo(RectF(0.5f * d, 0.5f * d, 2 * r, 2 * r), 270f, -90f)
        lineTo(0.5f * d, h - r)
        arcTo(RectF(0.5f * d, h - 2 * r, 2 * r, h - 0.5f * d), 180f, -90f)
        lineTo(w, h - 0.5f * d)
    }, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = d
        color = 0x14FFFFFF
    })
    bitmap.prepareToDraw()
    return bitmap
}

class PanelRowView(
    context: Context,
    private val d: Float,
    row: PanelRow,
    private val fonts: PanelFonts,
) : View(context) {
    var row = row
        private set
    var icon: Bitmap? = null
        set(value) {
            field = value
            invalidate()
        }
    private var t = 0f
    private var toggleT = if ((row.trailing as? RowTrailing.Toggle)?.on == true) 1f else 0f
    private var focusAnimator: ValueAnimator? = null
    private var toggleAnimator: ValueAnimator? = null
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val boxEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = d
    }
    private val glyphPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = fonts.icons
        textSize = 20 * d
        textAlign = Paint.Align.CENTER
    }
    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = fonts.regular
        textSize = 14 * d
        letterSpacing = BODY_SPACING
        isSubpixelText = true
        isLinearText = true
    }
    private val checkPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = fonts.icons
        textSize = 24 * d
        textAlign = Paint.Align.CENTER
    }
    private val iconPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dst = RectF()

    fun update(next: PanelRow) {
        val on = (next.trailing as? RowTrailing.Toggle)?.on
        val was = (row.trailing as? RowTrailing.Toggle)?.on
        row = next
        if (on != null && was != null && on != was) {
            toggleAnimator?.cancel()
            toggleAnimator = ValueAnimator.ofFloat(toggleT, if (on) 1f else 0f).apply {
                duration = FOCUS_MS
                interpolator = Ease.outCubic
                addUpdateListener {
                    toggleT = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }
        invalidate()
    }

    fun setRowFocus(on: Boolean) {
        focusAnimator?.cancel()
        setLayerType(LAYER_TYPE_HARDWARE, null)
        focusAnimator = ValueAnimator.ofFloat(t, if (on) 1f else 0f).apply {
            duration = FOCUS_MS
            interpolator = Ease.outCubic
            addUpdateListener {
                t = it.animatedValue as Float
                val s = 1f + (ROW_ZOOM - 1f) * t
                scaleX = s
                scaleY = s
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (focusAnimator === animation) setLayerType(LAYER_TYPE_NONE, null)
                }
            })
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val middle = h / 2f
        fill.color = lerpColor(0x0AFFFFFF, Color.WHITE, t)
        canvas.drawRoundRect(0f, 0f, w, h, 16 * d, 16 * d, fill)
        val ink = if (t >= 0.5f) FOCUSED_GLYPH_COLOR else 0xE6FFFFFF.toInt()
        var x = 14 * d
        when (val leading = row.leading) {
            is RowLeading.Glyph -> {
                val box = 34 * d
                val top = middle - box / 2f
                fill.color = lerpColor(0x0FFFFFFF, 0x0F000000, t)
                canvas.drawRoundRect(x, top, x + box, top + box, 10 * d, 10 * d, fill)
                boxEdge.color = lerpColor(0x0AFFFFFF, 0x14000000, t)
                canvas.drawRoundRect(x, top, x + box, top + box, 10 * d, 10 * d, boxEdge)
                glyphPaint.color = lerpColor(0xC7FFFFFF.toInt(), FOCUSED_GLYPH_COLOR, t)
                val fm = glyphPaint.fontMetrics
                canvas.drawText(leading.glyph, x + box / 2f, middle - (fm.ascent + fm.descent) / 2f, glyphPaint)
                x += box + 12 * d
            }
            is RowLeading.AppIcon -> {
                val size = 32 * d
                icon?.let {
                    dst.set(x, middle - size / 2f, x + size, middle + size / 2f)
                    canvas.drawBitmap(it, null, dst, iconPaint)
                }
                x += size + 12 * d
            }
            RowLeading.None -> Unit
        }
        var right = w - 14 * d
        when (val trailing = row.trailing) {
            RowTrailing.Chevron -> {
                glyphPaint.color = lerpColor(0x40FFFFFF, 0x991C1C1E.toInt(), t)
                val fm = glyphPaint.fontMetrics
                canvas.drawText(Glyphs.CHEVRON_RIGHT, right - 10 * d, middle - (fm.ascent + fm.descent) / 2f, glyphPaint)
                right -= 28 * d
            }
            RowTrailing.Check -> {
                checkPaint.color = lerpColor(Color.WHITE, FOCUSED_GLYPH_COLOR, t)
                val fm = checkPaint.fontMetrics
                canvas.drawText(Glyphs.CHECK, right - 12 * d, middle - (fm.ascent + fm.descent) / 2f, checkPaint)
                right -= 36 * d
            }
            is RowTrailing.Label -> {
                labelPaint.color = ink
                val width = labelPaint.measureText(trailing.text)
                val fm = labelPaint.fontMetrics
                canvas.drawText(trailing.text, right - width, middle - (fm.ascent + fm.descent) / 2f, labelPaint)
                right -= width + 12 * d
            }
            is RowTrailing.Toggle -> {
                drawToggle(canvas, right, middle)
                right -= 54 * d
            }
            RowTrailing.None -> Unit
        }
        labelPaint.color = ink
        val text = TextUtils.ellipsize(row.label, labelPaint, right - x, TextUtils.TruncateAt.END)
        val fm = labelPaint.fontMetrics
        canvas.drawText(text, 0, text.length, x, middle - (fm.ascent + fm.descent) / 2f, labelPaint)
    }

    private fun drawToggle(canvas: Canvas, right: Float, middle: Float) {
        val ink = lerpColor(Color.WHITE, FOCUSED_GLYPH_COLOR, t)
        val width = 42 * d
        val height = 24 * d
        val left = right - width
        val top = middle - height / 2f
        fill.color = lerpColor(Color.argb(0x29, Color.red(ink), Color.green(ink), Color.blue(ink)), Color.WHITE, toggleT)
        canvas.drawRoundRect(left, top, right, top + height, 12 * d, 12 * d, fill)
        boxEdge.color = lerpColor(Color.argb(0x14, Color.red(ink), Color.green(ink), Color.blue(ink)), 0x2E000000, toggleT)
        canvas.drawRoundRect(left, top, right, top + height, 12 * d, 12 * d, boxEdge)
        fill.color = lerpColor(Color.argb(0xC7, Color.red(ink), Color.green(ink), Color.blue(ink)), 0xFF1A1A1E.toInt(), toggleT)
        val knob = 9 * d
        val cx = left + 3 * d + knob + (width - 6 * d - 2 * knob) * toggleT
        canvas.drawCircle(cx, middle, knob, fill)
    }
}

private class TabStripView(context: Context, private val d: Float, private val tabs: PanelTabs, private val fonts: PanelFonts) : View(context) {
    var focused = false
        set(value) {
            field = value
            invalidate()
        }
    private val strip = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x0DFFFFFF }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = d
    }
    private val selectedFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x42FFFFFF }
    private val glyph = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = fonts.icons
        textSize = 20 * d
        textAlign = Paint.Align.CENTER
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRoundRect(0f, 0f, w, h, 16 * d, 16 * d, strip)
        edge.color = 0x0FFFFFFF
        canvas.drawRoundRect(0.5f * d, 0.5f * d, w - 0.5f * d, h - 0.5f * d, 16 * d, 16 * d, edge)
        val inset = 4 * d
        val slot = (w - 2 * inset) / tabs.glyphs.size
        tabs.glyphs.forEachIndexed { i, text ->
            val selected = tabs.selected == i
            val left = inset + slot * i + 6 * d
            val right = inset + slot * (i + 1) - 6 * d
            if (selected) {
                canvas.drawRoundRect(left, inset, right, h - inset, 12 * d, 12 * d, selectedFill)
                if (focused) {
                    edge.color = 0x47FFFFFF
                    canvas.drawRoundRect(left, inset, right, h - inset, 12 * d, 12 * d, edge)
                }
            }
            glyph.color = if (selected) Color.WHITE else 0x80FFFFFF.toInt()
            val fm = glyph.fontMetrics
            canvas.drawText(text, (left + right) / 2f, h / 2f - (fm.ascent + fm.descent) / 2f, glyph)
        }
    }
}

class PanelPageView(
    context: Context,
    private val d: Float,
    val page: PanelPage,
    private val fonts: PanelFonts,
    private val loadIcon: (String, (Bitmap?) -> Unit) -> Unit,
    private val click: () -> Unit,
) : LinearLayout(context) {
    private val title = TextView(context).apply {
        typeface = if (page.appHeader != null) fonts.medium else fonts.semiBold
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, 20 * d)
        letterSpacing = (if (page.appHeader != null) 0.15f else 0.1f) / 20f
        exactText()
        maxLines = if (page.appHeader != null && !page.showsPackage) 1 else 2
        ellipsize = TextUtils.TruncateAt.END
    }
    private val tabStrip = page.tabs?.let { TabStripView(context, d, it, fonts) }
    private val viewport = ScrollView(context).apply {
        isVerticalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        isFocusable = false
        descendantFocusability = FOCUS_BLOCK_DESCENDANTS
    }
    private val list = LinearLayout(context).apply { orientation = VERTICAL }
    private val empty = TextView(context).apply {
        typeface = fonts.regular
        setTextColor(0x99FFFFFF.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_PX, 14 * d)
        letterSpacing = BODY_SPACING
        gravity = Gravity.CENTER
        visibility = GONE
    }
    private var rows: List<PanelRowView> = emptyList()
    private var focusShown = true
    private var focus = 0
    private var scroll = 0f
    private var scrollAnimator: ValueAnimator? = null
    private var revealPending = false

    init {
        orientation = VERTICAL
        list.setPadding(0, 0, 0, if (page.appHeader != null) 0 else px(16f))
        if (page.appHeader != null) addView(appHeader(page.appHeader)) else addHeader()
        tabStrip?.let {
            addView(it, LayoutParams(LayoutParams.MATCH_PARENT, px(48f)).apply {
                setMargins(px(12f), px(2f), px(12f), px(10f))
            })
        }
        val body = FrameLayout(context)
        addView(body, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        body.addView(viewport, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        viewport.addView(list, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        body.addView(empty, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        rebuild()
    }

    private fun px(dp: Float) = (dp * d).roundToInt()

    private fun addHeader() {
        title.text = page.title()
        addView(title, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            setMargins(px(14f), px(13f), px(8f), px(12f))
        })
        addView(hairline(), LayoutParams(LayoutParams.MATCH_PARENT, px(1f)).apply { setMargins(px(8f), 0, px(8f), px(8f)) })
    }

    private fun small(text: String) = TextView(context).apply {
        this.text = text
        typeface = fonts.regular
        setTextColor(SMALL_TEXT_COLOR)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, 12 * d)
        letterSpacing = SMALL_SPACING
        exactText()
        minHeight = px(15f)
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
    }

    private fun appHeader(app: App) = LinearLayout(context).apply {
        orientation = VERTICAL
        val top = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val icon = ImageView(context)
        top.addView(icon, LayoutParams(px(50f), px(50f)))
        loadIcon(app.packageName) { icon.setImageBitmap(it) }
        val text = LinearLayout(context).apply { orientation = VERTICAL }
        title.text = app.name
        text.addView(title)
        if (!page.showsPackage) text.addView(small("v${app.version}"))
        top.addView(text, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = px(if (page.showsPackage) 8f else 16f) })
        addView(top)
        if (page.showsPackage) {
            addView(small(app.packageName).apply { minHeight = px(16f) }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = px(8.5f) })
            addView(small("v${app.version}"))
            addView(View(context).apply { setBackgroundColor(0xFF3F484A.toInt()) }, LayoutParams(LayoutParams.MATCH_PARENT, px(1f)).apply {
                topMargin = px(6.5f)
                bottomMargin = px(7.5f)
            })
        } else {
            addView(hairline(), LayoutParams(LayoutParams.MATCH_PARENT, px(1f)).apply { setMargins(px(14f), px(26f), px(14f), px(10f)) })
        }
    }

    private fun hairline() = View(context).apply {
        background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0x1FFFFFFF, 0x05FFFFFF))
    }

    private fun rebuild() {
        list.removeAllViews()
        for (paragraph in page.paragraphs) {
            list.addView(TextView(context).apply {
                text = paragraph
                typeface = fonts.regular
                setTextColor(0xB3FFFFFF.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_PX, 14 * d)
                letterSpacing = BODY_SPACING
                fixLineHeight(14 * 1.35f * d)
            }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { setMargins(px(14f), px(6f), px(14f), px(6f)) })
        }
        if (page.paragraphs.isNotEmpty()) list.addView(View(context), LayoutParams(LayoutParams.MATCH_PARENT, px(8f)))
        val entries = page.rows()
        rows = entries.map { entry ->
            if (entry.dividerBefore) {
                list.addView(hairline(), LayoutParams(LayoutParams.MATCH_PARENT, px(1f)).apply { setMargins(px(14f), px(10f), px(14f), px(10f)) })
            }
            PanelRowView(context, d, entry, fonts).also { view ->
                list.addView(view, LayoutParams(LayoutParams.MATCH_PARENT, px(rowHeight(entry))).apply {
                    setMargins(px(if (entry.indented) 32f else 8f), px(3f), px(8f), px(3f))
                })
                (entry.leading as? RowLeading.AppIcon)?.let { icon -> loadIcon(icon.packageName) { view.icon = it } }
            }
        }
        empty.text = page.emptyText
        empty.visibility = if (rows.isEmpty() && page.emptyText != null) VISIBLE else GONE
        focus = if (page.opensUnfocused) NO_ROW else entries.indexOfFirst { it.focusFirst }.coerceAtLeast(0)
        rows.getOrNull(focus)?.setRowFocus(focusShown)
        setScroll(0f)
        revealPending = true
        requestLayout()
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (revealPending && viewport.height > 0) {
            revealPending = false
            ensureVisible(animated = false, centred = true)
        }
    }

    fun showFocus(on: Boolean) {
        focusShown = on
        rows.getOrNull(focus)?.setRowFocus(on)
    }

    fun refresh() {
        val entries = page.rows()
        title.text = page.title()
        if (entries.map { it.label } == rows.map { it.row.label } && entries.size == rows.size) {
            rows.forEachIndexed { i, view -> view.update(entries[i]) }
            return
        }
        val keep = focus
        val at = scroll
        rebuild()
        revealPending = false
        if (focus != TAB_STRIP && keep in entries.indices && keep != focus) {
            rows.getOrNull(focus)?.setRowFocus(false)
            focus = keep
            rows[keep].setRowFocus(focusShown)
        }
        setScroll(at)
    }

    private fun setScroll(value: Float) {
        scroll = value
        viewport.scrollTo(0, value.roundToInt())
    }

    private fun ensureVisible(animated: Boolean, centred: Boolean) {
        val view = rows.getOrNull(focus) ?: return
        val maxScroll = (list.height - viewport.height).coerceAtLeast(0).toFloat()
        val bottomPad = if (view === rows.last()) list.paddingBottom else 0
        val target = if (centred) {
            view.top + view.height / 2f - viewport.height / 2f
        } else {
            val pad = 3 * d
            when {
                view.top - pad < scroll -> view.top - pad
                view.bottom + pad + bottomPad > scroll + viewport.height -> view.bottom + pad + bottomPad - viewport.height
                else -> scroll
            }
        }.coerceIn(0f, maxScroll)
        scrollAnimator?.cancel()
        if (!animated) {
            setScroll(target)
            return
        }
        scrollAnimator = ValueAnimator.ofFloat(scroll, target).apply {
            duration = 300
            interpolator = Ease.out
            addUpdateListener { setScroll(it.animatedValue as Float) }
            start()
        }
    }

    private fun setFocus(next: Int) {
        rows.getOrNull(focus)?.setRowFocus(false)
        tabStrip?.focused = next == TAB_STRIP
        focus = next
        rows.getOrNull(next)?.setRowFocus(true)
        if (next != TAB_STRIP) ensureVisible(page.centred, page.centred)
    }

    fun move(direction: Direction) {
        click()
        if (focus == NO_ROW) {
            if (rows.isNotEmpty()) setFocus(firstFocusedRow(direction, rows.size))
            return
        }
        val tabs = page.tabs
        if (focus == TAB_STRIP) {
            when (direction) {
                Direction.Left, Direction.Right -> {
                    tabs!!.step(if (direction == Direction.Right) 1 else -1)
                    tabStrip!!.invalidate()
                    title.text = page.title()
                    rebuild()
                    rows.getOrNull(focus)?.setRowFocus(false)
                    focus = TAB_STRIP
                }
                Direction.Down -> if (rows.isNotEmpty()) setFocus(0)
                Direction.Up -> Unit
            }
            return
        }
        when (direction) {
            Direction.Up -> if (focus > 0) setFocus(focus - 1) else if (tabs != null) setFocus(TAB_STRIP)
            Direction.Down -> if (focus < rows.size - 1) setFocus(focus + 1)
            else -> Unit
        }
    }

    fun confirm() {
        rows.getOrNull(focus)?.row?.onPress?.invoke()
    }
}

class SidePanel(
    context: Context,
    private val d: Float,
    private val fonts: PanelFonts,
    glass: Bitmap,
    private val loadIcon: (String, (Bitmap?) -> Unit) -> Unit,
    private val click: () -> Unit,
    private val onClosed: () -> Unit,
) : FrameLayout(context) {
    private val barrier = View(context).apply {
        setBackgroundColor(0x8A000000.toInt())
        alpha = 0f
    }
    private val panel = FrameLayout(context).apply {
        alpha = 0f
        clipChildren = true
    }
    private val holder = FrameLayout(context)
    private val pages = ArrayList<PanelPageView>()
    private var running: ValueAnimator? = null
    private var closing = false

    init {
        addView(barrier, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(panel, LayoutParams(glass.width, LayoutParams.MATCH_PARENT, Gravity.END))
        panel.addView(ImageLayerView(context, glass), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        val inset = (16 * d).roundToInt()
        panel.addView(holder, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply { setMargins(inset, inset, inset, inset) })
    }

    private fun run(from: Float, to: Float, ms: Long, ease: android.view.animation.Interpolator, onEnd: () -> Unit, onValue: (Float) -> Unit) {
        running?.cancel()
        running = ValueAnimator.ofFloat(from, to).apply {
            duration = ms
            interpolator = ease
            addUpdateListener { onValue(it.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (!cancelled) onEnd()
                }
            })
            start()
        }
    }

    fun open(first: PanelPage) {
        addPage(first)
        panel.setLayerType(LAYER_TYPE_HARDWARE, null)
        run(0f, 1f, 150, Ease.outCubic, { panel.setLayerType(LAYER_TYPE_NONE, null) }) {
            barrier.alpha = it
            panel.alpha = it
        }
    }

    private fun addPage(page: PanelPage): PanelPageView {
        val view = PanelPageView(context, d, page, fonts, loadIcon, click)
        holder.addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        pages.add(view)
        return view
    }

    private fun place(leaving: View, arriving: View, t: Float) {
        val width = holder.width.toFloat()
        arriving.alpha = Ease.outCubic.getInterpolation(interval(t, 0.45f, 1f))
        arriving.translationX = 0.04f * width * (1f - Ease.outCubic.getInterpolation(interval(t, 0.25f, 1f)))
        leaving.alpha = 1f - Ease.inCubic.getInterpolation(interval(t, 0f, 0.4f))
        leaving.translationX = -0.02f * width * Ease.inCubic.getInterpolation(interval(t, 0f, 0.5f))
    }

    private fun interval(t: Float, from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)

    fun push(page: PanelPage) {
        val leaving = pages.last()
        val arriving = addPage(page)
        arriving.alpha = 0f
        leaving.setLayerType(LAYER_TYPE_HARDWARE, null)
        arriving.setLayerType(LAYER_TYPE_HARDWARE, null)
        run(0f, 1f, 140, LinearInterpolator(), {
            leaving.setLayerType(LAYER_TYPE_NONE, null)
            arriving.setLayerType(LAYER_TYPE_NONE, null)
            leaving.visibility = INVISIBLE
        }) { place(leaving, arriving, it) }
    }

    fun pop() {
        if (closing) return
        if (pages.size < 2) {
            close()
            return
        }
        val arriving = pages.removeAt(pages.lastIndex)
        val leaving = pages.last()
        leaving.visibility = VISIBLE
        leaving.setLayerType(LAYER_TYPE_HARDWARE, null)
        arriving.setLayerType(LAYER_TYPE_HARDWARE, null)
        leaving.refresh()
        run(1f, 0f, 110, LinearInterpolator(), {
            leaving.setLayerType(LAYER_TYPE_NONE, null)
            holder.removeView(arriving)
        }) { place(leaving, arriving, it) }
    }

    private fun close() {
        closing = true
        panel.setLayerType(LAYER_TYPE_HARDWARE, null)
        run(1f, 0f, 150, Ease.outCubic, onClosed) {
            barrier.alpha = it
            panel.alpha = it
        }
    }

    val top: PanelPageView get() = pages.last()
}
