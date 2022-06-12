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

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

@Composable
inline fun <Query> LazyListState.middleItem(
    crossinline queryMapper: (LazyListItemInfo) -> Query,
    crossinline onQueryChanged: (Query) -> Unit
) {
    LaunchedEffect(this) {
        snapshotFlow {
            val visible = layoutInfo.visibleItemsInfo
            val middle = visible.getOrNull(visible.size / 2)
            middle?.let(queryMapper)
        }
            .filterNotNull()
            .distinctUntilChanged()
            .collect {
                onQueryChanged(it)
            }
    }
}

@Composable
inline fun <Query> LazyGridState.middleItem(
    crossinline queryMapper: (LazyGridItemInfo) -> Query,
    crossinline onQueryChanged: (Query) -> Unit
) {
    LaunchedEffect(this) {
        snapshotFlow {
            val visible = layoutInfo.visibleItemsInfo
            val middle = visible.getOrNull(visible.size / 2)
            middle?.let(queryMapper)
        }
            .filterNotNull()
            .distinctUntilChanged()
            .collect {
                onQueryChanged(it)
            }
    }
}