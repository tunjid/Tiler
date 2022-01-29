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

package com.tunjid.demo.common.ui.numbers

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.pow
import kotlin.math.roundToInt

fun argbFlow(changeDelayMillis: Long = 500L): Flow<Int> = flow {
    var fraction = 0f
    var colorIndex = 0
    val colorsSize = colors.size

    while (true) {
        fraction += 0.05f
        if (fraction > 1f) {
            fraction = 0f
            colorIndex++
        }

        emit(
            interpolateColors(
                fraction = fraction,
                startValue = colors[colorIndex % colorsSize],
                endValue = colors[(colorIndex + 1) % colorsSize]
            )
        )
        delay(changeDelayMillis)
    }
}

private fun interpolateColors(fraction: Float, startValue: Int, endValue: Int): Int {
    val startA = (startValue shr 24 and 0xff) / 255.0f
    var startR = (startValue shr 16 and 0xff) / 255.0f
    var startG = (startValue shr 8 and 0xff) / 255.0f
    var startB = (startValue and 0xff) / 255.0f
    val endA = (endValue shr 24 and 0xff) / 255.0f
    var endR = (endValue shr 16 and 0xff) / 255.0f
    var endG = (endValue shr 8 and 0xff) / 255.0f
    var endB = (endValue and 0xff) / 255.0f

    // convert from sRGB to linear
    startR = startR.toDouble().pow(2.2).toFloat()
    startG = startG.toDouble().pow(2.2).toFloat()
    startB = startB.toDouble().pow(2.2).toFloat()
    endR = endR.toDouble().pow(2.2).toFloat()
    endG = endG.toDouble().pow(2.2).toFloat()
    endB = endB.toDouble().pow(2.2).toFloat()

    // compute the interpolated color in linear space
    var a = startA + fraction * (endA - startA)
    var r = startR + fraction * (endR - startR)
    var g = startG + fraction * (endG - startG)
    var b = startB + fraction * (endB - startB)

    // convert back to sRGB in the [0..255] range
    a = a * 255.0f
    r = r.toDouble().pow(1.0 / 2.2).toFloat() * 255.0f
    g = g.toDouble().pow(1.0 / 2.2).toFloat() * 255.0f
    b = b.toDouble().pow(1.0 / 2.2).toFloat() * 255.0f

    return a.roundToInt() shl 24 or (r.roundToInt() shl 16) or (g.roundToInt() shl 8) or b.roundToInt()
}

private val colors = listOf(
    Color.Black,
    Color.Blue,
    Color.Cyan,
    Color.DarkGray,
    Color.Gray,
    Color.Green,
    Color.LightGray,
    Color.Magenta,
    Color.Red,
    Color.Yellow,
).map(Color::toArgb)
