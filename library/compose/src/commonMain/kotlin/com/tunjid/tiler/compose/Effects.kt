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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import com.tunjid.tiler.TiledList
import com.tunjid.tiler.queryAtOrNull
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Composable
fun <Query> LazyListState.PivotedTilingEffect(
    items: TiledList<Query, *>,
    indexSelector: (IntRange) -> Int = kotlin.ranges.IntRange::first,
    onQueryChanged: (Query?) -> Unit
) {
    LaunchedEffect(this, items) {
        snapshotFlow {
            val visibleItemsInfo = layoutInfo.visibleItemsInfo
            val index = indexSelector(visibleItemsInfo.indices)
            visibleItemsInfo.getOrNull(index)
        }
            .map { it?.index?.let(items::queryAtOrNull) }
            .distinctUntilChanged()
            .collect(onQueryChanged)
    }
}

@Composable
fun <Query> LazyGridState.PivotedTilingEffect(
    list: TiledList<Query, *>,
    indexSelector: (IntRange) -> Int = kotlin.ranges.IntRange::first,
    current: (Query?) -> Unit
) {
    LaunchedEffect(this, list) {
        snapshotFlow {
            val visibleItemsInfo = layoutInfo.visibleItemsInfo
            val index = indexSelector(visibleItemsInfo.indices)
            visibleItemsInfo.getOrNull(index)
        }
            .map { it?.index?.let(list::queryAtOrNull) }
            .distinctUntilChanged()
            .collect(current)
    }
}

@Composable
@ExperimentalFoundationApi
fun <Query> LazyStaggeredGridState.PivotedTilingEffect(
    list: TiledList<Query, *>,
    indexSelector: (IntRange) -> Int = kotlin.ranges.IntRange::first,
    current: (Query?) -> Unit
) {
    LaunchedEffect(this, list) {
        snapshotFlow {
            val visibleItemsInfo = layoutInfo.visibleItemsInfo
            val index = indexSelector(visibleItemsInfo.indices)
            visibleItemsInfo.getOrNull(index)
        }
            .map { it?.index?.let(list::queryAtOrNull) }
            .distinctUntilChanged()
            .collect(current)
    }
}