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
            .tileWith(
                maxQueries = 3,
                queryItemsSize = null,
                orderFactory = { Tile.Order.Sorted(Int::compareTo) }
            )
            .take(requests.size)
            .toList()

        val optimizedEmissions = requests
            .asFlow()
            .tileWith(
                maxQueries = 3,
                queryItemsSize = 10,
                orderFactory = { Tile.Order.Sorted(Int::compareTo) }
            )
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
            .tileWith(
                maxQueries = 3,
                queryItemsSize = null,
                orderFactory = {
                    Tile.Order.PivotSorted(query = 4, comparator = Int::compareTo)
                },
            )
            .take(requests.size)
            .toListWithTimeout(200)

        val optimizedEmissions = requests
            .asFlow()
            .tileWith(
                maxQueries = 3,
                queryItemsSize = 10,
                orderFactory = {
                    Tile.Order.PivotSorted(query = 4, comparator = Int::compareTo)
                },
            )
            .toListWithTimeout(200)

        assertEquals(
            expected = listOf(
                // First emission
                4.tiledTestRange(),
                // Second emission
                3.tiledTestRange() + 4.tiledTestRange(),
                // Third emission
                3.tiledTestRange() + 4.tiledTestRange() + 8.tiledTestRange(),
                // Fourth emission, 5 should replace 8 in pivoting
                3.tiledTestRange() + 4.tiledTestRange() + 5.tiledTestRange(),
                // Fifth emission, 2 should not show up in the list
            ),
            actual = emissions
        )

        // Optimizations should not change the results
        assertEquals(
            expected = emissions,
            actual = optimizedEmissions
        )
    }

    @Test
    fun empty_tiles_are_ignored() = runTest {
        val requests = listOf<Tile.Input<Int, Int>>(
            Tile.Request.On(query = 1),
            Tile.Request.On(query = 2),
            Tile.Request.On(query = 3),
            Tile.Request.On(query = 4),
            Tile.Request.On(query = 5),
            Tile.Request.On(query = 6),
            Tile.Request.On(query = 7),
            Tile.Order.PivotSorted(query = 6, comparator = Int::compareTo),
        )

        val emissions = requests
            .asFlow()
            .tileWith(
                maxQueries = 3,
                queryItemsSize = null,
                orderFactory = {
                    Tile.Order.Sorted(comparator = Int::compareTo)
                },
                pageFactory = { page ->
                    if (page % 2 == 0) emptyList()
                    else page.tiledTestRange()
                }
            )
            .take(requests.size - 1)
            .toList()

        // First emission
        assertEquals(
            expected = 1.tiledTestRange(),
            actual = emissions[0]
        )

        // Second emission, 2 should not be present
        assertEquals(
            expected = 1.tiledTestRange(),
            actual = emissions[1]
        )

        // Third emission
        assertEquals(
            expected = 1.tiledTestRange() + 3.tiledTestRange(),
            actual = emissions[2]
        )

        // Fourth emission, 4 should not be present
        assertEquals(
            expected = 1.tiledTestRange() + 3.tiledTestRange(),
            actual = emissions[3]
        )

        // Fifth emission
        assertEquals(
            expected = 1.tiledTestRange() + 3.tiledTestRange() + 5.tiledTestRange(),
            actual = emissions[4]
        )

        // sixth, 6 should not be present
        assertEquals(
            expected = 1.tiledTestRange() + 3.tiledTestRange() + 5.tiledTestRange(),
            actual = emissions[5]
        )

        // seventh emission, 7 should not be present bc of max queries
        assertEquals(
            expected = 1.tiledTestRange() + 3.tiledTestRange() + 5.tiledTestRange(),
            actual = emissions[6]
        )

        // No eighth emission, the output has not meaningfully changed.
    }
}

private fun Flow<Tile.Input<Int, Int>>.tileWith(
    maxQueries: Int,
    queryItemsSize: Int?,
    pageFactory: (Int) -> List<Int> = { page -> page.testRange().toList() },
    orderFactory: () -> Tile.Order<Int, Int>
): Flow<TiledList<Int, Int>> = listTiler(
    // Take 3 pages of items
    limiter = Tile.Limiter(
        maxQueries = maxQueries,
        itemSizeHint = queryItemsSize
    ),
    order = orderFactory(),
    fetcher = { page ->
        flowOf(pageFactory(page))
    }).invoke(this)
