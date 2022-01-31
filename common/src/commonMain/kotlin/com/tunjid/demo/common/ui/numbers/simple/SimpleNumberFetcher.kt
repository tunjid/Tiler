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

package com.tunjid.demo.common.ui.numbers.simple

import com.tunjid.demo.common.ui.MutedColors
import com.tunjid.demo.common.ui.numbers.Item
import com.tunjid.demo.common.ui.numbers.NumberTile
import com.tunjid.demo.common.ui.numbers.pageRange
import com.tunjid.tiler.Tile
import com.tunjid.tiler.tiledList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

class SimpleNumberFetcher(
    itemsPerPage: Int,
    isDark: Boolean,
) {
    private val requests = MutableStateFlow(0)

    /**
     * Tiling function to fetch items for a given page
     */
    private val listTiler: (Flow<Tile.Input.List<Int, List<Item>>>) -> Flow<List<List<Item>>> =
        tiledList(
            order = Tile.Order.Sorted(comparator = Int::compareTo),
            fetcher = { page ->
                flowOf(page.pageRange(itemsPerPage).map {
                    Item.Tile(
                        NumberTile(
                            page = page,
                            number = it,
                            color = MutedColors.colorAt(isDark = isDark, index = 0)
                        )
                    )
                })
            }
        )

    /**
     * Outputs items fetched
     */
    val listItems: Flow<List<Item>> = listTiler
        .invoke(requests
            .onStart {
                emit(requests.value + 1)
                emit(requests.value + 2)
            }
            .map { Tile.Request.On(it) }
        )
        .map { it.flatten() }

    /**
     * Fetches items for the requested page. Fetching is idempotent; no page will be fetched more
     * than once.
     *
     * Note: All pages fetched are kept in memory. To see how memory can be managed, check out the
     * intermediate and advanced examples.
     */
    fun fetchPage(page: Int) {
        requests.value = page
    }
}