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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.GridCells
import androidx.compose.foundation.lazy.GridItemSpan
import androidx.compose.foundation.lazy.LazyGridState
import androidx.compose.foundation.lazy.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import com.tunjid.demo.common.ui.numbers.advanced.GridSize
import kotlin.math.roundToInt

object GridListStyle : ListStyle<LazyGridState>(
    name = "Grid",
    itemsPerPage = 50
) {
    override fun firstVisibleIndex(state: LazyGridState): Int? =
        state.layoutInfo.visibleItemsInfo.firstOrNull()?.index

    @Composable
    override fun boundaryKey(lazyListState: LazyGridState): Any? {
        var previousIndex by remember(lazyListState) { mutableStateOf(lazyListState.firstVisibleItemIndex) }
        var previousScrollOffset by remember(lazyListState) { mutableStateOf(lazyListState.firstVisibleItemScrollOffset) }
        return remember(lazyListState) {
            derivedStateOf {
                if (lazyListState.layoutInfo.totalItemsCount == 0) return@derivedStateOf null

                val isDownward = when {
                    previousIndex != lazyListState.firstVisibleItemIndex -> {
                        previousIndex < lazyListState.firstVisibleItemIndex
                    }
                    else -> if (previousScrollOffset == lazyListState.firstVisibleItemScrollOffset) {
                        return@derivedStateOf null
                    } else {
                        previousScrollOffset <= lazyListState.firstVisibleItemScrollOffset
                    }.also {
                        previousIndex = lazyListState.firstVisibleItemIndex
                        previousScrollOffset = lazyListState.firstVisibleItemScrollOffset
                    }
                }
                val boundaryItem = lazyListState.layoutInfo.visibleItemsInfo.let {
                    if (isDownward) it.lastOrNull() else it.firstOrNull()
                } ?: return@derivedStateOf null

                val percentage = (boundaryItem.index * 100f)
                    .div(lazyListState.layoutInfo.totalItemsCount)
                    .roundToInt()

                if (isDownward && percentage < 80) return@derivedStateOf null
                if (!isDownward && percentage > 20) return@derivedStateOf null

                return@derivedStateOf boundaryItem.key.pageFromKey
            }
        }.value
    }

    @Composable
    override fun rememberState(): LazyGridState = rememberLazyGridState()

    override fun stickyHeaderOffsetCalculator(
        state: LazyGridState,
        headerMatcher: (Any) -> Boolean
    ): Int {
        val layoutInfo = state.layoutInfo
        val startOffset = layoutInfo.viewportStartOffset
        val firstCompletelyVisibleItem = layoutInfo.visibleItemsInfo.firstOrNull {
            it.offset.y >= startOffset
        } ?: return 0

        return when (headerMatcher(firstCompletelyVisibleItem.key)) {
            false -> 0
            true -> firstCompletelyVisibleItem.size
                .height
                .minus(firstCompletelyVisibleItem.offset.y)
                .let { difference -> if (difference < 0) 0 else -difference }
        }
    }

    @Composable
    override fun HeaderItem(
        modifier: Modifier,
        item: Item.Header
    ) {
        Row {
            HeaderTile(
                modifier = modifier.wrapContentSize(),
                item = item
            )
        }
    }

    @Composable
    override fun TileItem(
        modifier: Modifier,
        item: Item.Tile
    ) {
        NumberTile(
            modifier = modifier
                .aspectRatio(1f)
                .scale(0.9f),
            tile = item.numberTile
        )
    }

    @Composable
    override fun Content(
        state: LazyGridState,
        items: List<Item>
    ) {
        LazyVerticalGrid(
            cells = GridCells.Fixed(GridSize),
            state = state,
            content = {
                items(
                    items = items,
                    key = { it.key },
                    span = { item ->
                        when (item) {
                            is Item.Tile -> GridItemSpan(currentLineSpan = 1)
                            is Item.Header -> GridItemSpan(maxCurrentLineSpan)
                        }
                    },
                    itemContent = { item ->
                        when (item) {
                            is Item.Header -> HeaderItem(
                                modifier = Modifier,
                                item = item,
                            )
                            is Item.Tile -> TileItem(
                                modifier = Modifier,
                                item = item,
                            )
                        }
                    }
                )
            }
        )
    }
}
