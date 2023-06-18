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
import com.tunjid.tiler.PivotRequest
import com.tunjid.tiler.Pivot
import com.tunjid.tiler.Tile
import com.tunjid.tiler.TiledList
import com.tunjid.tiler.buildTiledList
import com.tunjid.tiler.distinct
import com.tunjid.tiler.emptyTiledList
import com.tunjid.tiler.listTiler
import com.tunjid.tiler.toPivotedTileInputs
import com.tunjid.tiler.toTiledList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlin.math.max
import kotlin.math.min

private const val ITEMS_PER_PAGE = 100

private const val MIN_ITEMS_PER_PAGE = 50

val ascendingPageComparator = compareBy(PageQuery::page)
val descendingPageComparator = ascendingPageComparator.reversed()

// Query for items describing the page and sort order
data class PageQuery(
    val page: Int,
    val isAscending: Boolean
)

data class State(
    val isAscending: Boolean = true,
    val itemsPerPage: Int = ITEMS_PER_PAGE,
    val currentPage: Int = 0,
    val firstVisibleIndex: Int = -1,
    val pivotSummary: String,
    val items: TiledList<PageQuery, NumberTile> = emptyTiledList()
)

class Loader(
    isDark: Boolean,
    scope: CoroutineScope
) {
    // Current query that is visible in the view port
    private val currentQuery = MutableStateFlow(
        PageQuery(
            page = 0,
            isAscending = true
        )
    )

    // Number of columns in the grid
    private val numberOfColumns = MutableStateFlow(1)

    // Flow specifying the pivot configuration
    private val pivotRequests = combine(
        currentQuery.map { it.isAscending },
        numberOfColumns,
        ::pivotRequest
    ).distinctUntilChanged()

    // Define inputs that match the current pivoted position
    private val pivotInputs = currentQuery.toPivotedTileInputs<PageQuery, NumberTile>(
        pivotRequests = pivotRequests
    )
        .shareIn(scope, SharingStarted.WhileSubscribed())

    // Allows for changing the order on response to user input
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

    // Change limit to account for dynamic view port size
    private val limitInputs = numberOfColumns.map { noColumns ->
        Tile.Limiter<PageQuery, NumberTile>(
            maxQueries = max(
                a = noColumns * 2,
                b = 3
            ),
            itemSizeHint = ITEMS_PER_PAGE
        )
    }

    private val tiledList = merge(
        pivotInputs,
        orderInputs,
        limitInputs,
    )
        .toTiledList(
            numberTiler(isDark = isDark)
        )
        .filter { it.size >= MIN_ITEMS_PER_PAGE }
        .shareIn(scope, SharingStarted.WhileSubscribed())

    val state = combine(
        pivotInputs.pivotSummaries(),
        currentQuery,
        tiledList,
    ) { pivotSummary, pageQuery, tiledList ->
        State(
            isAscending = pageQuery.isAscending,
            currentPage = pageQuery.page,
            pivotSummary = pivotSummary,
            items = tiledList.distinct()
        )
    }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = State(pivotSummary = "")
        )

    fun setCurrentPage(page: Int) = currentQuery.update { query ->
        query.copy(page = page)
    }

    fun toggleOrder() = currentQuery.update { query ->
        query.copy(isAscending = !query.isAscending)
    }

    fun setNumberOfColumns(numberOfColumns: Int) = this.numberOfColumns.update {
        numberOfColumns
    }

    // Avoid breaking object equality in [PivotRequest] by using vals
    private val nextQuery: PageQuery.() -> PageQuery? = {
        copy(page = page + 1)
    }
    private val previousQuery: PageQuery.() -> PageQuery? = {
        copy(page = page - 1).takeIf { it.page >= 0 }
    }

    /**
     * Pivoted tiling with the grid size as a dynamic input parameter
     */
    private fun pivotRequest(
        isAscending: Boolean,
        numberOfColumns: Int,
    ) = PivotRequest<PageQuery, NumberTile>(
        onCount = max(a = 3, b = 2 * numberOfColumns),
        offCount = 2 * numberOfColumns,
        nextQuery = nextQuery,
        previousQuery = previousQuery,
        comparator = when {
            isAscending -> ascendingPageComparator
            else -> descendingPageComparator
        }
    )
}

val State.tilingSummary
    get() =
        """
Items per page: $itemsPerPage
Tiled list size: ${items.size}
$pivotSummary
""".trim()

/**
 * Fetches a [Map] of [PageQuery] to [NumberTile] where the [NumberTile] instances self update
 */
private fun numberTiler(
    isDark: Boolean,
): ListTiler<PageQuery, NumberTile> =
    listTiler(
        limiter = Tile.Limiter(
            maxQueries = 3,
            itemSizeHint = ITEMS_PER_PAGE
        ),
        order = Tile.Order.PivotSorted(
            query = PageQuery(page = 0, isAscending = true),
            comparator = ascendingPageComparator
        ),
        fetcher = { pageQuery ->
            pageQuery.colorShiftingTiles(ITEMS_PER_PAGE, isDark)
        }
    )

private fun Flow<Tile.Input<PageQuery, NumberTile>>.pivotSummaries(): Flow<String> =
    filterIsInstance<Pivot<PageQuery, NumberTile>>()
        .map { input ->
            listOf(
                "Active pages: ${input.on.map(PageQuery::page).sorted()}",
                "Pages in memory: ${input.off.map(PageQuery::page).sorted()}",
                "Evicted: ${input.evict.map(PageQuery::page).sorted()}"
            ).joinToString(separator = "\n")
        }