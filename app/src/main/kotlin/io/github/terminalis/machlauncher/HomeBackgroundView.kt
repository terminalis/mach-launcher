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

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout

private const val SWAP_MS = 1500L

private class LayerStack(context: Context, durationMs: Float) : FrameLayout(context) {
    private val layers = HashMap<FadeSwitcher<Bitmap>.Entry, ImageLayerView>()
    val switcher: FadeSwitcher<Bitmap> = FadeSwitcher(durationMs) { sync() }

    private fun sync() {
        val live = switcher.entries
        val iterator = layers.entries.iterator()
        while (iterator.hasNext()) {
            val (entry, view) = iterator.next()
            if (entry !in live) {
                removeView(view)
                iterator.remove()
            }
        }
        live.forEachIndexed { i, entry ->
            val view = layers.getOrPut(entry) {
                ImageLayerView(context, entry.item).also {
                    addView(it, i, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
                }
            }
            view.alpha = entry.opacity
        }
    }
}

class HomeBackgroundView(context: Context) : FrameLayout(context) {
    private val underBlur = FrameLayout(context)
    private val wallpaper = LayerStack(context, SWAP_MS.toFloat())
    private val hero = LayerStack(context, 600f).apply {
        alpha = 0f
        visibility = View.GONE
    }
    private var blur: ImageLayerView? = null
    private var blurShown = false
    private var blurAnimator: ValueAnimator? = null
    private var swapAnimator: ValueAnimator? = null
    private var heroShown = false

    init {
        val full = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        underBlur.addView(wallpaper, full)
        underBlur.addView(hero, LayoutParams(full))
        addView(underBlur, LayoutParams(full))
    }

    fun showWallpaper(bitmap: Bitmap) = wallpaper.switcher.switchTo(bitmap)

    fun showHero(bitmap: Bitmap?) {
        if (bitmap != null) hero.switcher.switchTo(bitmap)
        val show = bitmap != null
        if (show == heroShown) return
        heroShown = show
        wallpaper.visibility = View.VISIBLE
        hero.visibility = View.VISIBLE
        swapAnimator?.cancel()
        swapAnimator = ValueAnimator.ofFloat(hero.alpha, if (show) 1f else 0f).apply {
            duration = SWAP_MS
            interpolator = LinearInterpolator()
            addUpdateListener {
                val t = it.animatedValue as Float
                hero.alpha = t
                wallpaper.alpha = 1f - t
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (heroShown == show) (if (show) wallpaper else hero).visibility = View.GONE
                }
            })
            start()
        }
    }

    fun setBlur(bitmap: Bitmap) {
        val current = blur
        if (current != null) {
            current.bitmap = bitmap
            return
        }
        blur = ImageLayerView(context, bitmap).also {
            it.alpha = if (blurShown) 1f else 0f
            addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
        if (blurShown) underBlur.visibility = View.GONE
    }

    fun setBlurShown(shown: Boolean) {
        if (shown == blurShown) return
        blurShown = shown
        val layer = blur ?: return
        blurAnimator?.cancel()
        underBlur.visibility = View.VISIBLE
        blurAnimator = ValueAnimator.ofFloat(layer.alpha, if (shown) 1f else 0f).apply {
            duration = 250
            interpolator = Ease.inOut
            addUpdateListener { layer.alpha = it.animatedValue as Float }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (blurShown) underBlur.visibility = View.GONE
                }
            })
            start()
        }
    }
}
