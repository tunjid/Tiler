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
import com.tunjid.demo.common.ui.numbers.colorShiftingTiles
import com.tunjid.tiler.Tile
import com.tunjid.tiler.tiledMap
import com.tunjid.tiler.toTiledMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan

const val StartAscending = true

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
    val pivotPage: Int = 0,
    // Pages actively being collected and loaded from
    val on: List<Int> = listOf(),
    // Pages whose emissions are in memory, but are not being collected from
    val off: List<Int> = listOf(),
    // Pages to remove from memory
    val evict: List<Int> = listOf(),
    // Sort order
    val isAscending: Boolean = true,
)

val LoadMetadata.loadSummary get() = "Active pages: ${on}\nPages in memory: $off"

/**
 * Loads pages pivoted around the users scroll position
 */
fun Flow<Action.Load>.loadMetadata() =
    distinctUntilChanged()
        .scan(LoadMetadata()) { previous, emitted ->
            when (emitted) {
                is Action.Load.LoadAround -> {
                    // Load 5 pages pivoted around the current page at once
                    val on: List<Int> = ((emitted.page - 2)..(emitted.page + 2))
                        .filter { it >= 0 }
                        .toList()
                    // Keep 2 pages on either end of the active pages in memory
                    val off: List<Int> = ((emitted.page - 5)..(emitted.page + -3))
                        .plus(((emitted.page + 3)..(emitted.page + 5)))
                        .filter { it >= 0 }
                    LoadMetadata(
                        on = on,
                        off = off,
                        pivotPage = emitted.page,
                        isAscending = previous.isAscending,
                        // Evict everything not in the curren active and inactive range
                        evict = (previous.on + previous.off) - (on + off).toSet()
                    )
                }
                Action.Load.ToggleOrder -> previous.copy(isAscending = !previous.isAscending)
            }
        }
        .distinctUntilChanged()

fun Flow<LoadMetadata>.toNumberedTiles(
    itemsPerPage: Int,
    isDark: Boolean,
) = flatMapLatest(LoadMetadata::toQueries)
    .toTiledMap(
        numberTiler(
            itemsPerPage = itemsPerPage,
            isDark = isDark
        )
    )

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
            page.colorShiftingTiles(itemsPerPage, isDark)
                .map { if (isAscending) it else it.asReversed() }
        }
    )

private fun LoadMetadata.tileRequest(
    pages: List<Int>,
    constructor: (PageQuery) -> Tile.Request<PageQuery, List<NumberTile>>
): List<Tile.Request<PageQuery, List<NumberTile>>> =
    pages.map {
        constructor(
            PageQuery(
                page = it,
                isAscending = isAscending,
            )
        )
    }

private fun LoadMetadata.toQueries(): Flow<Tile.Input.Map<PageQuery, List<NumberTile>>> =
    listOf(
        tileRequest(evict) { Tile.Request.Evict(it) },
        tileRequest(off) { Tile.Request.Off(it) },
        tileRequest(on) { Tile.Request.On(it) },
        listOf(
            Tile.Order.PivotSorted(
                comparator =
                if (isAscending) ascendingPageComparator
                else descendingPageComparator
            )
        )
    )
        .flatten()
        .asFlow()
