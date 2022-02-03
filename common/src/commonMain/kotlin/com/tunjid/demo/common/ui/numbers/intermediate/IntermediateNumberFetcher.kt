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

package com.tunjid.demo.common.ui.numbers.intermediate

import com.tunjid.demo.common.ui.numbers.Item
import com.tunjid.demo.common.ui.numbers.colorShiftingTiles
import com.tunjid.tiler.Tile
import com.tunjid.tiler.tiledList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.max

private const val ConcurrentPages = 5
private const val PagesReturned = 3

private data class LoadMetadata(
    val currentQueries: List<Int> = listOf(),
    val toEvict: List<Int> = listOf(),
)

class IntermediateNumberFetcher(
    private val scope: CoroutineScope,
    itemsPerPage: Int,
    isDark: Boolean,
) {
    private val requests =
        MutableSharedFlow<Tile.Request.On<Int, List<Int>>>()

    /**
     * Tiling function to fetch items for a given page
     */
    private val listTiler: (Flow<Tile.Input.List<Int, List<Item>>>) -> Flow<List<List<Item>>> =
        tiledList(
            order = Tile.Order.PivotSorted(comparator = Int::compareTo),
            limiter = Tile.Limiter.List { it.size > itemsPerPage * PagesReturned },
            fetcher = { page ->
                page.colorShiftingTiles(
                    itemsPerPage, isDark
                ).map { it.map(Item::Tile) }
            }
        )

    private val managedRequests: Flow<Tile.Input.List<Int, List<Item>>> = requests
        .scan(LoadMetadata()) { metadata, request ->
            val page = request.query
            val isAtEnd = page > (metadata.currentQueries.lastOrNull() ?: 0)
            val allQueries = (metadata.currentQueries + page).distinct().sorted()
            val (toKeep, toEvict) = allQueries.partition {
                if (isAtEnd) it > (page - ConcurrentPages)
                else it < (page + ConcurrentPages)
            }
            metadata.copy(
                currentQueries = toKeep,
                toEvict = toEvict
            )
        }
        .flatMapLatest { metadata ->
            val toEvict: List<Tile.Request.Evict<Int, List<Item>>> = metadata
                .toEvict
                .map { Tile.Request.Evict(it) }
            val toTurnOn: List<Tile.Request.On<Int, List<Item>>> = metadata.currentQueries
                .map { Tile.Request.On(it) }

            (toEvict + toTurnOn).asFlow()
        }

    val listItems: StateFlow<List<Item>> = listTiler
        .invoke(managedRequests)
        .map { it.flatten() }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = listOf()
        )

    /**
     * Makes sure items returned are pivoted around this page
     */
    fun pivotAround(page: Int) = awaitSubscribers {
        requests.emit(Tile.Request.On(page))
    }

    /**
     * Fetch items for the page behind this
     */
    fun fetchPrevious(page: Int) = awaitSubscribers {
        requests.emit(Tile.Request.On(max(a = page - 1, b = 0)))
    }

    /**
     * Fetch items for the specified page after this
     */
    fun fetch(page: Int) = awaitSubscribers {
        requests.emit(Tile.Request.On(page))
    }

    /**
     * Fetch items for the page after this
     */
    fun fetchNext(page: Int) = awaitSubscribers {
        requests.emit(Tile.Request.On(page + 1))
    }

    /**
     * Make sure the downstream is connected before fetching
     */
    private fun awaitSubscribers(block: suspend () -> Unit) {
        scope.launch {
            requests.subscriptionCount.first { it > 0 }
            block()
        }
    }
}
