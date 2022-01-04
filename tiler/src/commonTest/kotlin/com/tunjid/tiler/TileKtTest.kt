/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tunjid.tiler

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.collections.flatten
import kotlin.test.*

@ExperimentalCoroutinesApi
class TileKtTest {

    private lateinit var listTiler: (Flow<Tile.Input.List<Int, List<Int>>>) -> Flow<List<List<Int>>>
    private lateinit var tileFlowMap: MutableMap<Int, MutableStateFlow<List<Int>>>

    @BeforeTest
    @FlowPreview
    fun setUp() {
        tileFlowMap = mutableMapOf()
        listTiler = tiledList(
            flattener = Tile.Flattener.Sorted(Int::compareTo)) { page ->
            tileFlowMap.getOrPut(page) { MutableStateFlow(page.testRange.toList()) }
        }
    }

    @Test
    fun `requesting 1 tile works`() = runTest {
        val requests = listOf<Tile.Request<Int, List<Int>>>(
            Tile.Request.On(query = 1),
        )

        val emissions = requests
            .asFlow()
            .toTiledList(listTiler)
            .take(requests.size)
            .toList()
            .map(List<List<Int>>::flatten)

        assertEquals(1.testRange.toList(), emissions[0])
    }

    @Test
    fun `requesting multiple queries works`() = runTest {
        val requests = listOf<Tile.Request<Int, List<Int>>>(
            Tile.Request.On(query = 1),
            Tile.Request.On(query = 3),
            Tile.Request.On(query = 8),
        )
        val emissions = requests
            .asFlow()
            .toTiledList(listTiler)
            .take(requests.size)
            .toList()
            .map(List<List<Int>>::flatten)

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
    fun `only requested queries have subscribers`() = runTest {
        val requests = listOf<Tile.Request<Int, List<Int>>>(
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
            .map(List<List<Int>>::flatten)

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
    fun `Can turn off valve for query`() = runTest {
        val requests = listOf<Tile.Request<Int, List<Int>>>(
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
            .map(List<List<Int>>::flatten)

        assertEquals(
            (1.testRange + 3.testRange + 8.testRange).toList(),
            emissions.last()
        )
    }

    @Test
    fun `queries are idempotent`() = runTest {
        val requests = listOf<Tile.Request<Int, List<Int>>>(
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
            .map(List<List<Int>>::flatten)

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
    fun `queries can be evicted`() = runTest {
        val requests = listOf<Tile.Request<Int, List<Int>>>(
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
            .map(List<List<Int>>::flatten)

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
    fun `items can be requested in a map`() = runTest {
        val requests = listOf<Tile.Request<Int, List<Int>>>(
            Tile.Request.On(query = 1),
            Tile.Request.On(query = 3),
            Tile.Request.On(query = 8),
        )

        val emissions = tiledMap<Int, List<Int>> {
                page -> flowOf(page.testRange.toList())
        }
            .invoke(requests.asFlow())
            .take(requests.size)
            .toList()

        assertEquals(
            mapOf(1 to 1.testRange.toList()),
            emissions[0]
        )
        assertEquals(
            mapOf(
                1 to 1.testRange.toList(),
                3 to 3.testRange.toList()
            ),
            emissions[1]
        )
        assertEquals(
            mapOf(
                1 to 1.testRange.toList(),
                3 to 3.testRange.toList(),
                8 to 8.testRange.toList()
            ),
            emissions[2]
        )
    }
}
