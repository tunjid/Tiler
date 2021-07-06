package com.tunjid.tyler

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
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

    private lateinit var tiler: (Flow<TileRequest<Int, List<Int>>>) -> Flow<List<List<Int>>>
    private lateinit var tileFlowMap: MutableMap<Int, MutableStateFlow<List<Int>>>

    @Before
    @FlowPreview
    fun setUp() {
        tileFlowMap = mutableMapOf()
        tiler = tiles { page ->
            tileFlowMap.getOrPut(page) { MutableStateFlow(page.testRange.toList()) }
        }
    }

    @After
    fun tearDown() {
        testScope.cleanupTestCoroutines()
    }

    @Test
    fun `requesting 1 tile works`() = runBlocking {
        val requests = listOf<TileRequest.Request<Int, List<Int>>>(
            TileRequest.Request.On(query = 1),
        )

        val emissions = requests
            .asFlow()
            .tiles(tiler)
            .drop(1) // First emission is an empty list
            .take(requests.size)
            .toList()
            .map(List<List<Int>>::flatten)

        assertEquals(1.testRange.toList(), emissions[0])
    }

    @Test
    fun `requesting multiple queries works`() = runBlocking {
        val requests = listOf<TileRequest.Request<Int, List<Int>>>(
            TileRequest.Request.On(query = 1),
            TileRequest.Request.On(query = 3),
            TileRequest.Request.On(query = 8),
        )
        val emissions = requests
            .asFlow()
            .tiles(tiler)
            .drop(1) // First emission is an empty list
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
        val requests = listOf<TileRequest.Request<Int, List<Int>>>(
            TileRequest.Request.On(query = 1),
            TileRequest.Request.On(query = 3),
            TileRequest.Request.On(query = 8),
        )
        val emissions = requests
            .asFlow()
            .tiles(tiler)
            .drop(1) // First emission is an empty list
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
        val requests = listOf<TileRequest.Request<Int, List<Int>>>(
            TileRequest.Request.On(query = 1),
            TileRequest.Request.On(query = 3),
            TileRequest.Request.On(query = 8),
            TileRequest.Request.Off(query = 3),
            TileRequest.Request.Off(query = 9),
        )

        // Make this hot and shared eagerly to assert subscriptions are still held
        val emissions = requests
            .asFlow()
            .onEach { delay(100) }
            .tiles(tiler)
            .drop(1) // First emission is an empty list
            .withIndex()
            .onEach { (index, _) ->
                assertEquals(
                    if (index > 2) 0 else 1,
                    tileFlowMap.getValue(requests[index].query).subscriptionCount.value
                )
            }
            .map { it.value }
            .take(requests.filterIsInstance<TileRequest.Request.On<Int, List<Int>>>().size)
            .toList()
            .map(List<List<Int>>::flatten)

        assertEquals(
            (1.testRange + 3.testRange + 8.testRange).toList(),
            emissions.last()
        )
    }

    @Test
    fun `queries are idempotent`() = runBlocking {
        val requests = listOf<TileRequest.Request<Int, List<Int>>>(
            TileRequest.Request.On(query = 1),
            TileRequest.Request.On(query = 1),
            TileRequest.Request.On(query = 1),
            TileRequest.Request.On(query = 1),
        )

        // Make this hot and shared eagerly to assert subscriptions are still held
        val emissions = requests
            .asFlow()
            .tiles(tiler)
            .take(2)
            .toList()
            .map(List<List<Int>>::flatten)

        assertEquals(
            1.testRange.toList(),
            emissions.last()
        )

        assertNull(withTimeoutOrNull(200) {
            requests
                .asFlow()
                .tiles(tiler)
                .take(3)
                .toList()
        })
    }
}
