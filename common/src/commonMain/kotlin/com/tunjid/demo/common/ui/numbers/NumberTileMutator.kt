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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.scan

const val GridSize = 5

sealed class Action {
    data class Load(val page: Int) : Action()
    data class FirstVisibleIndexChanged(val index: Int) : Action()
}

data class State(
    val firstVisibleIndex: Int = -1,
    val activePages: List<Int> = listOf(),
    val chunkedItems: List<List<Item>> = listOf()
)

val State.stickyHeader: Item.Header?
    get() = when (val item = chunkedItems.getOrNull(firstVisibleIndex)?.firstOrNull()) {
        is Item.Tile -> Item.Header(page = item.page, color = item.numberTile.color)
        is Item.Header -> item
        null -> null
    }

sealed class Item(open val page: Int) {
    data class Tile(val numberTile: NumberTile) : Item(numberTile.page)
    data class Header(
        override val page: Int,
        val color: Int,
    ) : Item(page)

    val key
        get() = when (this) {
            is Tile -> "tile-${numberTile.number}"
            is Header -> "header-$page"
        }
}

val Any.isStickyHeaderKey get() = this is String && this.contains("header")

data class NumberTile(
    val number: Int,
    val color: Int,
    val page: Int
)

/**
 * A summary of the loading queries in the app
 */
private data class LoadMetadata(
    val previousQueries: List<Int> = listOf(),
    val currentQueries: List<Int> = listOf(),
    val toEvict: List<Int> = listOf(),
    val inMemory: List<Int> = listOf(),
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
                is Action.FirstVisibleIndexChanged -> action.flow.stickyHeaderMutations()
            }
        }
    }
)

private fun Flow<Action.Load>.loadMutations(): Flow<Mutation<State>> = merge(
    toNumberedTiles()
        .map { pagesToTiles ->
            Mutation {
                val chunked: List<List<Item>> = pagesToTiles.flatMap { (page, numberTiles) ->
                    val color = numberTiles.first().color
                    val header = Item.Header(page = page, color = color)
                    listOf(listOf(header)) + numberTiles.map(Item::Tile).chunked(GridSize)
                }
                copy(chunkedItems = chunked)
            }
        },
    loadMetadata()
        .map {
            Mutation { copy(activePages = it.currentQueries) }
        }
)

private fun Flow<Action.FirstVisibleIndexChanged>.stickyHeaderMutations(): Flow<Mutation<State>> =
    distinctUntilChanged()
        .map { (firstVisibleIndex) ->
            Mutation { copy(firstVisibleIndex = firstVisibleIndex) }
        }

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
    loadMetadata()
        .flatMapLatest { (previousQueries, currentQueries, evictions) ->
            // Turn on flows for the requested pages
            val toTurnOn = currentQueries
                .map { Tile.Request.On<Int, List<NumberTile>>(it) }

            // Turn off the flows for all old requests that are not in the new request batch
            // The existing emitted values will be kept in memory, but their backing flows
            // will stop being collected
            val toTurnOff = previousQueries
                .filterNot { currentQueries.contains(it) }
                .map { Tile.Request.Off<Int, List<NumberTile>>(it) }

            // Evict all items 10 pages behind the smallest page in the new request.
            // Their backing flows will stop being collected, and their existing values will be
            // evicted from memory
            val toEvict = evictions
                .map { Tile.Request.Evict<Int, List<NumberTile>>(it) }

            (toTurnOn + toTurnOff + toEvict).asFlow()
        }
        .toTiledMap(numberTiler())

private fun Flow<Action.Load>.loadMetadata(): Flow<LoadMetadata> =
    distinctUntilChanged()
        .map { (page) ->
            listOf(page - 1, page + 1, page).filter { it >= 0 }
        }
        .scan(LoadMetadata()) { existingQueries, currentQueries ->
            val currentlyInMemory = (existingQueries.inMemory + currentQueries).distinct()
            val toEvict = when (val min = currentQueries.minOrNull()) {
                null -> listOf()
                // Evict items more than 5 offset pages behind the min current query
                else -> currentlyInMemory.filter { min - it > 5 }
            }
            existingQueries.copy(
                previousQueries = existingQueries.currentQueries,
                currentQueries = currentQueries,
                inMemory = currentlyInMemory - toEvict.toSet(),
                toEvict = toEvict
            )
        }
