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

const val APP_CARD_ASPECT_RATIO = 16f / 9f
const val APP_CARD_HORIZONTAL_PADDING = 12f
const val APP_CARD_VERTICAL_PADDING = 9f
const val LAUNCHER_SECTION_HORIZONTAL_PADDING = 24f
const val APP_NAME_LABEL_HEIGHT = 24f

fun appCardRowExtentFromContentWidth(contentWidth: Float): Float = contentWidth / HOME_COLUMNS / APP_CARD_ASPECT_RATIO

fun appCardGridAspectRatio(crossAxisExtent: Float, columnsCount: Int): Float {
    val cellWidth = crossAxisExtent / columnsCount
    return cellWidth / (cellWidth / APP_CARD_ASPECT_RATIO + APP_NAME_LABEL_HEIGHT)
}
