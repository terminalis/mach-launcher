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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.LinearInterpolator
import kotlin.math.ceil
import kotlin.math.roundToInt

private const val TILE_RADIUS = 12f
private const val TILE_ZOOM = 1.15f
private const val TILE_PRESSED = 0.9f
private const val TILE_SURFACE = 0xFF121212.toInt()

class TileContent(context: Context, private val d: Float) : View(context) {
    var image: Bitmap? = null
        set(value) {
            field = value
            invalidate()
        }
    var dim = 1f
        set(value) {
            field = value
            invalidate()
        }
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dst = RectF()

    init {
        elevation = 4 * d
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, TILE_RADIUS * d)
            }
        }
        outlineAmbientShadowColor = Color.BLACK
        outlineSpotShadowColor = Color.BLACK
    }

    override fun onDraw(canvas: Canvas) {
        dst.set(0f, 0f, width.toFloat(), height.toFloat())
        image?.let { canvas.drawBitmap(it, null, dst, paint) }
        if (dim > 0f) {
            dimPaint.color = Color.argb((0x1A * dim).toInt(), 0, 0, 0)
            canvas.drawRoundRect(dst, TILE_RADIUS * d, TILE_RADIUS * d, dimPaint)
        }
    }
}

class PlusContent(context: Context, private val d: Float, private val glyph: Bitmap) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dst = RectF()
    var lit = false
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        fill.color = if (lit) 0x33FFFFFF else 0x1AFFFFFF
        canvas.drawRoundRect(0f, 0f, w, h, TILE_RADIUS * d, TILE_RADIUS * d, fill)
        val size = glyph.width / TILE_ZOOM
        dst.set((w - size) / 2f, (h - size) / 2f, (w + size) / 2f, (h + size) / 2f)
        canvas.drawBitmap(glyph, null, dst, paint)
    }
}

class PlusFrame(context: Context, d: Float, margin: Int, private val plus: PlusContent, width: Int, height: Int) :
    ZoomFrame(context, d, margin, plus, width, height, TILE_RADIUS * d, TILE_ZOOM, 0f, TILE_PRESSED) {
    override fun onFocusShown(on: Boolean) {
        plus.lit = on
    }
}

class MoveArrowsView(context: Context, private val d: Float, private val icons: Typeface, private val inDock: Boolean) : View(context) {
    private val circle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCC1E1E1E.toInt() }
    private val glyph = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = icons
        textSize = 24 * d
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }

    init {
        elevation = 5 * d
        outlineProvider = null
    }

    private fun arrow(canvas: Canvas, x: Float, y: Float, text: String) {
        canvas.drawCircle(x, y, 18 * d, circle)
        val fm = glyph.fontMetrics
        canvas.drawText(text, x, y - (fm.ascent + fm.descent) / 2f, glyph)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = 18 * d
        arrow(canvas, r, h / 2f, Glyphs.CHEVRON_LEFT)
        arrow(canvas, w - r, h / 2f, Glyphs.CHEVRON_RIGHT)
        if (!inDock) {
            arrow(canvas, w / 2f, r, Glyphs.CHEVRON_UP)
            arrow(canvas, w / 2f, h - r, Glyphs.CHEVRON_DOWN)
        }
    }
}

class TileLabelView(context: Context, private val bitmap: Bitmap) : View(context) {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    init {
        alpha = 0f
    }

    override fun hasOverlappingRendering() = false

    override fun onDraw(canvas: Canvas) {
        canvas.drawBitmap(bitmap, (width - bitmap.width) / 2f, 0f, paint)
    }
}

class TileFrame(
    context: Context,
    d: Float,
    margin: Int,
    val content: TileContent,
    width: Int,
    height: Int,
) : ZoomFrame(context, d, margin, content, width, height, TILE_RADIUS * d, TILE_ZOOM, 0f, TILE_PRESSED) {
    var label: View? = null
    private val tileWidth = width
    private val tileHeight = height
    private val tileMargin = margin
    private var arrows: View? = null
    private var dimAnimator: ValueAnimator? = null
    private var pressAnimator: ValueAnimator? = null

    fun showMoving(moving: View?) {
        arrows?.let { removeView(it) }
        arrows = moving
        zoom = if (moving != null) 1f else TILE_ZOOM
        if (moving != null) {
            addView(moving, LayoutParams(tileWidth, tileHeight).apply {
                leftMargin = tileMargin
                topMargin = tileMargin
            })
        }
    }

    override fun onFocusShown(on: Boolean) {
        label?.animate()?.alpha(if (on) 1f else 0f)?.setDuration(180)?.setInterpolator(LinearInterpolator())
        dimAnimator?.cancel()
        dimAnimator = ValueAnimator.ofFloat(content.dim, if (on) 0f else 1f).apply {
            duration = 180
            interpolator = Ease.inOut
            addUpdateListener { content.dim = it.animatedValue as Float }
            start()
        }
    }

    override fun onPressShown(on: Boolean) {
        pressAnimator?.cancel()
        pressAnimator = ValueAnimator.ofFloat(content.alpha, if (on) 0.85f else 1f).apply {
            duration = 150
            addUpdateListener { content.alpha = it.animatedValue as Float }
            start()
        }
    }
}

