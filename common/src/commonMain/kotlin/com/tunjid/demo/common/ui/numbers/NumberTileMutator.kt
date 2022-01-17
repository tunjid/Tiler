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

import com.tunjid.mutator.Mutation
import com.tunjid.mutator.Mutator
import com.tunjid.mutator.coroutines.stateFlowMutator
import com.tunjid.mutator.coroutines.toMutationStream
import com.tunjid.tiler.Tile
import com.tunjid.tiler.Tile.Input
import com.tunjid.tiler.tiledMap
import com.tunjid.tiler.toTiledMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.scan

const val GridSize = 5

data class State(
    val activePages: List<Int> = listOf(),
    val chunkedItems: List<List<Item>> = listOf()
)

sealed class Item(open val page: Int) {
    data class Tile(val numberTile: NumberTile) : Item(numberTile.page)
    data class Header(override val page: Int) : Item(page)

    val key get() = when(this) {
        is Tile -> "tile-${numberTile.number}"
        is Header -> "header-$page"
    }
}

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
                is Action.Load -> action.flow.loadMutations()
            }
        }
    }
)

private fun Flow<Action.Load>.loadMutations(): Flow<Mutation<State>> = merge(
    toNumberedTiles()
        .map { pagesToTiles ->
            Mutation {
                val chunked: List<List<Item>> = pagesToTiles.flatMap { (page, numberTiles) ->
                    listOf(listOf(Item.Header(page = page))) + numberTiles.map(Item::Tile)
                        .chunked(GridSize)
                }
                copy(chunkedItems = chunked)
            }
        },
    pageChanges()
        .map { (_, activePages) ->
            Mutation { copy(activePages = activePages) }
        }
)

private fun numberTiler(): (Flow<Input.Map<Int, List<NumberTile>>>) -> Flow<Map<Int, List<NumberTile>>> =
    tiledMap(
        limiter = Tile.Limiter.Map { pages -> pages.size > 4 },
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

private fun Flow<Action.Load>.toNumberedTiles(): Flow<Map<Int, List<NumberTile>>> =
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
        .toTiledMap(numberTiler())

private fun Flow<Action.Load>.pageChanges(): Flow<Pair<List<Int>, List<Int>>> =
    map { (page) -> listOf(page - 1, page, page + 1).filter { it >= 0 } }
        .scan(listOf<Int>() to listOf<Int>()) { pair, new ->
            pair.copy(
                first = pair.second,
                second = new
            )
        }

