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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.widget.FrameLayout
import kotlin.math.roundToInt

class GlassPanel(
    context: Context,
    private val d: Float,
    private val screenWidth: Int,
    private val screenHeight: Int,
) : FrameLayout(context) {
    private val radius = 24 * d
    private val spread = (2 * d).roundToInt()
    val switcher = FadeSwitcher<Bitmap>(600f) { invalidate() }
    private var shadow: BakedShadow? = null
    private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x80000000.toInt() }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = d
    }
    private val location = IntArray(2)
    private val shaders = HashMap<Bitmap, BitmapShader>()
    private val shaderMatrix = Matrix()
    private val shaderPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    init {
        setWillNotDraw(false)
        clipChildren = false
        clipToPadding = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        shadow = BakedShadow(w + 2 * spread, h + 2 * spread, radius + spread, 20f, d)
        outlinePaint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(0x40FFFFFF, 0x1AFFFFFF, 0x0AFFFFFF),
            floatArrayOf(0f, 0.4f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        shadow?.draw(canvas, -spread.toFloat(), -spread.toFloat(), 0x4D000000)
        getLocationInWindow(location)
        shaders.keys.retainAll(switcher.entries.map { it.item }.toSet())
        for (entry in switcher.entries) {
            val bitmap = entry.item
            val shader = shaders.getOrPut(bitmap) { BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
            shaderMatrix.setScale(screenWidth.toFloat() / bitmap.width, screenHeight.toFloat() / bitmap.height)
            shaderMatrix.postTranslate(-location[0].toFloat(), -location[1].toFloat())
            shader.setLocalMatrix(shaderMatrix)
            shaderPaint.shader = shader
            shaderPaint.alpha = (entry.opacity * 255).toInt()
            canvas.drawRoundRect(0f, 0f, w, h, radius, radius, shaderPaint)
        }
        canvas.drawRoundRect(0f, 0f, w, h, radius, radius, tintPaint)
        val inset = 0.5f * d
        canvas.drawRoundRect(inset, inset, w - inset, h - inset, radius - inset, radius - inset, outlinePaint)
    }
}
