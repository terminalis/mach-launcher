package io.github.terminalis.machlauncher

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Canvas
import android.graphics.drawable.GradientDrawable
import android.text.TextPaint
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

const val BANNER_MARGIN = 4f
private const val BANNER_FILL = 0xFF171D1E.toInt()
private const val BANNER_FOCUS = 0xFF2F3435.toInt()

class BannerButton(
    context: Context,
    private val d: Float,
    private val glyph: String,
    private val label: String,
    private val endPadding: Float,
    fonts: PanelFonts,
) : View(context), HomeFocusable {
    private var focused = false
    var highlights = false
        set(value) {
            field = value
            invalidate()
        }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BANNER_FOCUS }
    private val glyphPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = fonts.icons
        textSize = 18 * d
        color = Color.WHITE
    }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = fonts.medium
        textSize = 14 * d
        letterSpacing = 1.25f / 14f
        color = Color.WHITE
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(((16 + 18 + 8 + endPadding) * d + textPaint.measureText(label)).toInt(), (48 * d).toInt())
    }

    override fun setFocusState(on: Boolean) {
        focused = on
        invalidate()
    }

    override fun press(open: () -> Unit) = open()

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()
        val inset = 4 * d
        if (focused && highlights) canvas.drawRoundRect(0f, inset, width.toFloat(), h - inset, 20 * d, 20 * d, fill)
        val gm = glyphPaint.fontMetrics
        canvas.drawText(glyph, 16 * d, h / 2f - (gm.ascent + gm.descent) / 2f, glyphPaint)
        val tm = textPaint.fontMetrics
        canvas.drawText(label, 16 * d + 18 * d + 8 * d, h / 2f - (tm.ascent + tm.descent) / 2f, textPaint)
    }
}

class ListingsBanner(context: Context, private val d: Float, private val fonts: PanelFonts) : LinearLayout(context) {
    val grant = BannerButton(context, d, Glyphs.LOCK_OPEN, AppStrings.grantTvListings, 24f, fonts)
    val check = BannerButton(context, d, Glyphs.REFRESH, AppStrings.checkAgain, 16f, fonts)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(px(20f), px(20f), px(20f), px(20f))
        background = GradientDrawable().apply {
            setColor(BANNER_FILL)
            cornerRadius = 12 * d
        }
        addView(TextView(context).apply {
            text = Glyphs.TV
            typeface = fonts.icons
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, 36 * d)
        })
        val text = LinearLayout(context).apply { orientation = VERTICAL }
        text.addView(TextView(context).apply {
            this.text = AppStrings.tvListingsPermissionTitle
            typeface = fonts.medium
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, 16 * d)
            letterSpacing = 0.15f / 16f
            includeFontPadding = false
        })
        text.addView(TextView(context).apply {
            this.text = AppStrings.tvListingsPermissionBody
            typeface = fonts.regular
            setTextColor(SMALL_TEXT_COLOR)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, 12 * d)
            letterSpacing = SMALL_SPACING
            includeFontPadding = false
        }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = px(4f) })
        addView(text, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply { setMargins(px(16f), 0, px(16f), 0) })
        val buttons = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.END
        }
        buttons.addView(grant, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        buttons.addView(check, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = px(8f) })
        addView(buttons)
    }

    private fun px(dp: Float) = (dp * d).roundToInt()

    fun showFocus() {
        grant.highlights = true
        check.highlights = true
    }
}
