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

import java.util.IdentityHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HeroBaker<T : Any>(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val bake: (ByteArray) -> T,
    private val onChanged: (T?) -> Unit,
) {
    private val baked = IdentityHashMap<ByteArray, T>()
    private var active: ByteArray? = null
    private var posters: List<ByteArray> = emptyList()
    private var current: T? = null
    private var baking = false

    fun update(active: ByteArray?, posters: List<ByteArray>) {
        this.active = active
        this.posters = posters
        baked.keys.retainAll { wanted(it) }
        showActive()
        if (baking) return
        baking = true
        scope.launch {
            while (true) {
                val bytes = nextBake() ?: break
                val textures = withContext(dispatcher) { bake(bytes) }
                if (wanted(bytes)) {
                    baked[bytes] = textures
                    showActive()
                }
            }
            baking = false
        }
    }

    private fun wanted(bytes: ByteArray) = bytes === active || posters.any { it === bytes }

    private fun nextBake(): ByteArray? {
        val first = active
        if (first != null && !baked.containsKey(first)) return first
        return posters.firstOrNull { !baked.containsKey(it) }
    }

    private fun showActive() {
        val first = active
        val next = if (first == null) null else baked[first] ?: current
        if (next === current) return
        current = next
        onChanged(next)
    }
}
