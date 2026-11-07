package io.github.terminalis.machlauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import android.view.ViewOutlineProvider
import kotlin.math.roundToInt

private const val CARD_RADIUS = 8f
private const val CARD_ZOOM = 1.12f
private const val CARD_LIFT = 4f
private const val CARD_PRESSED = 0.97f
private const val POSTER_WIDTH = 720
private const val SETTLE_DELAY_MS = 150L

class CardArt(
    poster: ByteArray,
    pick: Recommendation,
    private val width: Int,
    private val height: Int,
    private val d: Float,
    bold: Typeface,
    regular: Typeface,
) {
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = bold
        textSize = 12 * d
        color = Color.WHITE
        letterSpacing = 0.25f / 12f
    }
    private val appPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = regular
        textSize = 10 * d
        color = 0xCCFFFFFF.toInt()
        letterSpacing = 0.25f / 10f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x1A000000 }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x3DFFFFFF }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val posterPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val progress = pick.progressPercent / 100f
    private val textWidth = (width - 16 * d).toInt()
    private val titleLayout = layout(pick.title, titlePaint, 2)
    private val appLayout = layout(pick.appName, appPaint, 1)
    private val descriptionLayout = if (pick.description.isEmpty()) null else layout(pick.description, appPaint, 1)
    private val source = BitmapFactory.decodeByteArray(poster, 0, poster.size).let {
        Bitmap.createScaledBitmap(it, POSTER_WIDTH, (it.height * POSTER_WIDTH.toFloat() / it.width).roundToInt(), true)
    }

    val live = roundedCover(source, width, height, CARD_RADIUS * d)
    val rest = bake(1f, false)
    val focus = bake(CARD_ZOOM, true)

    private fun layout(text: String, paint: TextPaint, lines: Int) =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, textWidth)
            .setMaxLines(lines).setEllipsize(TextUtils.TruncateAt.END).setIncludePad(false)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).build()

    private fun bake(zoom: Float, asFocused: Boolean): Bitmap {
        val bitmap = Bitmap.createBitmap((width * zoom).toInt(), (height * zoom).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawBitmap(roundedCover(source, bitmap.width, bitmap.height, CARD_RADIUS * d * zoom), 0f, 0f, posterPaint)
        canvas.scale(bitmap.width.toFloat() / width, bitmap.height.toFloat() / height)
        drawOverlays(canvas, asFocused)
        bitmap.prepareToDraw()
        return bitmap
    }

    fun drawOverlays(canvas: Canvas, asFocused: Boolean) {
        val w = width.toFloat()
        val h = height.toFloat()
        val radius = CARD_RADIUS * d
        val line = if (asFocused) descriptionLayout else null
        val labelHeight = 16 * d + titleLayout.height + 2 * d + appLayout.height + 8 * d + (line?.let { it.height + 4 * d } ?: 0f)
        val top = h - labelHeight
        labelPaint.shader = LinearGradient(0f, top, 0f, h, 0x00000000, if (asFocused) 0xB3000000.toInt() else 0x8C000000.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(0f, 0f, w, h, radius, radius, labelPaint)
        canvas.save()
        canvas.translate(8 * d, top + 16 * d)
        titleLayout.draw(canvas)
        canvas.translate(0f, titleLayout.height + 2 * d)
        appLayout.draw(canvas)
        if (line != null) {
            canvas.translate(0f, appLayout.height + 4 * d)
            line.draw(canvas)
        }
        canvas.restore()
        if (asFocused) {
            if (progress > 0f) drawProgress(canvas, w, h, radius)
        } else {
            canvas.drawRoundRect(0f, 0f, w, h, radius, radius, dimPaint)
        }
    }

    private fun drawProgress(canvas: Canvas, w: Float, h: Float, radius: Float) {
        val top = h - 3 * d
        val corners = floatArrayOf(0f, 0f, 0f, 0f, radius, radius, radius, radius)
        canvas.drawPath(Path().apply { addRoundRect(RectF(0f, top, w, h), corners, Path.Direction.CW) }, trackPaint)
        val left = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, radius, radius)
        canvas.drawPath(Path().apply { addRoundRect(RectF(0f, top, w * progress, h), left, Path.Direction.CW) }, barPaint)
    }
}

class RowCardView(context: Context, d: Float) : View(context) {
    var art: CardArt? = null
        set(value) {
            field = value
            invalidate()
        }
    var focused = false
        set(value) {
            field = value
            invalidate()
        }
    var settled = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dst = RectF()

    init {
        elevation = 2 * d
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, CARD_RADIUS * d)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        val current = art ?: return
        dst.set(0f, 0f, width.toFloat(), height.toFloat())
        when {
            !focused -> canvas.drawBitmap(current.rest, null, dst, paint)
            settled -> {
                canvas.drawBitmap(current.live, null, dst, paint)
                current.drawOverlays(canvas, true)
            }
            else -> canvas.drawBitmap(current.focus, null, dst, paint)
        }
    }
}

class CardFrame(context: Context, d: Float, margin: Int, val card: RowCardView, width: Int, height: Int) :
    ZoomFrame(context, d, margin, card, width, height, CARD_RADIUS * d, CARD_ZOOM, CARD_LIFT, CARD_PRESSED) {
    private val settle = Runnable { card.settled = true }

    override fun onFocusShown(on: Boolean) {
        removeCallbacks(settle)
        card.focused = on
        card.settled = false
    }

    override fun onFocusProgress(t: Float) {
        if (focused && t >= 1f) postDelayed(settle, SETTLE_DELAY_MS)
    }
}
