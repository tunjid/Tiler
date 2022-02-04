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

package com.tunjid.demo.common.ui.numbers.intermediate

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import com.tunjid.demo.common.ui.AppRoute
import com.tunjid.demo.common.ui.numbers.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

object IntermediateNumbersRoute : AppRoute {
    override val id: String
        get() = "intermediate"

    @Composable
    override fun Render() {
        Tabbed(
            listStyles = listOf(
                ColumnListStyle as ListStyle<ScrollableState>,
                GridListStyle as ListStyle<ScrollableState>,
            ),
            contentDependencies = { scope, listStyle, isDark ->
                IntermediateNumberFetcher(
                    scope = scope,
                    itemsPerPage = listStyle.itemsPerPage,
                    isDark = isDark,
                )
            },
            content = { listStyle, dependency ->
                IntermediateList(
                    listStyle = listStyle,
                    fetcher = dependency
                )
            }
        )
    }
}

@Composable
fun IntermediateList(
    listStyle: ListStyle<ScrollableState>,
    fetcher: IntermediateNumberFetcher
) {
    val lazyState = listStyle.rememberState()
    val items by fetcher.listItems.collectAsState()
    val stickyHeader: Item.Header? = when (val item = items.getOrNull(
        listStyle.firstVisibleIndex(lazyState) ?: -1
    )) {
        is Item.Tile -> Item.Header(page = item.page, color = item.numberTile.color)
        is Item.Header -> item
        null -> null
    }

    Surface {
        StickyHeaderContainer(
            lazyState = lazyState,
            offsetCalculator = { lazyState ->
                listStyle.stickyHeaderOffsetCalculator(
                    state = lazyState,
                    headerMatcher = Any::isStickyHeaderKey
                )
            },
            stickyHeader = {
                if (stickyHeader != null) listStyle.HeaderItem(
                    modifier = Modifier,
                    item = stickyHeader
                )
            },
            content = {
                listStyle.Content(
                    state = lazyState,
                    items = items,
                )
            }
        )
    }

    LaunchedEffect(lazyState) {
        snapshotFlow { listStyle.firstVisibleKey(lazyState)?.pageFromKey }
            .filterNotNull()
            .distinctUntilChanged()
            .collect {
                fetcher.fetchPrevious(it)
                fetcher.pivotAround(it)
            }
    }

    LaunchedEffect(lazyState) {
        snapshotFlow { listStyle.lastVisibleKey(lazyState)?.pageFromKey }
            .filterNotNull()
            .distinctUntilChanged()
            .collect {
                fetcher.fetchNext(it)
            }
    }

    LaunchedEffect(true) {
        fetcher.fetch(items.lastOrNull()?.page ?: 0)
    }
}

