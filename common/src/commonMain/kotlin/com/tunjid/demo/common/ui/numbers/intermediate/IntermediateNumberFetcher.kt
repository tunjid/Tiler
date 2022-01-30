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

import com.tunjid.demo.common.ui.MutedColors
import com.tunjid.demo.common.ui.numbers.NumberTile
import com.tunjid.demo.common.ui.numbers.pageRange
import com.tunjid.tiler.Tile
import com.tunjid.tiler.tiledList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan

class IntermediateNumberFetcher(
    itemsPerPage: Int,
    isDark: Boolean,
) {
    private val requests =
        MutableStateFlow<Tile.Request.On<Int, List<Int>>>(Tile.Request.On(query = 0))

    private val managedRequests: Flow<Tile.Input.List<Int, List<NumberTile>>> = requests
        .map { (page) -> listOf(page, page + 1).filter { it >= 0 } }
        .scan(listOf<Int>() to listOf<Int>()) { oldRequestsToNewRequests, newRequests ->
            // Keep track of what was last requested
            oldRequestsToNewRequests.copy(
                first = oldRequestsToNewRequests.second,
                second = newRequests
            )
        }
        .flatMapLatest { (oldRequests, newRequests) ->
            // Evict all items 10 pages behind the smallest page in the new request.
            // Their backing flows will stop being collected, and their existing values will be
            // evicted from memory
            val toEvict: List<Tile.Request.Evict<Int, List<NumberTile>>> = (newRequests.minOrNull()
                ?.minus(10)
                ?.downTo(0)
                ?.take(10)
                ?: listOf())
                .map { Tile.Request.Evict(it) }

            // Turn off the flows for all old requests that are not in the new request batch
            // The existing emitted values will be kept in memory, but their backing flows
            // will stop being collected
            val toTurnOff: List<Tile.Request.Off<Int, List<NumberTile>>> = oldRequests
                .filterNot(newRequests::contains)
                .map { Tile.Request.Off(it) }

            val toTurnOn: List<Tile.Request.On<Int, List<NumberTile>>> = newRequests
                .map { Tile.Request.On(it) }

            (toEvict + toTurnOff + toTurnOn).asFlow()
        }

    private val listTiler: (Flow<Tile.Input.List<Int, List<NumberTile>>>) -> Flow<List<List<NumberTile>>> =
        tiledList(
            order = Tile.Order.Sorted(comparator = Int::compareTo),
            fetcher = { page ->
                flowOf(page.pageRange(itemsPerPage).map {
                    NumberTile(
                        page = page,
                        number = it,
                        color = MutedColors.random(isDark = isDark)
                    )
                })
            }
        )

    val listItems: Flow<List<NumberTile>> = listTiler
        .invoke(managedRequests)
        .map { it.flatten() }

    fun fetchPage(page: Int) {
        requests.value = Tile.Request.On(page)
    }
}