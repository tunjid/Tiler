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

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tunjid.tiler.compose.PivotedTilingEffect

@Composable
fun AdaptiveTiledGrid(
    loader: Loader
) {
    val state by loader.state.collectAsState()
    val isAscending = state.isAscending
    val tiledItems = state.items

    val lazyState = rememberLazyGridState()

    Scaffold(
        bottomBar = {
            TilingSummary(state.tilingSummary)
        },
        floatingActionButton = {
            Fab(
                onClick = { loader.toggleOrder() },
                isAscending = isAscending
            )
        }
    ) {
        LazyVerticalGrid(
            state = lazyState,
            columns = GridCells.Adaptive(200.dp),
            content = {
                items(
                    items = tiledItems,
                    key = NumberTile::key,
                    span = {
                        loader.setNumberOfColumns(maxLineSpan)
                        if (it.number % state.itemsPerPage == 0) GridItemSpan(maxLineSpan)
                        else GridItemSpan(1)
                    },
                    itemContent = { numberTile ->
                        NumberTile(
                            Modifier.animateItem(),
                            numberTile
                        )
                    }
                )
            }
        )
    }

    lazyState.PivotedTilingEffect(
        items = tiledItems,
        indexSelector = { start + (endInclusive - start) / 2 },
        onQueryChanged = { if (it != null) loader.setCurrentPage(it.page) }
    )
}

@Composable
private fun Fab(
    onClick: () -> Unit,
    isAscending: Boolean
) {
    FloatingActionButton(
        onClick = { onClick() },
        content = {
            Row(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .animateContentSize()
            ) {
                val text = if (isAscending) "Sort descending" else "Sort ascending"
                Text(text)
                when {
                    isAscending -> Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = text
                    )

                    else -> Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = text
                    )
                }
            }
        }
    )
}
