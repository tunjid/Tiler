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

import com.tunjid.demo.common.ui.numbers.advanced.PageQuery
import com.tunjid.demo.common.ui.numbers.advanced.StartAscending
import com.tunjid.demo.common.ui.numbers.advanced.ascendingPageComparator
import com.tunjid.tiler.Tile
import com.tunjid.tiler.tiledList
import com.tunjid.tiler.tiledMap
import com.tunjid.tiler.toTiledList
import com.tunjid.tiler.toTiledMap
import com.tunjid.utilities.PivotRequest
import com.tunjid.utilities.PivotResult
import com.tunjid.utilities.ReactiveMap
import com.tunjid.utilities.pivotWith
import com.tunjid.utilities.toRequests
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class State(
    val isAscending: Boolean = StartAscending,
    val currentPage: Int = 0,
    val firstVisibleIndex: Int = -1,
    val loadSummary: String = "",
    val items: List<NumberTile> = listOf()
)

val Pivot = PivotRequest<PageQuery>(
    onCount = 5,
    nextQuery = { copy(page = page + 1) },
    previousQuery = { copy(page = page - 1).takeIf { it.page >= 0 } }
)

class Loader(
    scope: CoroutineScope
) {
    private val currentQuery = MutableStateFlow(PageQuery(page = 0, isAscending = true))
    private val pivots = currentQuery.pivotWith(Pivot)
    private val loads = pivots.toRequests<PageQuery, List<NumberTile>>()
        .toTiledList(
            numberTiler(
                itemsPerPage = 10,
                isDark = false,
            )
        )
        .map(List<List<NumberTile>>::flatten)
//        .map(::ReactiveMap)
        .shareIn(scope, SharingStarted.WhileSubscribed())


    val state = combine(
        currentQuery,
        pivots,
        loads,
    ) { pageQuery, pivotResult, loads ->
        State(
            isAscending = pageQuery.isAscending,
            currentPage = pageQuery.page,
            loadSummary = pivotResult.loadSummary,
            items = loads
        )
    }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = State()
        )

//    init {
//        loads
//            .flatMapLatest { it.keyFlow }
//            .distinctUntilChanged()
//            .onEach {
//                println("On page ${it.page}")
//                currentQuery.update { query ->
//                    query.copy(page = it.page)
//                }
//            }
//            .launchIn(scope)
//    }

    fun setCurrentPage(page: Int) = currentQuery.update { query ->
        query.copy(page = page)
    }
    fun toggleOrder() = currentQuery.update { query ->
        query.copy(isAscending = !query.isAscending)
    }
}

private val PivotResult<PageQuery>.loadSummary
    get() = "Active pages: ${on.map { it.page }}\nPages in memory: ${off.map { it.page }}"

/**
 * Fetches a [Map] of [PageQuery] to [NumberTile] where the [NumberTile] instances self update
 */
private fun numberTiler(
    itemsPerPage: Int,
    isDark: Boolean,
): (Flow<Tile.Input.List<PageQuery, List<NumberTile>>>) -> Flow<List<List<NumberTile>>> =
    tiledList(
        limiter = Tile.Limiter.List { pages -> pages.size > 4 },
        order = Tile.Order.PivotSorted(comparator = ascendingPageComparator),
        fetcher = { (page, isAscending) ->
            page.colorShiftingTiles(itemsPerPage, isDark)
                .map { if (isAscending) it else it.asReversed() }
//                .onEach { println("Emitted $page") }
        }
    )
