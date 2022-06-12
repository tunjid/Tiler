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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import com.tunjid.demo.common.ui.numbers.advanced.GridSize
import middleItem

object GridListStyle : ListStyle<LazyGridState>(
    name = "Grid",
    itemsPerPage = 50
) {
    override fun firstVisibleIndex(state: LazyGridState): Int? = state
        .layoutInfo
        .visibleItemsInfo
        .firstOrNull()
        ?.index

    override fun firstVisibleKey(state: LazyGridState): Any? = state
        .layoutInfo.visibleItemsInfo
        .firstOrNull()
        ?.key

    override fun lastVisibleIndex(state: LazyGridState): Int? = state
        .layoutInfo.visibleItemsInfo
        .lastOrNull()
        ?.index

    override fun lastVisibleKey(state: LazyGridState): Any? = state
        .layoutInfo.visibleItemsInfo
        .lastOrNull()
        ?.key

    @Composable
    override fun rememberState(): LazyGridState = rememberLazyGridState()

    @Composable
    override fun pageChangeListener(state: LazyGridState, onPageChanged: (Int) -> Unit) {
        state.middleItem(
            queryMapper = { it.key.pageFromKey },
            onQueryChanged = onPageChanged
        )
    }

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
        items: List<Item>,
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(GridSize),
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
                    contentType = { item ->
                        when (item) {
                            is Item.Tile -> 0
                            is Item.Header -> 1
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
