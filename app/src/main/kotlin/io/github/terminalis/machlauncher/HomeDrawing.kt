package io.github.terminalis.machlauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.view.animation.PathInterpolator
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

object Ease {
    val out = PathInterpolator(0.0f, 0.0f, 0.58f, 1.0f)
    val outCubic = PathInterpolator(0.215f, 0.61f, 0.355f, 1.0f)
    val inCubic = PathInterpolator(0.55f, 0.055f, 0.675f, 0.19f)
    val inOut = PathInterpolator(0.42f, 0.0f, 0.58f, 1.0f)
    val inOutCubic = PathInterpolator(0.645f, 0.045f, 0.355f, 1.0f)
    val fastOutSlowIn = PathInterpolator(0.4f, 0.0f, 0.2f, 1.0f)
}

fun blurPx(logicalBlur: Float, density: Float): Float {
    if (logicalBlur <= 0f) return 0f
    val sigmaPx = (logicalBlur * 0.57735f + 0.5f) * density
    return (sigmaPx - 0.5f) / 0.57735f
}

fun lerpColor(a: Int, b: Int, t: Float): Int = Color.argb(
    (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * t).toInt(),
    (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
    (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
    (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt(),
)

fun drawCover(canvas: Canvas, source: Bitmap, dst: RectF, paint: Paint) {
    val scale = max(dst.width() / source.width, dst.height() / source.height)
    val srcW = dst.width() / scale
    val srcH = dst.height() / scale
    val left = (source.width - srcW) / 2f
    val top = (source.height - srcH) / 2f
    canvas.drawBitmap(
        source,
        Rect(left.roundToInt(), top.roundToInt(), (left + srcW).roundToInt(), (top + srcH).roundToInt()),
        dst,
        paint,
    )
}

fun roundedCover(source: Bitmap, width: Int, height: Int, radius: Float): Bitmap {
    val cover = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    drawCover(Canvas(cover), source, RectF(0f, 0f, width.toFloat(), height.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
    val rounded = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = BitmapShader(cover, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
    Canvas(rounded).drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, paint)
    rounded.prepareToDraw()
    return rounded
}

class BakedShadow(width: Int, height: Int, radius: Float, blurDp: Float, d: Float) {
    private val blur = blurPx(blurDp, d)
    private val pad = ceil(blur * 1.8).toInt()
    private val bitmap = Bitmap.createBitmap(width + 2 * pad, height + 2 * pad, Bitmap.Config.ALPHA_8).also {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
        }
        Canvas(it).drawRoundRect(pad.toFloat(), pad.toFloat(), (pad + width).toFloat(), (pad + height).toFloat(), radius, radius, paint)
        it.prepareToDraw()
    }
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    fun draw(canvas: Canvas, left: Float, top: Float, color: Int) {
        paint.color = color
        canvas.drawBitmap(bitmap, left - pad, top - pad, paint)
    }

    companion object {
        private val cache = HashMap<List<Int>, BakedShadow>()

        fun focus(width: Int, height: Int, radius: Float, d: Float) =
            cache.getOrPut(listOf(width, height, radius.toInt())) { BakedShadow(width, height, radius, 28f, d) }
    }
}

class ImageLayerView(context: Context, bitmap: Bitmap) : View(context) {
    var bitmap = bitmap
        set(value) {
            field = value
            invalidate()
        }
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dst = RectF()

    override fun hasOverlappingRendering() = false

    override fun onDraw(canvas: Canvas) {
        dst.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawBitmap(bitmap, null, dst, paint)
    }
}
