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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity

@Composable
fun StickyHeaderContainer(
    modifier: Modifier = Modifier,
    listState: LazyListState,
    headerMatcher: (Any) -> Boolean,
    stickyHeader: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    var headerOffset by remember { mutableStateOf(0) }
    val headerOffsetDp = with(LocalDensity.current) { headerOffset.toDp() }

    Box(modifier = modifier) {
        content()
        Box(
            modifier = Modifier.offset(y = headerOffsetDp)
        ) {
            stickyHeader()
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val startOffset = layoutInfo.viewportStartOffset
            val firstCompletelyVisibleItem = layoutInfo.visibleItemsInfo.firstOrNull {
                it.offset >= startOffset
            } ?: return@snapshotFlow 0

            when (headerMatcher(firstCompletelyVisibleItem.key)) {
                false -> 0
                true -> firstCompletelyVisibleItem.size
                    .minus(firstCompletelyVisibleItem.offset)
                    .let { difference -> if (difference < 0) 0 else -difference }
            }
        }
            .collect {
                headerOffset = it
            }
    }
}