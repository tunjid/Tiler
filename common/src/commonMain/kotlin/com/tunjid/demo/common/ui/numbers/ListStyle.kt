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

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

sealed class ListStyle<T : ScrollableState>(
    val name: String,
    val itemsPerPage: Int,
) {

    abstract fun firstVisibleIndex(state: T): Int?

    abstract fun stickyHeaderOffsetCalculator(
        state: T,
        headerMatcher: (Any) -> Boolean
    ): Int

    @Composable
    abstract fun rememberState(): T

    @Composable
    abstract fun HeaderItem(
        modifier: Modifier,
        item: Item.Header
    )

    @Composable
    abstract fun TileItem(
        modifier: Modifier,
        item: Item.Tile
    )

    @Composable
    abstract fun Content(
        state: T,
        items: List<Item>,
        onItemsBoundaryReached: (item: Item) -> Unit
    )
}
