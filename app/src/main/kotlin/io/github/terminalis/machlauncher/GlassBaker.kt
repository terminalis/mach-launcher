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

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val GLASS_WIDTH = 480
private const val GLASS_BLUR = 15.0
private const val GRID_BLUR = 10.0

private val GLASS_SATURATION = floatArrayOf(
    1.62992f, -0.57216f, -0.05776f, 0f, 0f,
    -0.17008f, 1.22784f, -0.05776f, 0f, 0f,
    -0.17008f, -0.57216f, 1.74224f, 0f, 0f,
    0f, 0f, 0f, 1f, 0f,
)

class HeroTextures(val hero: Bitmap, val dockBlur: Bitmap, val gridBlur: Bitmap)

class GlassBaker(private val screenWidth: Int, private val screenHeight: Int, density: Float) {
    private val filterPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val glassHeight = GLASS_WIDTH * screenHeight / screenWidth
    private val blurScale = GLASS_WIDTH / (screenWidth / density).toDouble()

    fun decode(bytes: ByteArray): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val scale = min(1.0, max(screenWidth.toDouble() / bounds.outWidth, screenHeight.toDouble() / bounds.outHeight))
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val w = (bounds.outWidth * scale).roundToInt()
        val h = (bounds.outHeight * scale).roundToInt()
        return if (w == decoded.width && h == decoded.height) decoded else Bitmap.createScaledBitmap(decoded, w, h, true)
    }

    fun screenCover(source: Bitmap): Bitmap {
        if (source.width == screenWidth && source.height == screenHeight) return source
        val cover = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888)
        drawCover(Canvas(cover), source, RectF(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat()), filterPaint)
        return cover
    }

    fun gradient(gradient: BackgroundGradient): Bitmap {
        val w = screenWidth.toFloat()
        val h = screenHeight.toFloat()
        val bitmap = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888)
        val shader = LinearGradient(0f, h / 2f, w, h / 2f, gradient.from, gradient.to, Shader.TileMode.CLAMP)
        shader.setLocalMatrix(Matrix().apply { setRotate(Math.toDegrees(gradient.rotation.toDouble()).toFloat(), w / 2f, h / 2f) })
        Canvas(bitmap).drawRect(0f, 0f, w, h, Paint(Paint.DITHER_FLAG).apply { this.shader = shader })
        return bitmap
    }

    private fun paintScrim(canvas: Canvas, w: Float, h: Float) {
        val vertical = Paint(Paint.DITHER_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, h,
                intArrayOf(0x59000000, 0x00000000, 0x80000000.toInt(), 0xE0000000.toInt()),
                floatArrayOf(0f, 0.25f, 0.65f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, w, h, vertical)
        val horizontal = Paint(Paint.DITHER_FLAG).apply {
            shader = LinearGradient(
                0f, h / 2f, w, h / 2f,
                intArrayOf(0xA6000000.toInt(), 0x40000000, 0x00000000),
                floatArrayOf(0f, 0.35f, 0.7f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, w, h, horizontal)
    }

    fun bakeHero(source: Bitmap): HeroTextures {
        val hero = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(hero)
        drawCover(canvas, source, RectF(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat()), filterPaint)
        paintScrim(canvas, screenWidth.toFloat(), screenHeight.toFloat())
        val small = Bitmap.createScaledBitmap(hero, GLASS_WIDTH, glassHeight, true)
        val dock = blur(small, GLASS_BLUR * blurScale, GLASS_SATURATION)
        val grid = blur(small, GRID_BLUR * blurScale, null)
        hero.prepareToDraw()
        dock.prepareToDraw()
        grid.prepareToDraw()
        return HeroTextures(hero, dock, grid)
    }

    private fun small(source: Bitmap): Bitmap {
        val small = Bitmap.createBitmap(GLASS_WIDTH, glassHeight, Bitmap.Config.ARGB_8888)
        drawCover(Canvas(small), source, RectF(0f, 0f, GLASS_WIDTH.toFloat(), glassHeight.toFloat()), filterPaint)
        return small
    }

    fun glassFrom(source: Bitmap): Bitmap = blur(small(source), GLASS_BLUR * blurScale, GLASS_SATURATION).also { it.prepareToDraw() }

    fun gridFrom(source: Bitmap): Bitmap = blur(small(source), GRID_BLUR * blurScale, null).also { it.prepareToDraw() }

    private fun boxesForGauss(sigma: Double, n: Int): IntArray {
        val wIdeal = sqrt(12 * sigma * sigma / n + 1)
        var wl = floor(wIdeal).toInt()
        if (wl % 2 == 0) wl--
        val wu = wl + 2
        val mIdeal = (12 * sigma * sigma - n * wl * wl - 4 * n * wl - 3 * n) / (-4 * wl - 4)
        val m = mIdeal.roundToInt()
        return IntArray(n) { if (it < m) wl else wu }
    }

    private fun blur(source: Bitmap, sigma: Double, matrix: FloatArray?): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        val channels = Array(4) { IntArray(w * h) }
        for (i in pixels.indices) {
            val p = pixels[i]
            channels[0][i] = (p ushr 24) and 0xFF
            channels[1][i] = (p ushr 16) and 0xFF
            channels[2][i] = (p ushr 8) and 0xFF
            channels[3][i] = p and 0xFF
        }
        val scratch = IntArray(w * h)
        for (box in boxesForGauss(sigma, 3)) {
            val r = (box - 1) / 2
            for (c in channels) {
                boxHorizontal(c, scratch, w, h, r)
                boxVertical(scratch, c, w, h, r)
            }
        }
        for (i in pixels.indices) {
            val a = channels[0][i]
            var red = channels[1][i].toFloat()
            var green = channels[2][i].toFloat()
            var blue = channels[3][i].toFloat()
            if (matrix != null) {
                val r0 = matrix[0] * red + matrix[1] * green + matrix[2] * blue + matrix[4]
                val g0 = matrix[5] * red + matrix[6] * green + matrix[7] * blue + matrix[9]
                val b0 = matrix[10] * red + matrix[11] * green + matrix[12] * blue + matrix[14]
                red = r0
                green = g0
                blue = b0
            }
            pixels[i] = (a shl 24) or
                (red.roundToInt().coerceIn(0, 255) shl 16) or
                (green.roundToInt().coerceIn(0, 255) shl 8) or
                blue.roundToInt().coerceIn(0, 255)
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun boxHorizontal(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        for (y in 0 until h) {
            val row = y * w
            var sum = 0
            for (k in -r..r) sum += src[row + k.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                dst[row + x] = (sum + div / 2) / div
                sum += src[row + min(x + r + 1, w - 1)] - src[row + max(x - r, 0)]
            }
        }
    }

    private fun boxVertical(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        for (x in 0 until w) {
            var sum = 0
            for (k in -r..r) sum += src[k.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                dst[y * w + x] = (sum + div / 2) / div
                sum += src[min(y + r + 1, h - 1) * w + x] - src[max(y - r, 0) * w + x]
            }
        }
    }
}
