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
import com.tunjid.demo.common.ui.numbers.NumberTile
import com.tunjid.demo.common.ui.numbers.pageRange
import com.tunjid.tiler.Tile
import com.tunjid.tiler.tiledList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class SimpleNumberFetcher(
    itemsPerPage: Int,
    isDark: Boolean
) {
    private val requests =
        MutableStateFlow<Tile.Input.List<Int, List<NumberTile>>>(Tile.Request.On(query = 0))

    private val listTiler: (Flow<Tile.Input.List<Int, List<NumberTile>>>) -> Flow<List<List<NumberTile>>> = tiledList(
        order = Tile.Order.Sorted(comparator = Int::compareTo),
        fetcher = { page ->
            flowOf( page.pageRange(itemsPerPage).map {
                NumberTile(
                    page = page,
                    number = it,
                    color = MutedColors.random(isDark = isDark)
                )
            })
        }
    )

    val listItems: Flow<List<NumberTile>> = listTiler
        .invoke(requests)
        .map { it.flatten() }

    fun fetchPage(page: Int) {
        requests.value = Tile.Request.On(page)
    }
}