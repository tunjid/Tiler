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

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

fun Int.pageRange(itemsPerPage: Int): IntRange {
    val start = this * itemsPerPage
    return start.until(start + itemsPerPage)
}

fun PageQuery.colorShiftingTiles(itemsPerPage: Int, isDark: Boolean) =
    percentageAndIndex().map { (percentage, count) ->
        page.pageRange(itemsPerPage).mapIndexed { index, number ->
            NumberTile(
                number = number,
                color = interpolateColors(
                    fraction = percentage,
                    startValue = MutedColors.colorAt(
                        isDark = isDark,
                        index = index + count
                    ),
                    endValue = MutedColors.colorAt(
                        isDark = isDark,
                        index = index + count + 1
                    )
                ),
            )
        }
    }
        .map { if (isAscending) it else it.asReversed() }

private fun percentageAndIndex(
    changeDelayMillis: Long = 500L
): Flow<Pair<Float, Int>> = flow {
    var percentage = 0f
    var index = 0

    while (true) {
        percentage += 0.05f
        if (percentage > 1f) {
            percentage = 0f
            index++
        }

        emit(percentage to index)
        delay(changeDelayMillis)
    }
}

