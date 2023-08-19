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

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tunjid.tiler.compose.PivotedTilingEffect

@Composable
fun StickyHeaderTiledList(
    loader: Loader
) {
    val state by loader.state.collectAsState()
    val groupedTiledItems = state.groupedItems

    val lazyState = rememberLazyListState()

    Scaffold(
        bottomBar = {
            TilingSummary(state.tilingSummary)
        },
    ) {
        LazyColumn(
            state = lazyState,
            content = {
                groupedTiledItems.forEach { (page, items) ->
                    stickyHeader {
                        PageHeader(page)
                    }
                    items(
                        items = items,
                        key = NumberTile::key,
                        itemContent = { numberTile ->
                            NumberTile(
                                Modifier.animateItemPlacement(),
                                numberTile
                            )
                        }
                    )
                }
            }
        )
    }

    lazyState.PivotedTilingEffect(
        items = state.items,
        indexSelector = { start + (endInclusive - start) / 2 },
        onQueryChanged = { if (it != null) loader.setCurrentPage(it.page) }
    )
}

@Composable
private fun PageHeader(page: Int) {
    Card(
        shape = RoundedCornerShape(
            topEnd = 16.dp,
            bottomEnd = 16.dp
        )
    ) {
        Text(
            modifier = Modifier
                .padding(
                    vertical = 8.dp,
                    horizontal = 16.dp
                ),
            text = "Page $page"
        )
    }

}
