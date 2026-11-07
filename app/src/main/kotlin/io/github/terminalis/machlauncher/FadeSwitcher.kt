package io.github.terminalis.machlauncher

import android.view.Choreographer

class FadeSwitcher<T>(
    private val durationMs: Float,
    private val onFrame: () -> Unit,
) : Choreographer.FrameCallback {

    enum class Direction { FORWARD, REVERSE }

    inner class Entry(val item: T) {
        var value = 0f
        var direction: Direction? = null
        var curveDirection: Direction? = null

        val opacity: Float
            get() {
                val useReverse = (curveDirection ?: direction) == Direction.REVERSE
                return (if (useReverse) Ease.inCubic else Ease.outCubic).getInterpolation(value)
            }

        fun animate(to: Direction) {
            direction = to
            curveDirection = curveDirection ?: to
        }
    }

    val entries = ArrayList<Entry>()
    private var current: Entry? = null
    private var lastFrameNanos = 0L
    private var ticking = false

    fun show(item: T) {
        val entry = Entry(item)
        entry.value = 1f
        entries.add(entry)
        current = entry
        onFrame()
    }

    fun switchTo(item: T) {
        if (current == null) {
            show(item)
            return
        }
        current?.animate(Direction.REVERSE)
        val entry = Entry(item)
        entry.animate(Direction.FORWARD)
        entries.add(entry)
        current = entry
        if (!ticking) {
            ticking = true
            lastFrameNanos = 0L
            Choreographer.getInstance().postFrameCallback(this)
        }
        onFrame()
    }

    override fun doFrame(frameTimeNanos: Long) {
        val dt = if (lastFrameNanos == 0L) 0f else (frameTimeNanos - lastFrameNanos) / 1_000_000f
        lastFrameNanos = frameTimeNanos
        var animating = false
        val iterator = entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            when (entry.direction) {
                Direction.FORWARD -> {
                    entry.value = (entry.value + dt / durationMs).coerceAtMost(1f)
                    if (entry.value >= 1f) {
                        entry.direction = null
                        entry.curveDirection = null
                    } else {
                        animating = true
                    }
                }
                Direction.REVERSE -> {
                    entry.value = (entry.value - dt / durationMs).coerceAtLeast(0f)
                    if (entry.value <= 0f) {
                        iterator.remove()
                    } else {
                        animating = true
                    }
                }
                null -> {}
            }
        }
        onFrame()
        if (animating) {
            Choreographer.getInstance().postFrameCallback(this)
        } else {
            ticking = false
        }
    }
}
