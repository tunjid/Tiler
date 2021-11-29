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

package com.tunjid.tyler

import android.graphics.Color
import com.tunjid.mutator.Mutation
import com.tunjid.mutator.Mutator
import com.tunjid.mutator.coroutines.stateFlowMutator
import com.tunjid.mutator.coroutines.toMutationStream
import com.tunjid.tiler.Tile
import com.tunjid.tiler.flattenWith
import com.tunjid.tiler.tiledList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan

data class State(
    val numbers: List<NumberTile> = listOf()
)

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
    transform = {
        it.toMutationStream {
            when (val type: Action = type()) {
                is Action.Load -> {
                    type.flow
                        .map { (page) -> listOf(page - 1, page, page + 1) }
                        .scan(listOf<Int>() to listOf<Int>()) { pair, new ->
                            pair.copy(
                                first = pair.second,
                                second = new
                            )
                        }
                        .flatMapLatest { (oldPages, newPages) ->
                            oldPages
                                .filterNot { newPages.contains(it) }
                                .map { Tile.Request.Off<Int, List<NumberTile>>(it) }
                                .plus(newPages.map { Tile.Request.On(it) })
                                .asFlow()

                        }
                        .flattenWith(numberTiler())

                        .map { items ->
                            Mutation { copy(numbers = items.flatten()) }
                        }
                }
            }
        }
    }
)

fun numberTiler() = tiledList(
    flattener = Tile.Flattener.PivotSorted(
        comparator = Int::compareTo,
        limiter = { pages -> pages.size > 4 }
    ),
    fetcher = { page: Int ->
        val start = page * 50
        flowOf(
            start.until(start + 50)
                .map { number ->
                    NumberTile(
                        number = number,
                        color = Color.BLACK,
                        page = page
                    )
                }
        )
    }
)