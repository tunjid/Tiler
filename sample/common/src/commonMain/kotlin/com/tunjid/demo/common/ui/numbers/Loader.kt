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

import com.tunjid.tiler.ListTiler
import com.tunjid.tiler.Tile
import com.tunjid.tiler.TiledList
import com.tunjid.tiler.emptyTiledList
import com.tunjid.tiler.filterTransform
import com.tunjid.tiler.tiledList
import com.tunjid.tiler.toTiledList
import com.tunjid.tiler.utilities.PivotRequest
import com.tunjid.tiler.utilities.PivotResult
import com.tunjid.tiler.utilities.pivotWith
import com.tunjid.tiler.utilities.toTileInputs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

private const val ITEMS_PER_PAGE = 10

val ascendingPageComparator = compareBy(PageQuery::page)
val descendingPageComparator = ascendingPageComparator.reversed()

// Query for items describing the page and sort order
data class PageQuery(
    val page: Int,
    val isAscending: Boolean
) {
    override fun toString(): String = "page: $page; asc: $isAscending"
}

data class State(
    val isAscending: Boolean = true,
    val itemsPerPage: Int = ITEMS_PER_PAGE,
    val currentPage: Int = 0,
    val firstVisibleIndex: Int = -1,
    val loadSummary: String = "",
    val items: TiledList<PageQuery, NumberTile> = emptyTiledList()
)

class Loader(
    isDark: Boolean,
    scope: CoroutineScope
) {
    private val currentQuery = MutableStateFlow(PageQuery(page = 0, isAscending = true))
    private val gridSizes = MutableStateFlow(1)
    private val pivots = currentQuery.pivotWith(
        gridSizes.map(::pivotRequest)
    )
    private val orderInputs = currentQuery
        .map { pageQuery ->
            Tile.Order.PivotSorted<PageQuery, NumberTile>(
                query = pageQuery,
                comparator = when {
                    pageQuery.isAscending -> ascendingPageComparator
                    else -> descendingPageComparator
                }
            )
        }
        .distinctUntilChanged()
    private val limitInputs = gridSizes.map { gridSize ->
        Tile.Limiter<PageQuery, NumberTile> { items -> items.size > 40 * gridSize }
    }
    private val tiledList = merge(
        pivots.toTileInputs(),
        orderInputs,
        limitInputs,
    )
        .toTiledList(
            numberTiler(
                itemsPerPage = ITEMS_PER_PAGE,
                isDark = isDark,
            )
        )
        .shareIn(scope, SharingStarted.WhileSubscribed())

    val state = combine(
        currentQuery,
        pivots,
        tiledList,
    ) { pageQuery, pivotResult, tiledList ->
        State(
            isAscending = pageQuery.isAscending,
            currentPage = pageQuery.page,
            loadSummary = pivotResult.loadSummary,
            items = tiledList.filterTransform(
                filterTransformer = { distinctBy(NumberTile::key) }
            )
        )
    }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = State()
        )

    fun setCurrentPage(page: Int) = currentQuery.update { query ->
        query.copy(page = page)
    }

    fun toggleOrder() = currentQuery.update { query ->
        query.copy(isAscending = !query.isAscending)
    }

    fun setGridSize(gridSize: Int) = gridSizes.update {
        gridSize
    }

    private val nextQuery: PageQuery.() -> PageQuery? = { copy(page = page + 1) }
    private val previousQuery: PageQuery.() -> PageQuery? = { copy(page = page - 1).takeIf { it.page >= 0 } }

    private fun pivotRequest(gridSize: Int) = PivotRequest(
        onCount = 5 * gridSize,
        offCount = 4,
        nextQuery = nextQuery,
        previousQuery = previousQuery
    )
}

private val PivotResult<PageQuery>.loadSummary
    get() = "Active pages: ${on.map { it.page }}\nPages in memory: ${off.map { it.page }}\n" +
            "Evicted: ${evict.map { it.page }}"

/**
 * Fetches a [Map] of [PageQuery] to [NumberTile] where the [NumberTile] instances self update
 */
private fun numberTiler(
    itemsPerPage: Int,
    isDark: Boolean,
): ListTiler<PageQuery, NumberTile> =
    tiledList(
        limiter = Tile.Limiter { items -> items.size > 40 },
        order = Tile.Order.PivotSorted(
            query = PageQuery(page = 0, isAscending = true),
            comparator = ascendingPageComparator
        ),
        fetcher = { pageQuery ->
            pageQuery.colorShiftingTiles(itemsPerPage, isDark)
        }
    )
