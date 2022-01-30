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

import androidx.compose.foundation.gestures.ScrollableState
import com.tunjid.demo.common.ui.numbers.ListStyle
import com.tunjid.demo.common.ui.numbers.NumberTile
import com.tunjid.mutator.Mutation
import com.tunjid.mutator.Mutator
import com.tunjid.mutator.coroutines.stateFlowMutator
import com.tunjid.mutator.coroutines.toMutationStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*

const val GridSize = 5

sealed class Action(val key: String) {
    sealed class Load : Action(key = "Load") {
        data class Start(val page: Int) : Load()
        data class LoadMore(val page: Int) : Load()
        object ToggleOrder : Load()
    }

    data class FirstVisibleIndexChanged(val index: Int) : Action(key = "FirstVisibleIndexChanged")
}

data class State(
    val listStyle: ListStyle<ScrollableState>,
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

fun numberTilesMutator(
    scope: CoroutineScope,
    itemsPerPage: Int,
    isDark: Boolean,
    listStyle: ListStyle<ScrollableState>
): Mutator<Action, StateFlow<State>> = stateFlowMutator(
    scope = scope,
    initialState = State(listStyle = listStyle),
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
): Flow<Mutation<State>> = loadMetadata()
    .shareIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(200),
        replay = 1
    ).let { loadMetadata ->
        merge(
            loadMetadata.toNumberedTiles(itemsPerPage = itemsPerPage, isDark = isDark)
                .map { pagesToTiles: Map<PageQuery, List<NumberTile>> ->
                    Mutation {
                        copy(items = pagesToTiles.flatMap { (pageQuery, numberTiles) ->
                            val color = numberTiles.first().color
                            val header = Item.Header(page = pageQuery.page, color = color)
                            listOf(header) + numberTiles.map(Item::Tile)
                        })
                    }
                },
            loadMetadata
                .map {
                    Mutation {
                        copy(
                            isAscending = it.isAscending,
                            // 3 pages are fetched at once, the middle is the current page
                            currentPage = it.startPage,
                            loadSummary = it.loadSummary
                        )
                    }
                }
        )
    }
