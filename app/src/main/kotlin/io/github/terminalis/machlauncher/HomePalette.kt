package io.github.terminalis.machlauncher

class TextShadow(val color: Int, val dx: Float, val dy: Float, val blurRadius: Float)

val HOME_TEXT_SHADOWS = listOf(TextShadow(0x8A000000.toInt(), 0f, 2f, 4f))

const val FOCUSED_GLYPH_COLOR = 0xFF1C1C1E.toInt()
