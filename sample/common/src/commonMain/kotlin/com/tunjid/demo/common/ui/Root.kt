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

package com.tunjid.demo.common.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.tunjid.demo.common.ui.numbers.Loader
import com.tunjid.demo.common.ui.numbers.AdaptiveTiledGrid
import com.tunjid.demo.common.ui.numbers.StickyHeaderTiledList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun Root() {
    val numPages = 2
    val pagerState = rememberPagerState()
    val scope = rememberCoroutineScope()
    Column {
        TabRow(selectedTabIndex = pagerState.currentPage) {
            repeat(numPages) { page ->
                val title = when (page) {
                    0 -> "Adaptive Paging"
                    1 -> "Sticky Headers"
                    else -> throw IllegalArgumentException()
                }
                Tab(text = { Text(title) },
                    selected = pagerState.currentPage == page,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(page)
                        }
                    }
                )
            }
        }
        HorizontalPager(
            pageCount = numPages,
            state = pagerState,
        ) { page ->
            when (page) {
                0 -> AdaptiveTiledGrid(
                    loader = rememberLoader()
                )

                1 -> StickyHeaderTiledList(
                    loader = rememberLoader()
                )
            }
        }
    }
}

@Composable
fun rememberLoader(
    isDark: Boolean = isSystemInDarkTheme(),
    scope: CoroutineScope = rememberCoroutineScope()
) = remember {
    Loader(
        isDark = isDark,
        scope = scope
    )
}