fun bakeTile(banner: ByteArray, width: Int, height: Int, d: Float): Bitmap? {
    val decoded = BitmapFactory.decodeByteArray(banner, 0, banner.size) ?: return null
    val source = Bitmap.createScaledBitmap(decoded, 480, (decoded.height * 480f / decoded.width).roundToInt(), true)
    return roundedCover(source, width, height, TILE_RADIUS * d)
}

fun bakeFallbackTile(icon: ByteArray, name: String, width: Int, height: Int, d: Float, regular: Typeface): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val radius = TILE_RADIUS * d
    canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TILE_SURFACE })
    val inset = 8 * d
    val innerWidth = width - 2 * inset
    val iconSlot = innerWidth * 2f / 5f
    BitmapFactory.decodeByteArray(icon, 0, icon.size)?.let { image ->
        val size = minOf(iconSlot, height - 2 * inset)
        val left = inset + (iconSlot - size) / 2f
        val top = (height - size) / 2f
        canvas.drawBitmap(image, null, RectF(left, top, left + size, top + size), Paint(Paint.FILTER_BITMAP_FLAG))
    }
    val textLeft = inset + iconSlot + inset
    val textWidth = (width - inset - textLeft).toInt()
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = regular
        textSize = 12 * d
        color = SMALL_TEXT_COLOR
        letterSpacing = SMALL_SPACING
    }
    val layout = StaticLayout.Builder.obtain(name, 0, name.length, paint, textWidth)
        .setMaxLines(3).setEllipsize(TextUtils.TruncateAt.END).setIncludePad(false)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
    canvas.translate(textLeft, (height - layout.height) / 2f)
    layout.draw(canvas)
    bitmap.prepareToDraw()
    return bitmap
}

fun bakeLabel(name: String, maxWidth: Int, d: Float, regular: Typeface): Bitmap {
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = regular
        textSize = 12 * d
        color = Color.WHITE
        letterSpacing = SMALL_SPACING
    }
    val text = TextUtils.ellipsize(name, paint, maxWidth.toFloat(), TextUtils.TruncateAt.END).toString()
    val pad = ceil(blurPx(6f, d) * 1.8f)
    val fm = paint.fontMetrics
    val width = (paint.measureText(text) + 2 * pad).toInt() + 1
    val height = (fm.descent - fm.ascent + 2 * pad).toInt() + 1
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val baseline = pad - fm.ascent
    paint.setShadowLayer(blurPx(6f, d), 0f, 0f, 0x8A000000.toInt())
    canvas.drawText(text, pad, baseline, paint)
    paint.setShadowLayer(blurPx(2f, d), 0f, 0f, 0xDD000000.toInt())
    canvas.drawText(text, pad, baseline, paint)
    paint.clearShadowLayer()
    canvas.drawText(text, pad, baseline, paint)
    bitmap.prepareToDraw()
    return bitmap
}

fun bakeGlyph(glyph: String, sizePx: Float, icons: Typeface, color: Int, shadow: TextShadow?, d: Float): Bitmap {
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = icons
        textSize = sizePx
        this.color = color
        textAlign = Paint.Align.CENTER
        if (shadow != null) setShadowLayer(blurPx(shadow.blurRadius, d), shadow.dx * d, shadow.dy * d, shadow.color)
    }
    val side = ceil(sizePx * 1.6f).toInt()
    val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    val fm = paint.fontMetrics
    Canvas(bitmap).drawText(glyph, side / 2f, side / 2f - (fm.ascent + fm.descent) / 2f, paint)
    bitmap.prepareToDraw()
    return bitmap
}
