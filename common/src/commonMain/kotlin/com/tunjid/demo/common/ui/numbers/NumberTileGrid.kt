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
import androidx.compose.foundation.lazy.GridCells
import androidx.compose.foundation.lazy.GridItemSpan
import androidx.compose.foundation.lazy.LazyGridState
import androidx.compose.foundation.lazy.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun NumberTileGrid(
    gridState: LazyGridState,
    items: List<Item>
) {
    LazyVerticalGrid(
        cells = GridCells.Fixed(GridSize),
        state = gridState,
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