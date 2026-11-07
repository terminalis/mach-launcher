package io.github.terminalis.machlauncher

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout

interface HomeFocusable {
    fun setFocusState(on: Boolean)
    fun press(open: () -> Unit)
}

private const val FOCUS_MS = 180L
private const val OPEN_AFTER_MS = 150L
private const val RELEASE_AFTER_MS = 500L
private const val SHADOW_COLOR = 0x99000000.toInt()

open class ZoomFrame(
    context: Context,
    protected val d: Float,
    private val margin: Int,
    contentView: View,
    private val contentWidth: Int,
    private val contentHeight: Int,
    radius: Float,
    zoom: Float,
    private val lift: Float,
    private val pressedScale: Float,
) : FrameLayout(context), HomeFocusable {
    private val shadow = BakedShadow.focus(contentWidth, contentHeight, radius, d)
    protected var zoom = zoom
        set(value) {
            field = value
            animateScale()
        }
    private var scaleAnimator: ValueAnimator? = null
    private var focusAnimator: ValueAnimator? = null
    protected var focusT = 0f
        private set
    protected var focused = false
        private set
    private var pressed = false

    init {
        setWillNotDraw(false)
        addView(contentView, LayoutParams(contentWidth, contentHeight).apply {
            leftMargin = margin
            topMargin = margin
        })
    }

    protected open fun onFocusShown(on: Boolean) = Unit

    protected open fun onFocusProgress(t: Float) = Unit

    protected open fun onPressShown(on: Boolean) = Unit

    private fun animateScale() {
        val fromScale = scaleX
        val fromLift = translationY
        val toScale = if (pressed) pressedScale else if (focused) zoom else 1f
        val toLift = if (focused && !pressed) -lift * d else 0f
        scaleAnimator?.cancel()
        scaleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = FOCUS_MS
            interpolator = Ease.outCubic
            addUpdateListener {
                val t = it.animatedValue as Float
                val scale = fromScale + (toScale - fromScale) * t
                scaleX = scale
                scaleY = scale
                translationY = fromLift + (toLift - fromLift) * t
            }
            start()
        }
    }

    override fun setFocusState(on: Boolean) {
        focused = on
        onFocusShown(on)
        animateScale()
        focusAnimator?.cancel()
        focusAnimator = ValueAnimator.ofFloat(focusT, if (on) 1f else 0f).apply {
            duration = FOCUS_MS
            interpolator = Ease.outCubic
            addUpdateListener {
                focusT = it.animatedValue as Float
                onFocusProgress(focusT)
                invalidate()
            }
            start()
        }
    }

    override fun press(open: () -> Unit) {
        if (pressed) return
        pressed = true
        onPressShown(true)
        animateScale()
        postDelayed({
            open()
            postDelayed({
                pressed = false
                onPressShown(false)
                animateScale()
            }, RELEASE_AFTER_MS)
        }, OPEN_AFTER_MS)
    }

    override fun onDraw(canvas: Canvas) {
        if (focusT <= 0f) return
        val alpha = (0x99 * focusT).toInt() shl 24
        shadow.draw(canvas, margin.toFloat(), margin + 14 * d * focusT, alpha or (SHADOW_COLOR and 0xFFFFFF))
    }
}
