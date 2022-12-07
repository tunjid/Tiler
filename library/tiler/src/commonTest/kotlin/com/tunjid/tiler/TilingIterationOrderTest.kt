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

package com.tunjid.tiler

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ListMapTilingSamenessTest {

    @Test
    fun maintain_iteration_order_with_Sorted() = runTest {
        val requests = listOf<Tile.Request<Int, Int>>(
            Tile.Request.On(query = 1),
            Tile.Request.On(query = 3),
            Tile.Request.On(query = 8),
        )
        val emissions = requests
            .asFlow()
            .orderWith { Tile.Order.Sorted(Int::compareTo) }
            .take(requests.size)
            .toList()

        assertEquals(
            1.testRange.toList(),
            emissions[0]
        )

        assertEquals(
            (1.testRange + 3.testRange).toList(),
            emissions[1]
        )

        assertEquals(
            (1.testRange + 3.testRange + 8.testRange).toList(),
            emissions[2]
        )
    }

    @Test
    fun maintain_iteration_order_with_PivotSorted() = runTest {
        val requests = listOf<Tile.Request<Int, Int>>(
            Tile.Request.On(query = 4),
            Tile.Request.On(query = 3),
            Tile.Request.On(query = 8),
            Tile.Request.On(query = 5),
            Tile.Request.On(query = 2),
        )
        val emissions = requests
            .asFlow()
            .orderWith {
                Tile.Order.PivotSorted(query = 4, comparator = Int::compareTo)
            }
            .take(requests.size)
            .toList()

        assertEquals(
            4.testRange.toList(),
            emissions[0]
        )

        assertEquals(
            (3.testRange + 4.testRange).toList(),
            emissions[1]
        )

        assertEquals(
            (3.testRange + 4.testRange + 8.testRange).toList(),
            emissions[2]
        )

        assertEquals(
            (3.testRange + 4.testRange + 5.testRange).toList(),
            emissions[3]
        )

        assertEquals(
            (3.testRange + 4.testRange + 5.testRange).toList(),
            emissions[4]
        )
    }
}

private fun Flow<Tile.Request<Int, Int>>.orderWith(
    orderFactory: () -> Tile.Order<Int, Int>
): Flow<List<Int>> = tiledList(
    // Take 3 pages of items
    limiter = Tile.Limiter { it.size >= 30 },
    order = orderFactory(),
    fetcher = { page ->
        flowOf(page.testRange.toList())
    }).invoke(this)
