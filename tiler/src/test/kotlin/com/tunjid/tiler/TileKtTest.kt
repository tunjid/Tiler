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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import kotlin.collections.flatten

@ExperimentalCoroutinesApi
class TileKtTest {

    private val testScope = TestCoroutineScope()

    private lateinit var tiler: (Flow<Tile.Input<Int, List<Int>>>) -> Flow<List<List<Int>>>
    private lateinit var tileFlowMap: MutableMap<Int, MutableStateFlow<List<Int>>>

    @Before
    @FlowPreview
    fun setUp() {
        tileFlowMap = mutableMapOf()
        tiler = tiledList(flattener = Tile.Flattener.Sorted(Int::compareTo)) { page ->
            tileFlowMap.getOrPut(page) { MutableStateFlow(page.testRange.toList()) }
        }
    }

    @After
    fun tearDown() {
        testScope.cleanupTestCoroutines()
    }

    @Test
    fun `requesting 1 tile works`() = runBlocking {
        val requests = listOf<Tile.Request<Int, List<Int>>>(
            Tile.Request.On(query = 1),
        )

        val emissions = requests
            .asFlow()
            .flattenWith(tiler)
            .take(requests.size)
            .toList()
            .map(List<List<Int>>::flatten)

        assertEquals(1.testRange.toList(), emissions[0])
    }

    @Test
    fun `requesting multiple queries works`() = runBlocking {
        val requests = listOf<Tile.Request<Int, List<Int>>>(
            Tile.Request.On(query = 1),
            Tile.Request.On(query = 3),
            Tile.Request.On(query = 8),
        )
        val emissions = requests
            .asFlow()
            .flattenWith(tiler)
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
    fun `only requested queries have subscribers`() = runBlocking {
        val requests = listOf<Tile.Request<Int, List<Int>>>(
            Tile.Request.On(query = 1),
            Tile.Request.On(query = 3),
            Tile.Request.On(query = 8),
        )
        val emissions = requests
            .asFlow()
            .flattenWith(tiler)
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
    fun `Can turn off valve for query`() = runBlocking {
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
            .flattenWith(tiler)
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
    fun `queries are idempotent`() = runBlocking {
        val requests = listOf<Tile.Request<Int, List<Int>>>(
            Tile.Request.On(query = 1),
            Tile.Request.On(query = 1),
            Tile.Request.On(query = 1),
            Tile.Request.On(query = 1),
        )

        val emissions = requests
            .asFlow()
            .flattenWith(tiler)
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
                .flattenWith(tiler)
                .take(2)
                .toList()
        })
    }

    @Test
    fun `queries can be evicted`() = runBlocking {
        val requests = listOf<Tile.Request<Int, List<Int>>>(
            Tile.Request.On(query = 1),
            Tile.Request.On(query = 3),
            Tile.Request.Evict(query = 1),
            Tile.Request.On(query = 1),
        )

        val emissions = requests
            .asFlow()
            .flattenWith(tiler)
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
}
