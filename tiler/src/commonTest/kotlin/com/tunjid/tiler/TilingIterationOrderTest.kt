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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@ExperimentalCoroutinesApi
class ListMapTilingSamenessTest {

    @Test
    fun `maintain iteration order with Sorted`() = runTest {
        val requests = listOf<Tile.Request<Int, List<Int>>>(
            Tile.Request.On(query = 1),
            Tile.Request.On(query = 3),
            Tile.Request.On(query = 8),
        )
        val emissions = requests
            .asFlow()
            .zipWith { Tile.Order.Sorted(Int::compareTo) }
            .take(requests.size)
            .toList()

        assertSameness(
            1.testRange.toList(),
            emissions[0]
        )

        assertSameness(
            (1.testRange + 3.testRange).toList(),
            emissions[1]
        )

        assertSameness(
            (1.testRange + 3.testRange + 8.testRange).toList(),
            emissions[2]
        )
    }

    @Test
    fun `maintain iteration order with PivotSorted`() = runTest {
        val requests = listOf<Tile.Request<Int, List<Int>>>(
            Tile.Request.On(query = 1),
            Tile.Request.On(query = 3),
            Tile.Request.On(query = 8),
            Tile.Request.On(query = 9),
            Tile.Request.On(query = 4),
        )
        val emissions = requests
            .asFlow()
            .zipWith { Tile.Order.PivotSorted(Int::compareTo) }
            .take(requests.size)
            .toList()

        assertSameness(
            1.testRange.toList(),
            emissions[0]
        )

        assertSameness(
            (1.testRange + 3.testRange).toList(),
            emissions[1]
        )

        assertSameness(
            (1.testRange + 3.testRange + 8.testRange).toList(),
            emissions[2]
        )

        assertSameness(
            (3.testRange + 8.testRange + 9.testRange).toList(),
            emissions[3]
        )

        assertSameness(
            (3.testRange + 4.testRange + 8.testRange).toList(),
            emissions[4]
        )
    }
}

private fun assertSameness(expected: List<Int>, pair: Pair<List<Int>, List<Int>>) {
    assertEquals(expected, pair.first, "Testing list iteration order")
    assertEquals(expected, pair.second, "Testing map iteration order")
}

private fun Flow<Tile.Request<Int, List<Int>>>.zipWith(
    orderFactory: () -> Tile.Order<Int, List<Int>>
): Flow<Pair<List<Int>, List<Int>>> {
    val listTiled = tiledList(
        // Take 3 flattened sets
        limiter = Tile.Limiter.List { it.size >= 3 },
        order = orderFactory(),
        fetcher = { page ->
            flowOf(page.testRange.toList())
        }).invoke(this)
        .map { it.flatten() }

    val mapTiled = tiledMap(
        // Take 3 sets
        limiter = Tile.Limiter.Map { it.size >= 3 },
        order = orderFactory(),
        fetcher = { page ->
            flowOf(page.testRange.toList())
        })
        .invoke(this)
        .map { it.flatMap { (_, values) -> values } }

    return listTiled.zip(mapTiled, ::Pair)
}
