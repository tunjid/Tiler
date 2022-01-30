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

package com.tunjid.demo.common.ui.numbers.advanced

import com.tunjid.demo.common.ui.numbers.NumberTile
import com.tunjid.demo.common.ui.numbers.pageRange
import com.tunjid.tiler.Tile
import com.tunjid.tiler.tiledMap
import com.tunjid.tiler.toTiledMap
import kotlinx.coroutines.flow.*
import kotlin.math.abs

const val StartAscending = true
private const val PageWindow = 3

private val ascendingPageComparator = compareBy(PageQuery::page)
private val descendingPageComparator = ascendingPageComparator.reversed()

data class PageQuery(
    val page: Int,
    val isAscending: Boolean
)

/**
 * A summary of the loading queries in the app
 */
data class LoadMetadata(
    val previousQueries: List<PageQuery> = listOf(),
    val currentQueries: List<PageQuery> = listOf(),
    val toEvict: List<PageQuery> = listOf(),
    val inMemory: List<PageQuery> = listOf(),
    val comparator: Comparator<PageQuery>? = null,
    val isAscending: Boolean = StartAscending,
)

/**
 * Converts [LoadMetadata] into a [Map] of [PageQuery] to [NumberTile]
 */
fun Flow<LoadMetadata>.toNumberedTiles(
    itemsPerPage: Int,
    isDark: Boolean,
): Flow<Map<PageQuery, List<NumberTile>>> =
    flatMapLatest { (previousQueries, currentQueries, evictions, _, comparator) ->
        // Turn on flows for the requested pages
        val toTurnOn = currentQueries
            .map { Tile.Request.On<PageQuery, List<NumberTile>>(it) }

        // Turn off the flows for all old requests that are not in the new request batch
        // The existing emitted values will be kept in memory, but their backing flows
        // will stop being collected
        val toTurnOff = previousQueries
            .filterNot { currentQueries.contains(it) }
            .map { Tile.Request.Off<PageQuery, List<NumberTile>>(it) }

        // Evict all items 10 pages behind the smallest page in the new request.
        // Their backing flows will stop being collected, and their existing values will be
        // evicted from memory
        val toEvict = evictions
            .map { Tile.Request.Evict<PageQuery, List<NumberTile>>(it) }

        val comparison = listOfNotNull(
            comparator?.let { Tile.Order.PivotSorted<PageQuery, List<NumberTile>>(it) }
        )

        (toEvict + comparison + toTurnOff + toTurnOn).asFlow()
    }
        .toTiledMap(numberTiler(itemsPerPage = itemsPerPage, isDark = isDark))

/**
 * Metadata describing the status of loading reduced from [Action.Load] requests
 */
fun Flow<Action.Load>.loadMetadata(): Flow<LoadMetadata> =
    scan(listOf<Action.Load>()) { emissions, action -> (emissions + action).takeLast(2) }
        .transformWhile {
            if (it.size == 1) emit(it.first())
            else if (it.size > 1) {
                val (previous, current) = it
                if (current != previous || current is Action.Load.ToggleOrder) emit(current)
            }
            true
        }
        .scan(LoadMetadata()) { previousMetadata, loadAction ->
            // Check sort order
            val isAscending = when (loadAction) {
                is Action.Load.Start,
                is Action.Load.LoadMore -> previousMetadata.isAscending
                Action.Load.ToggleOrder -> !previousMetadata.isAscending
            }
            // Decide what pages to fetch concurrently
            val currentQueries = when (loadAction) {
                is Action.Load.Start -> pagesToLoad(
                    startPage = loadAction.page,
                    isAscending = isAscending
                )
                is Action.Load.LoadMore -> pagesToLoad(
                    startPage = loadAction.page,
                    isAscending = isAscending
                )
                Action.Load.ToggleOrder -> previousMetadata.currentQueries.map {
                    it.copy(isAscending = isAscending)
                }
            }
            val currentlyInMemory = (
                // If the sort order did not change, keep the same queries in memory
                when (previousMetadata.isAscending) {
                    isAscending -> previousMetadata.inMemory
                    else -> listOf()
                } + currentQueries).distinctBy(PageQuery::page)

            val toEvict = when (previousMetadata.isAscending) {
                // The sort order is the same, evict to relieve memory pressure
                isAscending -> when (val min = currentQueries.minByOrNull(PageQuery::page)) {
                    // Not enough memory usage to warrant evicting anythng
                    null -> listOf()
                    // Evict items more than 5 offset pages behind the min current query
                    else -> currentlyInMemory.filter { abs(min.page - it.page) > 5 }
                }
                // The sort order has changed, invalidate all queries
                else -> (previousMetadata.currentQueries + previousMetadata.inMemory).distinct()
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
        .distinctUntilChanged()

/**
 * Fetches a [Map] of [PageQuery] to [NumberTile] where the [NumberTile] instances self update
 */
private fun numberTiler(
    itemsPerPage: Int,
    isDark: Boolean,
): (Flow<Tile.Input.Map<PageQuery, List<NumberTile>>>) -> Flow<Map<PageQuery, List<NumberTile>>> =
    tiledMap(
        limiter = Tile.Limiter.Map { pages -> pages.size > 4 },
        order = Tile.Order.PivotSorted(comparator = ascendingPageComparator),
        fetcher = { (page, isAscending) ->
            argbFlow(isDark = isDark).map { color ->
                page.pageRange(itemsPerPage).map { number ->
                    NumberTile(
                        number = number,
                        color = color,
                        page = page
                    )
                }.let { if (isAscending) it else it.asReversed() }
            }
        }
    )

/**
 * Computes the [PageQuery] to run for a single page
 */
private fun pagesToLoad(startPage: Int, isAscending: Boolean): List<PageQuery> {
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
    return result.map {
        PageQuery(page = it, isAscending = isAscending)
    }
}

val LoadMetadata.startPage: Int
    get() {
        val isOdd = PageWindow % 2 != 0
        val index = if (isOdd) (PageWindow / 2) + 1 else PageWindow / 2
        return currentQueries.getOrNull(index)?.page ?: 0
    }

val LoadMetadata.loadSummary get() = "Active pages: ${activePages}\nPages in memory: $cachedPages"

private val LoadMetadata.activePages get() = currentQueries.map(PageQuery::page)

private val LoadMetadata.cachedPages get() = inMemory.map(PageQuery::page).sorted()
