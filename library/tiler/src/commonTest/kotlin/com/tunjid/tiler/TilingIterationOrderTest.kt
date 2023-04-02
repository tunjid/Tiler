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
            .orderWith(
                maxQueries = 3,
                queryItemsSize = null
            ) { Tile.Order.Sorted(Int::compareTo) }
            .take(requests.size)
            .toList()

        val optimizedEmissions = requests
            .asFlow()
            .orderWith(
                maxQueries = 3,
                queryItemsSize = 10
            ) { Tile.Order.Sorted(Int::compareTo) }
            .take(requests.size)
            .toList()


        assertEquals(
            expected = 1.tiledTestRange(),
            actual = emissions[0]
        )

        assertEquals(
            expected = 1.tiledTestRange() + 3.tiledTestRange(),
            actual = emissions[1]
        )

        assertEquals(
            expected = 1.tiledTestRange() + 3.tiledTestRange() + 8.tiledTestRange(),
            actual = emissions[2]
        )

        // Optimizations should not change the results
        assertEquals(
            expected = emissions,
            actual = optimizedEmissions
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
            .orderWith(
                maxQueries = 3,
                queryItemsSize = null,
            ) {
                Tile.Order.PivotSorted(query = 4, comparator = Int::compareTo)
            }
            .take(requests.size)
            .toList()

        val optimizedEmissions =requests
            .asFlow()
            .orderWith(
                maxQueries = 3,
                queryItemsSize = 10,
            ) {
                Tile.Order.PivotSorted(query = 4, comparator = Int::compareTo)
            }
            .take(requests.size)
            .toList()

        // First emission
        assertEquals(
            expected = 4.tiledTestRange(),
            actual = emissions[0]
        )

        // First second emission
        assertEquals(
            expected = 3.tiledTestRange() + 4.tiledTestRange(),
            actual = emissions[1]
        )

        // Third emission
        assertEquals(
            expected = 3.tiledTestRange() + 4.tiledTestRange() + 8.tiledTestRange(),
            actual = emissions[2]
        )

        // Fourth emission, 5 should replace 8 in pivoting
        assertEquals(
            expected = 3.tiledTestRange() + 4.tiledTestRange() + 5.tiledTestRange(),
            actual = emissions[3]
        )

        // Fifth emission, 2 should not show up in the list
        assertEquals(
            expected = 3.tiledTestRange() + 4.tiledTestRange() + 5.tiledTestRange(),
            actual = emissions[4]
        )

        // Optimizations should not change the results
        assertEquals(
            expected = emissions,
            actual = optimizedEmissions
        )
    }
}

private fun Flow<Tile.Request<Int, Int>>.orderWith(
    maxQueries: Int,
    queryItemsSize: Int?,
    orderFactory: () -> Tile.Order<Int, Int>
): Flow<List<Int>> = listTiler(
    // Take 3 pages of items
    limiter = Tile.Limiter(
        maxQueries = maxQueries,
        queryItemsSize = queryItemsSize
    ),
    order = orderFactory(),
    fetcher = { page ->
        flowOf(page.testRange.toList())
    }).invoke(this)
