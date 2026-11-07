package io.github.terminalis.machlauncher

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

private const val PLAY_GLYPH = ""
private const val DARK_INK = 0xDD000000.toInt()

class WatchButtonView(
    context: Context,
    private val d: Float,
    private val icons: Typeface,
    private val bold: Typeface,
) : View(context), HomeFocusable {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = d
    }
    private val image = Paint(Paint.FILTER_BITMAP_FLAG)
    private var t = 0f
    private var animator: ValueAnimator? = null
    private var scaleAnimator: ValueAnimator? = null
    private var pressed = false
    private var label = ""
    private var light: Bitmap? = null
    private var dark: Bitmap? = null
    private var restShadow: BakedShadow? = null
    private var focusShadow: BakedShadow? = null

    fun setLabel(text: String) {
        if (text == label) return
        label = text
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = bold
            textSize = 16 * d
            letterSpacing = 0.3f / 16f
        }
        val contentWidth = ceil(24 * d + 8 * d + textPaint.measureText(text)).toInt()
        val height = (48 * d).toInt()
        light = content(text, Color.WHITE, contentWidth, height)
        dark = content(text, DARK_INK, contentWidth, height)
        requestLayout()
    }

    private fun content(text: String, color: Int, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val iconPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = icons
            textSize = 24 * d
            this.color = color
            textAlign = Paint.Align.CENTER
        }
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = bold
            textSize = 16 * d
            letterSpacing = 0.3f / 16f
            this.color = color
        }
        val ifm = iconPaint.fontMetrics
        canvas.drawText(PLAY_GLYPH, 12 * d, height / 2f - (ifm.ascent + ifm.descent) / 2f, iconPaint)
        val tfm = textPaint.fontMetrics
        canvas.drawText(text, 24 * d + 8 * d, height / 2f - (tfm.ascent + tfm.descent) / 2f, textPaint)
        bitmap.prepareToDraw()
        return bitmap
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val content = light?.width ?: 0
        setMeasuredDimension(content + (48 * d).toInt(), (48 * d).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w > 0 && h > 0) {
            restShadow = BakedShadow(w, h, h / 2f, 4f, d)
            focusShadow = BakedShadow(w, h, h / 2f, 8f, d)
        }
    }

    override fun setFocusState(on: Boolean) {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(t, if (on) 1f else 0f).apply {
            duration = 180
            interpolator = Ease.outCubic
            addUpdateListener {
                t = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun press(open: () -> Unit) {
        if (pressed) return
        pressed = true
        animateScale(0.96f)
        postDelayed({
            open()
            postDelayed({
                pressed = false
                animateScale(1f)
            }, 500)
        }, 150)
    }

    private fun animateScale(to: Float) {
        scaleAnimator?.cancel()
        scaleAnimator = ValueAnimator.ofFloat(scaleX, to).apply {
            duration = 180
            interpolator = Ease.outCubic
            addUpdateListener {
                scaleX = it.animatedValue as Float
                scaleY = it.animatedValue as Float
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = h / 2f
        restShadow?.draw(canvas, 0f, 2 * d, Color.argb((0x42 * (1f - t)).toInt(), 0, 0, 0))
        focusShadow?.draw(canvas, 0f, 3 * d, Color.argb((0x61 * t).toInt(), 0, 0, 0))
        fill.color = lerpColor(0x38FFFFFF, Color.WHITE, t)
        canvas.drawRoundRect(0f, 0f, w, h, r, r, fill)
        if (t < 1f) {
            border.color = Color.argb((0x59 * (1f - t)).toInt(), 255, 255, 255)
            val inset = 0.5f * d
            canvas.drawRoundRect(inset, inset, w - inset, h - inset, r - inset, r - inset, border)
        }
        val left = 24 * d
        light?.let {
            image.alpha = ((1f - t) * 255).toInt()
            canvas.drawBitmap(it, left, 0f, image)
        }
        dark?.let {
            image.alpha = (t * 255).toInt()
            canvas.drawBitmap(it, left, 0f, image)
        }
    }
}

class PaginationView(context: Context, private val d: Float) : View(context) {
    private var active = FloatArray(0)
    private var animators = arrayOfNulls<ValueAnimator>(0)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = BakedShadow((18 * d).toInt(), (5 * d).toInt(), 3 * d, 6f, d)

    fun setCount(count: Int, index: Int) {
        if (count == active.size) return
        animators.forEach { it?.cancel() }
        active = FloatArray(count) { if (it == index) 1f else 0f }
        animators = arrayOfNulls(count)
        requestLayout()
        invalidate()
    }

    fun setActive(index: Int) {
        for (i in active.indices) {
            val target = if (i == index) 1f else 0f
            if (active[i] == target && animators[i]?.isRunning != true) continue
            animators[i]?.cancel()
            animators[i] = ValueAnimator.ofFloat(active[i], target).apply {
                duration = 250
                interpolator = Ease.inOut
                addUpdateListener {
                    active[i] = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var w = 0f
        for (a in active) w += 6 * d + (6 + 12 * a) * d
        setMeasuredDimension(w.toInt() + (12 * d).toInt(), (5 * d).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val h = 5 * d
        val r = min(3 * d, h / 2f)
        var x = 0f
        for (a in active) {
            x += 3 * d
            val w = (6 + 12 * a) * d
            if (a > 0f) glow.draw(canvas, x, 0f, Color.argb((0x99 * a).toInt(), 255, 255, 255))
            dotPaint.color = lerpColor(0x61FFFFFF, Color.WHITE, a)
            canvas.drawRoundRect(x, 0f, x + w, h, r, r, dotPaint)
            x += w + 3 * d
        }
    }
}

class HeroProgressView(context: Context, private val d: Float) : View(context) {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x3DFFFFFF }
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val rect = RectF()
    var progress = 0f
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        if (progress <= 0f) return
        val r = 2 * d
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, r, r, track)
        rect.right = width * progress
        canvas.drawRoundRect(rect, r, r, bar)
    }
}

class HeroBlock(
    context: Context,
    private val d: Float,
    regular: Typeface,
    bold: Typeface,
    extraBold: Typeface,
    icons: Typeface,
) : LinearLayout(context) {
    private val chip = text(11f, bold, Color.WHITE, 1.1f).apply {
        setPadding(px(11f), px(5f), px(11f), px(5f))
        background = GradientDrawable().apply {
            setColor(0x29FFFFFF)
            setStroke(px(1f), 0x38FFFFFF)
            cornerRadius = 6 * d
        }
    }
    private val title = text(34f, extraBold, Color.WHITE, -0.4f).apply {
        maxWidth = px(888f)
        ellipsize = TextUtils.TruncateAt.END
        setShadowLayer(blurPx(10f, d), 0f, 2 * d, 0xDD000000.toInt())
    }
    private val description = text(14f, regular, 0xD9FFFFFF.toInt()).apply {
        isSingleLine = false
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        maxWidth = px(720f)
        lineHeight = (14f * 1.35f * d).roundToInt()
        setShadowLayer(blurPx(6f, d), 0f, 1 * d, 0xDD000000.toInt())
    }
    private val progress = HeroProgressView(context, d)
    val watch = WatchButtonView(context, d, icons, bold)
    val pagination = PaginationView(context, d)

    init {
        orientation = VERTICAL
        clipChildren = false
        clipToPadding = false
        setPadding(0, 0, 0, px(8f))
        fun wrap(top: Float = 0f) = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = px(top) }
        addView(chip, wrap())
        addView(title, wrap(10f))
        addView(description, LayoutParams(LayoutParams.WRAP_CONTENT, px(40f)).apply { topMargin = px(6f) })
        addView(progress, LayoutParams(px(140f), px(4f)).apply { topMargin = px(8f) })
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        row.addView(watch)
        row.addView(pagination, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { leftMargin = px(20f) })
        addView(row, wrap(14f))
    }

    private fun px(dp: Float) = (dp * d).roundToInt()

    private fun text(size: Float, face: Typeface, color: Int, spacing: Float = 0f) = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, size * d)
        typeface = face
        setTextColor(color)
        letterSpacing = spacing / size
        includeFontPadding = false
        isSingleLine = true
    }

    fun show(item: Recommendation, index: Int, total: Int) {
        chip.text = item.appName.uppercase(Locale.US)
        title.text = item.title
        description.text = item.description
        progress.progress = item.progressPercent / 100f
        watch.setLabel(if (item.progressPercent > 0) AppStrings.resume else AppStrings.watch)
        pagination.visibility = if (total > 1) View.VISIBLE else View.GONE
        pagination.setCount(total, index)
        pagination.setActive(index)
    }
}
