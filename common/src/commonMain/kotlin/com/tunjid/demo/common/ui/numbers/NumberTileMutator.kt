/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tunjid.demo.common.ui.numbers

import android.graphics.Color
import com.tunjid.demo.common.ui.numbers.Action.Load
import com.tunjid.mutator.Mutation
import com.tunjid.mutator.Mutator
import com.tunjid.mutator.coroutines.stateFlowMutator
import com.tunjid.mutator.coroutines.toMutationStream
import com.tunjid.tiler.Tile
import com.tunjid.tiler.flattenWith
import com.tunjid.tiler.tiledList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.scan
import kotlin.math.pow
import kotlin.math.roundToInt

data class State(
    val activePages: List<Int> = listOf(),
    val numbers: List<NumberTile> = listOf()
)

val State.chunkedTiles get() = numbers.chunked(3)

sealed class Action {
    data class Load(val page: Int) : Action()
}

data class NumberTile(
    val number: Int,
    val color: Int,
    val page: Int
)

fun numberTilesMutator(
    scope: CoroutineScope
): Mutator<Action, StateFlow<State>> = stateFlowMutator(
    scope = scope,
    initialState = State(),
    transform = { actionFlow ->
        actionFlow.toMutationStream {
            when (val action: Action = type()) {
                is Load -> merge(
                    action.flow
                        .toNumberedTiles()
                        .map { items ->
                            Mutation { copy(numbers = items.flatten()) }
                        },
                    action.flow
                        .pageChanges()
                        .map { (_, activePages) ->
                            Mutation { copy(activePages = activePages) }
                        }
                )
            }
        }
    }
)

private fun numberTiler() = tiledList(
    limiter = Tile.Limiter.List { pages -> pages.size > 4 },
    flattener = Tile.Flattener.PivotSorted(comparator = Int::compareTo),
    fetcher = { page: Int ->
        val start = page * 50
        val numbers = start.until(start + 50)
        argbFlow().map { color ->
            numbers.map { number ->
                NumberTile(
                    number = number,
                    color = color,
                    page = page
                )
            }
        }
    }
)

private fun Flow<Load>.toNumberedTiles(): Flow<List<List<NumberTile>>> =
    pageChanges()
        .flatMapLatest { (oldPages, newPages) ->
            // Evict all items 10 pages behind the smallest page in the new request.
            // Their backing flows will stop being collected, and their existing values will be
            // evicted from memory
            val toEvict: List<Tile.Request.Evict<Int, List<NumberTile>>> = (newPages.minOrNull()
                ?.minus(10)
                ?.downTo(0)
                ?.take(10)
                ?: listOf())
                .map { Tile.Request.Evict(it) }

            // Turn off the flows for all old requests that are not in the new request batch
            // The existing emitted values will be kept in memory, but their backing flows
            // will stop being collected
            val toTurnOff: List<Tile.Request.Off<Int, List<NumberTile>>> = oldPages
                .filterNot(newPages::contains)
                .map { Tile.Request.Off(it) }

            // Turn on flows for the requested pages
            val toTurnOn: List<Tile.Request.On<Int, List<NumberTile>>> = newPages
                .map { Tile.Request.On(it) }

            (toEvict + toTurnOff + toTurnOn).asFlow()
        }
        .flattenWith(numberTiler())

private fun Flow<Load>.pageChanges(): Flow<Pair<List<Int>, List<Int>>> =
    map { (page) -> listOf(page - 1, page, page + 1).filter { it >= 0 } }
        .scan(listOf<Int>() to listOf<Int>()) { pair, new ->
            println(new)
            pair.copy(
                first = pair.second,
                second = new
            )
        }

private fun argbFlow(): Flow<Int> = flow {
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
        delay(100)
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
    Color.BLACK,
    Color.BLUE,
    Color.CYAN,
    Color.DKGRAY,
    Color.GRAY,
    Color.GREEN,
    Color.LTGRAY,
    Color.MAGENTA,
    Color.RED,
    Color.YELLOW,
)
