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

import androidx.compose.foundation.gestures.ScrollableState
import com.tunjid.mutator.Mutation
import com.tunjid.mutator.Mutator
import com.tunjid.mutator.coroutines.stateFlowMutator
import com.tunjid.mutator.coroutines.toMutationStream
import com.tunjid.tiler.Tile
import com.tunjid.tiler.Tile.Input
import com.tunjid.tiler.tiledMap
import com.tunjid.tiler.toTiledMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlin.math.abs

const val GridSize = 5
private const val PageWindow = 3
private val ascendingPageComparator = Comparator<Int>(Int::compareTo)
private val descendingPageComparator = ascendingPageComparator.reversed()

sealed class Action {
    data class Load(val page: Int, val isAscending: Boolean) : Action()
    data class FirstVisibleIndexChanged(val index: Int) : Action()
}

data class State(
    val listStyle: ListStyle<ScrollableState>,
    val isAscending: Boolean = true,
    val currentPage: Int = 0,
    val firstVisibleIndex: Int = -1,
    val loadSummary: String = "",
    val items: List<Item> = listOf()
)

val State.stickyHeader: Item.Header?
    get() = when (val item = items.getOrNull(firstVisibleIndex)) {
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
    val comparator: Comparator<Int>? = null,
    val isAscending: Boolean = false,
)

fun numberTilesMutator(
    scope: CoroutineScope,
    itemsPerPage: Int,
    listStyle: ListStyle<ScrollableState>
): Mutator<Action, StateFlow<State>> = stateFlowMutator(
    scope = scope,
    initialState = State(listStyle = listStyle),
    actionTransform = { actionFlow ->
        actionFlow.toMutationStream {
            when (val action: Action = type()) {
                is Action.Load -> action.flow.loadMutations(
                    scope = scope,
                    itemsPerPage = itemsPerPage
                )
                is Action.FirstVisibleIndexChanged -> action.flow.stickyHeaderMutations()
            }
        }
    }
)

private fun Flow<Action.Load>.loadMutations(
    scope: CoroutineScope,
    itemsPerPage: Int
): Flow<Mutation<State>> = shareIn(
    scope = scope,
    started = SharingStarted.WhileSubscribed(),
    replay = 1
).let { sharedFlow ->
    merge(
        sharedFlow.toNumberedTiles(itemsPerPage)
            .map { pagesToTiles ->
                Mutation {
                    copy(items = pagesToTiles.flatMap { (page, numberTiles) ->
                        val color = numberTiles.first().color
                        val header = Item.Header(page = page, color = color)
                        listOf(header) + when (isAscending) {
                            true -> numberTiles.map(Item::Tile)
                            else -> numberTiles.map(Item::Tile).reversed()
                        }
                    })
                }
            },
        sharedFlow.loadMetadata()
            .map {
                Mutation {
                    copy(
                        isAscending = it.isAscending,
                        // 3 pages are fetched at once, the middle is the current page
                        currentPage = it.currentQueries.startPage(),
                        loadSummary = "Active pages: ${it.currentQueries}\nPages in memory: ${it.inMemory.sorted()}"
                    )
                }
            }
    )
}

private fun Flow<Action.FirstVisibleIndexChanged>.stickyHeaderMutations(): Flow<Mutation<State>> =
    distinctUntilChanged()
        .map { (firstVisibleIndex) ->
            Mutation { copy(firstVisibleIndex = firstVisibleIndex) }
        }

private fun numberTiler(itemsPerPage: Int): (Flow<Input.Map<Int, List<NumberTile>>>) -> Flow<Map<Int, List<NumberTile>>> =
    tiledMap(
        limiter = Tile.Limiter.Map { pages -> pages.size > 4 },
        order = Tile.Order.PivotSorted(comparator = ascendingPageComparator),
        fetcher = { page: Int ->
            val start = page * itemsPerPage
            val numbers = start.until(start + itemsPerPage)
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

private fun Flow<Action.Load>.toNumberedTiles(itemsPerPage: Int): Flow<Map<Int, List<NumberTile>>> =
    loadMetadata()
        .flatMapLatest { (previousQueries, currentQueries, evictions, _, comparator) ->
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

            val comparison = listOfNotNull(
                comparator?.let { Tile.Order.PivotSorted<Int, List<NumberTile>>(it) }
            )

            (toTurnOn + toTurnOff + toEvict + comparison).asFlow()
        }
        .toTiledMap(numberTiler(itemsPerPage))

private fun Flow<Action.Load>.loadMetadata(): Flow<LoadMetadata> =
    distinctUntilChanged()
        .map { (startPage, isAscending) -> isAscending to pagesToLoad(startPage = startPage) }
        .scan(LoadMetadata()) { previousMetadata, (isAscending, currentQueries) ->
            val currentlyInMemory = (previousMetadata.inMemory + currentQueries).distinct()
            val toEvict = when (val min = currentQueries.minOrNull()) {
                null -> listOf()
                // Evict items more than 5 offset pages behind the min current query
                else -> currentlyInMemory.filter { abs(min - it) > 5 }
            }
            previousMetadata.copy(
                isAscending = isAscending,
                previousQueries = previousMetadata.currentQueries,
                currentQueries = currentQueries,
                inMemory = currentlyInMemory - toEvict.toSet(),
                toEvict = toEvict,
                comparator = when (isAscending) {
                    previousMetadata.isAscending -> null
                    else -> if (isAscending) ascendingPageComparator else descendingPageComparator
                }
            )
        }

private fun pagesToLoad(startPage: Int): List<Int> {
    var i = 0
    val result = mutableListOf(startPage)
    while (result.size < PageWindow) {
        i++
        if (result.size < PageWindow && startPage - 1 >= 0) result.add(
            index = 0,
            element = startPage - i
        )
        if (result.size < PageWindow) result.add(
            element = startPage + i
        )
    }
    return result
}

private fun List<Int>.startPage(): Int {
    val isOdd = PageWindow % 2 != 0
    val index = if (isOdd) (PageWindow / 2) + 1 else PageWindow / 2
    return getOrNull(index) ?: 0
}
