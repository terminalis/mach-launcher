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
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Date
import kotlin.math.roundToInt

private const val BUTTON_ZOOM = 1.08f
private val STATUS_GLYPHS = listOf("", "", "")

class StatusButtonView(context: Context, private val d: Float, glyph: String, icons: Typeface) : View(context), HomeFocusable {
    private val light = bakeGlyph(glyph, 20 * d * BUTTON_ZOOM, icons, Color.WHITE, HOME_TEXT_SHADOWS.first(), d)
    private val dark = bakeGlyph(glyph, 20 * d * BUTTON_ZOOM, icons, FOCUSED_GLYPH_COLOR, null, d)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val image = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dst = RectF()
    private var lit = 0f
    private var litAnimator: ValueAnimator? = null
    private var scaleAnimator: ValueAnimator? = null
    private var focused = false

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension((32 * d).toInt(), (28 * d).toInt())
    }

    private fun animateScale(to: Float) {
        scaleAnimator?.cancel()
        scaleAnimator = ValueAnimator.ofFloat(scaleX, to).apply {
            duration = 150
            interpolator = Ease.fastOutSlowIn
            addUpdateListener {
                scaleX = it.animatedValue as Float
                scaleY = it.animatedValue as Float
            }
            start()
        }
    }

    override fun setFocusState(on: Boolean) {
        focused = on
        animateScale(if (on) BUTTON_ZOOM else 1f)
        litAnimator?.cancel()
        litAnimator = ValueAnimator.ofFloat(lit, if (on) 1f else 0f).apply {
            duration = 150
            interpolator = Ease.outCubic
            addUpdateListener {
                lit = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun press(open: () -> Unit) {
        animateScale(0.92f)
        open()
        postDelayed({ animateScale(if (focused) BUTTON_ZOOM else 1f) }, 150)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (lit > 0f) {
            fill.color = Color.argb((lit * 255).toInt(), 255, 255, 255)
            canvas.drawRoundRect(0f, 0f, w, h, h / 2f, h / 2f, fill)
        }
        val size = light.width / BUTTON_ZOOM
        dst.set((w - size) / 2f, (h - size) / 2f, (w + size) / 2f, (h + size) / 2f)
        image.alpha = ((1f - lit) * 255).toInt()
        canvas.drawBitmap(light, null, dst, image)
        image.alpha = (lit * 255).toInt()
        canvas.drawBitmap(dark, null, dst, image)
    }
}

class StatusBarView(
    context: Context,
    private val d: Float,
    screenWidth: Int,
    screenHeight: Int,
    regular: Typeface,
    bold: Typeface,
    icons: Typeface,
) : FrameLayout(context) {
    val clockPanel = GlassPanel(context, d, screenWidth, screenHeight)
    val buttonsPanel = GlassPanel(context, d, screenWidth, screenHeight)
    val buttons = STATUS_GLYPHS.map { StatusButtonView(context, d, it, icons) }
    private val date = text(regular)
    private val clock = text(bold)

    init {
        setPadding(px(24f), 0, px(24f), 0)
        clipChildren = false
        clipToPadding = false
        clockPanel.setPadding(px(18f), px(10f), px(18f), px(10f))
        val clockRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        clockRow.addView(date)
        clockRow.addView(clock, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = px(16f)
        })
        clockPanel.addView(clockRow)
        addView(clockPanel, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.START or Gravity.CENTER_VERTICAL))
        buttonsPanel.setPadding(px(8f), px(4f), px(8f), px(4f))
        val buttonRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.forEachIndexed { i, button ->
            buttonRow.addView(button, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                if (i > 0) leftMargin = px(8f)
            })
        }
        buttonsPanel.addView(buttonRow)
        addView(buttonsPanel, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.CENTER_VERTICAL))
        tick(System.currentTimeMillis())
    }

    private fun px(dp: Float) = (dp * d).roundToInt()

    private fun text(face: Typeface) = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, 20 * d)
        typeface = face
        setTextColor(Color.WHITE)
        letterSpacing = 0.15f / 20f
        includeFontPadding = false
        isSingleLine = true
        val shadow = HOME_TEXT_SHADOWS.first()
        setShadowLayer(blurPx(shadow.blurRadius, d), shadow.dx * d, shadow.dy * d, shadow.color)
    }

    fun tick(now: Long) {
        val time = Date(now)
        date.text = statusDate(time)
        clock.text = statusClock(time, DateFormat.is24HourFormat(context))
    }
}
