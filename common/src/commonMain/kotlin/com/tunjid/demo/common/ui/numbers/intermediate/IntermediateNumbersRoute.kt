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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import com.tunjid.demo.common.ui.AppRoute
import com.tunjid.demo.common.ui.numbers.ColumnListStyle
import com.tunjid.demo.common.ui.numbers.GridListStyle
import com.tunjid.demo.common.ui.numbers.ListStyle
import com.tunjid.demo.common.ui.numbers.Tabbed
import kotlinx.coroutines.flow.mapNotNull

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
    val items by fetcher.listItems.collectAsState()
    val lazyState = listStyle.rememberState()

    listStyle.Content(
        state = lazyState,
        items = items,
        onStartBoundaryReached = {
            fetcher.fetchPrevious(page = it.page)
        },
        onEndBoundaryReached = {
            fetcher.fetchNext(page = it.page)
        }
    )

    LaunchedEffect(lazyState, items) {
        snapshotFlow { listStyle.firstVisibleIndex(lazyState)?.let(items::get) }
            .mapNotNull { it?.page }
            .collect { fetcher.pivotAround(it) }
    }

    val startItems by rememberUpdatedState(fetcher.listItems)

    LaunchedEffect(startItems) {
        if (startItems.value.isEmpty()) fetcher.pivotAround(0)
    }
}

