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

package com.tunjid.tiler.compose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridItemInfo
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import com.tunjid.tiler.TiledList
import com.tunjid.tiler.queryAtOrNull
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun <Query> LazyListState.PivotedTilingEffect(
    items: TiledList<Query, *>,
    indexSelector: IntRange.() -> Int = kotlin.ranges.IntRange::first,
    onQueryChanged: (Query?) -> Unit
) = PivotedTilingEffect(
    items = items,
    indexSelector = indexSelector,
    onQueryChanged = onQueryChanged,
    itemsList = { layoutInfo.visibleItemsInfo },
    indexForItem = LazyListItemInfo::index
)

@Composable
fun <Query> LazyGridState.PivotedTilingEffect(
    items: TiledList<Query, *>,
    indexSelector: IntRange.() -> Int = kotlin.ranges.IntRange::first,
    onQueryChanged: (Query?) -> Unit
) = PivotedTilingEffect(
    items = items,
    indexSelector = indexSelector,
    onQueryChanged = onQueryChanged,
    itemsList = { layoutInfo.visibleItemsInfo },
    indexForItem = LazyGridItemInfo::index
)

@Composable
fun <Query> LazyStaggeredGridState.PivotedTilingEffect(
    items: TiledList<Query, *>,
    indexSelector: IntRange.() -> Int = kotlin.ranges.IntRange::first,
    onQueryChanged: (Query?) -> Unit
) = PivotedTilingEffect(
    items = items,
    indexSelector = indexSelector,
    onQueryChanged = onQueryChanged,
    itemsList = { layoutInfo.visibleItemsInfo },
    indexForItem = LazyStaggeredGridItemInfo::index
)

@Composable
@ExperimentalFoundationApi
fun <Query> PagerState.PivotedTilingEffect(
    items: TiledList<Query, *>,
    onQueryChanged: (Query?) -> Unit
) {
    val updatedItems by rememberUpdatedState(items)
    LaunchedEffect(this) {
        snapshotFlow {
            updatedItems.queryAtOrNull(currentPage)
        }
            .distinctUntilChanged()
            .collect(onQueryChanged)
    }
}

@Composable
private inline fun <Query, LazyState : Any, LazyStateItem> LazyState.PivotedTilingEffect(
    items: TiledList<Query, *>,
    noinline onQueryChanged: (Query?) -> Unit,
    crossinline indexSelector: IntRange.() -> Int = kotlin.ranges.IntRange::first,
    crossinline itemsList: LazyState.() -> List<LazyStateItem>,
    crossinline indexForItem: (LazyStateItem) -> Int?,
) {
    val updatedItems by rememberUpdatedState(items)
    LaunchedEffect(this) {
        snapshotFlow {
            val visibleItemsInfo = itemsList(this@PivotedTilingEffect)
            val index = indexSelector(visibleItemsInfo.indices)
            val lazyStateItem = visibleItemsInfo.getOrNull(index)
            val itemIndex = lazyStateItem?.let(indexForItem)
            itemIndex?.let(updatedItems::queryAtOrNull)
        }
            .distinctUntilChanged()
            .collect(onQueryChanged)
    }
}