/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tunjid.demo.common.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.random.Random

object MutedColors {
    private val mutedColors = intArrayOf(
        Color(0xFF2980b9).toArgb(), // Belize Hole
        Color(0xFF2c3e50).toArgb(), // Midnight Blue
        Color(0xFFc0392b).toArgb(), // Pomegranate
        Color(0xFF16a085).toArgb(), // Green Sea
        Color(0xFF7f8c8d).toArgb() // Concrete
    )

    private val darkerMutedColors = intArrayOf(
        Color(0xFF304233).toArgb(),
        Color(0xFF353b45).toArgb(),
        Color(0xFF392e3a).toArgb(),
        Color(0xFF3e2a2a).toArgb(),
        Color(0xFF474747).toArgb()
    )

    fun colorAt(isDark: Boolean, index: Int) = palette(isDark).circular(index)

    fun random(isDark: Boolean): Int = palette(isDark).random()

    private fun palette(isDark: Boolean): IntArray = when (isDark) {
        true -> darkerMutedColors
        else -> mutedColors
    }
}

private fun IntArray.circular(index: Int) = this[index % size]

private fun IntArray.random() = this[(Random.Default.nextInt(size))]