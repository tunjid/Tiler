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

import com.tunjid.demo.common.ui.numbers.Item
import com.tunjid.demo.common.ui.numbers.NumberTile
import com.tunjid.demo.common.ui.numbers.colorShiftingTiles
import com.tunjid.mutator.Mutation
import com.tunjid.mutator.Mutator
import com.tunjid.mutator.coroutines.splitByType
import com.tunjid.mutator.coroutines.stateFlowMutator
import com.tunjid.mutator.coroutines.toMutationStream
import com.tunjid.tiler.Tile
import com.tunjid.tiler.tiledMap
import com.tunjid.tiler.toTiledMap
import com.tunjid.utilities.PivotRequest
import com.tunjid.utilities.PivotResult
import com.tunjid.utilities.pivotAround
import com.tunjid.utilities.pivotWith
import com.tunjid.utilities.toRequests
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn

const val GridSize = 5

const val StartAscending = true

 val ascendingPageComparator = compareBy(PageQuery::page)
 val descendingPageComparator = ascendingPageComparator.reversed()

val PagePivot = PivotRequest<PageQuery>(
    onCount = 5,
    nextQuery = { copy(page = page + 1) },
    previousQuery = { copy(page = page - 1).takeIf { it.page >= 0 } }
)

data class PageQuery(
    val page: Int,
    val isAscending: Boolean
)

sealed class Action(val key: String) {
    sealed class Load : Action(key = "Load") {
        data class LoadAround(val pageQuery: PageQuery) : Load()
        data class ToggleOrder(val isAscending: Boolean) : Load()
    }

    data class FirstVisibleIndexChanged(val index: Int) : Action(key = "FirstVisibleIndexChanged")
}

data class State(
    val isAscending: Boolean = StartAscending,
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

fun numberTilesMutator(
    scope: CoroutineScope,
    itemsPerPage: Int,
    isDark: Boolean,
): Mutator<Action, StateFlow<State>> = stateFlowMutator(
    scope = scope,
    initialState = State(),
    actionTransform = { actionFlow ->
        actionFlow.toMutationStream(keySelector = Action::key) {
            when (val action: Action = type()) {
                is Action.Load -> action.flow.loadMutations(
                    scope = scope,
                    isDark = isDark,
                    itemsPerPage = itemsPerPage,
                )

                is Action.FirstVisibleIndexChanged -> action.flow.stickyHeaderMutations()
            }
        }
    }
)

private fun Flow<Action.FirstVisibleIndexChanged>.stickyHeaderMutations(): Flow<Mutation<State>> =
    distinctUntilChanged()
        .map { (firstVisibleIndex) ->
            Mutation { copy(firstVisibleIndex = firstVisibleIndex) }
        }

private fun Flow<Action.Load>.loadMutations(
    scope: CoroutineScope,
    itemsPerPage: Int,
    isDark: Boolean,
): Flow<Mutation<State>> = shareIn(
    scope = scope,
    started = SharingStarted.WhileSubscribed(200),
    replay = 1
).let { loads ->
    merge(
        loads.toNumberedTiles(itemsPerPage = itemsPerPage, isDark = isDark)
            .map { pagesToTiles: Map<PageQuery, List<NumberTile>> ->
                Mutation {
                    copy(items = pagesToTiles.flatMap { (pageQuery, numberTiles) ->
                        val color = numberTiles.first().color
                        val header = Item.Header(page = pageQuery.page, color = color)
                        listOf(header) + numberTiles.map(Item::Tile)
                    }.distinctBy { it.key })
                }
            },
        loads.map { load ->
            when (load) {
                is Action.Load.LoadAround -> Mutation {
                    copy(
                        currentPage = load.pageQuery.page,
                        loadSummary = PagePivot.pivotAround(load.pageQuery).loadSummary
                    )
                }

                is Action.Load.ToggleOrder -> Mutation { copy(isAscending = load.isAscending) }
            }
        }
    )
}


val PivotResult<PageQuery>.loadSummary
    get() = "Active pages: ${on.map { it.page }}\nPages in memory: ${off.map { it.page }}"

private fun Flow<Action.Load>.toNumberedTiles(
    itemsPerPage: Int,
    isDark: Boolean,
) = splitByType(
    typeSelector = { it },
    transform = {
        when (val type = type()) {
            is Action.Load.LoadAround -> type.flow
                .map { it.pageQuery }
                .pivotWith(PagePivot)
                .toRequests()

            is Action.Load.ToggleOrder -> type.flow.map {
                Tile.Order.PivotSorted<PageQuery, List<NumberTile>>(
                    comparator = when {
                        it.isAscending -> descendingPageComparator
                        else -> ascendingPageComparator
                    }
                )
            }
        }
    }
)
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
