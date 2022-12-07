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

import app.cash.turbine.test
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TileKtTest {

    private lateinit var listTiler: ListTiler<Int, Int>
    private lateinit var tileFlowMap: MutableMap<Int, MutableStateFlow<List<Int>>>

    @BeforeTest
    fun setUp() {
        tileFlowMap = mutableMapOf()
        listTiler = listTiler(
            order = Tile.Order.Sorted(Int::compareTo)
        ) { page ->
            tileFlowMap.getOrPut(page) { MutableStateFlow(page.testRange.toList()) }
        }
    }

    @Test
    fun requesting_1_tile_works() = runTest {
        val requests = listOf<Tile.Request<Int, Int>>(
            Tile.Request.On(query = 1),
        )

        val emissions = requests
            .asFlow()
            .toTiledList(listTiler)
            .take(requests.size)
            .toList()

        assertEquals(1.testRange.toList(), emissions[0])
    }

    @Test
    fun requesting_multiple_queries_works() = runTest {
        val requests = listOf<Tile.Request<Int, Int>>(
            Tile.Request.On(query = 1),
            Tile.Request.On(query = 3),
            Tile.Request.On(query = 8),
        )
        val emissions = requests
            .asFlow()
            .toTiledList(listTiler)
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
    fun only_requested_queries_have_subscribers() = runTest {
        val requests = listOf<Tile.Request<Int, Int>>(
            Tile.Request.On(query = 1),
            Tile.Request.On(query = 3),
            Tile.Request.On(query = 8),
        )
        val emissions = requests
            .asFlow()
            .toTiledList(listTiler)
            .withIndex()
            .onEach { (index, _) ->
                val request = requests[index].query
                assertEquals(1, tileFlowMap.getValue(request).subscriptionCount.value)
            }
            .map { it.value }
            .take(requests.size)
            .toList()

        assertEquals(
            (1.testRange + 3.testRange + 8.testRange).toList(),
            emissions.last()
        )

        val queries = requests
            .map { it.query }

        0.rangeTo(10)
            .filterNot(queries::contains)
            .map(tileFlowMap::get)
            .forEach { assertNull(it) }
    }

    @Test
    fun can_turn_off_valve_for_query() = runTest {
        val requests = listOf<Tile.Request<Int, Int>>(
            Tile.Request.On(query = 1),
            Tile.Request.On(query = 3),
            Tile.Request.On(query = 8),
            Tile.Request.Off(query = 3),
            Tile.Request.Off(query = 9),
        )

        // Make this hot and shared eagerly to assert subscriptions are still held
        val emissions = requests
            .asFlow()
            .toTiledList(listTiler)
            .withIndex()
            .onEach { (index, _) ->
                assertEquals(
                    if (index > 2) 0 else 1,
                    tileFlowMap.getValue(requests[index].query).subscriptionCount.value
                )
            }
            .map { it.value }
            .take(requests.filterIsInstance<Tile.Request.On<Int, List<Int>>>().size)
            .toList()

        assertEquals(
            (1.testRange + 3.testRange + 8.testRange).toList(),
            emissions.last()
        )
    }

    @Test
    fun queries_are_idempotent() = runTest {
        val requests = listOf<Tile.Request<Int, Int>>(
            Tile.Request.On(query = 1),
            Tile.Request.On(query = 1),
            Tile.Request.On(query = 1),
            Tile.Request.On(query = 1),
        )

        val emissions = requests
            .asFlow()
            .toTiledList(listTiler)
            .take(1)
            .toList()

        assertEquals(
            1.testRange.toList(),
            emissions.last()
        )

        assertNull(withTimeoutOrNull(200) {
            requests
                .asFlow()
                .toTiledList(listTiler)
                .take(2)
                .toList()
        })
    }

    @Test
    fun queries_can_be_evicted() = runTest {
        val requests = listOf<Tile.Request<Int, Int>>(
            Tile.Request.On(query = 1),
            Tile.Request.On(query = 3),
            Tile.Request.Evict(query = 1),
            Tile.Request.On(query = 1),
        )

        val emissions = requests
            .asFlow()
            .toTiledList(listTiler)
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
            3.testRange.toList(),
            emissions[2]
        )
        assertEquals(
            (1.testRange + 3.testRange).toList(),
            emissions[3]
        )
    }

    @Test
    fun items_limits_and_sorting_can_be_changed_on_the_fly() = runTest {
        val requests = MutableSharedFlow<Tile.Input<Int, Int>>()
        val items = requests.toTiledList(listTiler)

        items.test {
            // Request page 1
            requests.emit(Tile.Request.On(query = 1))
            assertEquals(
                1.testRange.toList(),
                awaitItem()
            )

            // Request page 3
            requests.emit(Tile.Request.On(query = 3))
            assertEquals(
                (1.testRange + 3.testRange).toList(),
                awaitItem()
            )

            // Request page 8
            requests.emit(Tile.Request.On(query = 8))
            assertEquals(
                (1.testRange + 3.testRange + 8.testRange).toList(),
                awaitItem()
            )

            // Reverse sort by page
            requests.emit(Tile.Order.Sorted(comparator = Comparator<Int>(Int::compareTo).reversed()))
            assertEquals(
                (8.testRange + 3.testRange + 1.testRange).toList(),
                awaitItem()
            )

            // Limit results to 2 pages
            requests.emit(Tile.Limiter(check = { it.size >= 20 }))
            assertEquals(
                (8.testRange + 3.testRange).toList(),
                awaitItem()
            )

            // Limit results to 3 pages
            requests.emit(Tile.Limiter(check = { it.size >= 30 }))
            assertEquals(
                (8.testRange + 3.testRange + 1.testRange).toList(),
                awaitItem()
            )

            // Sort ascending
            requests.emit(Tile.Order.Sorted(comparator = Comparator(Int::compareTo)))
            assertEquals(
                (1.testRange + 3.testRange + 8.testRange).toList(),
                awaitItem()
            )

            // Request 4
            requests.emit(Tile.Request.On(query = 4))
            assertEquals(
                (1.testRange + 3.testRange + 4.testRange).toList(),
                awaitItem()
            )

            // Sort ascending pivoted around most recently requested
            requests.emit(
                Tile.Order.PivotSorted(
                    query = 4,
                    comparator = Comparator(Int::compareTo)
                )
            )
            assertEquals(
                (3.testRange + 4.testRange + 8.testRange).toList(),
                awaitItem()
            )

            // Sort ascending in absolute terms
            requests.emit(Tile.Order.Sorted(comparator = Comparator(Int::compareTo)))
            assertEquals(
                (1.testRange + 3.testRange + 4.testRange).toList(),
                awaitItem()
            )

            cancelAndIgnoreRemainingEvents()
        }
    }
}
