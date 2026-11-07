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

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun statusDate(time: Date): String = SimpleDateFormat("EEE d MMM", Locale.US).format(time)

fun statusClock(time: Date, use24Hour: Boolean): String =
    SimpleDateFormat(if (use24Hour) "HH:mm" else "hh:mm a", Locale.US).format(time)

fun millisToNextMinute(now: Long): Long = 60_000 - now % 60_000
