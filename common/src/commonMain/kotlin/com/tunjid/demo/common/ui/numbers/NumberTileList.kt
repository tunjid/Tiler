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

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlin.math.max
import kotlin.math.min

object NumberTileList : ListStyle<LazyListState>(name = "List") {
    override fun firstVisibleIndex(state: LazyListState): Int? =
        state.layoutInfo.visibleItemsInfo.firstOrNull()?.index

    override fun scrollState(
        state: LazyListState,
        items: List<Item>,
        isAscending: Boolean
    ): ScrollState =
        ScrollState(
            offset = state.firstVisibleItemScrollOffset,
            page = Pair(
                first = (
                    items.getOrNull(state.firstVisibleItemIndex)
                        ?.page
                        ?: 0),
                second = (
                    items.getOrNull(
                        state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    )
                        ?.page
                        ?: 0)
            ).let {
                if (isAscending) max(it.first, it.second) else min(it.first, it.second)
            }
        )

    override fun stickyHeaderOffsetCalculator(
        state: LazyListState,
        headerMatcher: (Any) -> Boolean
    ): Int {
        val layoutInfo = state.layoutInfo
        val startOffset = layoutInfo.viewportStartOffset
        val firstCompletelyVisibleItem = layoutInfo.visibleItemsInfo.firstOrNull {
            it.offset >= startOffset
        } ?: return 0

        return when (headerMatcher(firstCompletelyVisibleItem.key)) {
            false -> 0
            true -> firstCompletelyVisibleItem.size
                .minus(firstCompletelyVisibleItem.offset)
                .let { difference -> if (difference < 0) 0 else -difference }
        }
    }

    @Composable
    override fun rememberState(): LazyListState = rememberLazyListState()

    @Composable
    override fun Content(state: LazyListState, items: List<Item>) {
        LazyColumn(
            state = state,
            content = {
                items(
                    items = items,
                    key = { it.key },
                    itemContent = { item ->
                        when (item) {
                            is Item.Header -> Row {
                                HeaderTile(
                                    modifier = Modifier.wrapContentSize(),
                                    item = item
                                )
                            }
                            is Item.Tile -> NumberTile(
                                modifier = Modifier,
                                tile = item.numberTile
                            )
                        }
                    }
                )
            }
        )
    }
}
