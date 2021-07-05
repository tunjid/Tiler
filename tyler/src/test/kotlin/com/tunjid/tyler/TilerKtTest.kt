package com.tunjid.tyler

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

private typealias IntTiles = Map<Int, Tile<Int, List<Int>>>

@ExperimentalCoroutinesApi
class TilerKtTest {

    private val testScope = TestCoroutineScope()

    private lateinit var tiler: (Flow<TileRequest<Int>>) -> Flow<IntTiles>
    private lateinit var tileFlowMap: MutableMap<Int, MutableStateFlow<List<Int>>>

    @Before
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
        val requests = listOf(
            TileRequest.On(query = 1),
        )

        val emissions = requests
            .asFlow()
            .tiles(tiler)
            .flattened()
            .take(requests.size + 1)
            .toList()

        assertEquals(emptyList<Int>(), emissions[0])
        assertEquals(1.testRange.toList(), emissions[1])
    }

    @Test
    fun `requesting multiple queries works`() = runBlocking {
        val requests = listOf(
            TileRequest.On(query = 1),
            TileRequest.On(query = 3),
            TileRequest.On(query = 8),
        )
        val emissions = requests
            .asFlow()
            .tiles(tiler)
            .flattened()
            .take(requests.size + 1)
            .toList()

        assertEquals(
            emptyList<Int>(),
            emissions[0]
        )
        assertEquals(
            1.testRange.toList(),
            emissions[1]
        )
        assertEquals(
            (1.testRange + 3.testRange).toList(),
            emissions[2]
        )
        assertEquals(
            (1.testRange + 3.testRange + 8.testRange).toList(),
            emissions[3]
        )
    }

    @Test
    fun `only requested queries have subscribers`() = runBlocking {
        val requests = listOf(
            TileRequest.On(query = 1),
            TileRequest.On(query = 3),
            TileRequest.On(query = 8),
        )
        val emissions = requests
            .asFlow()
            .tiles(tiler)
            .flattened()
            .withRequestIndex { requestIndex ->
                val request = requests[requestIndex].query
                assertEquals(1, tileFlowMap.getValue(request).subscriptionCount.value)
            }
            .take(requests.size + 1)
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
    fun `Can turn off valve for query`() = runBlocking {
        val requests = listOf(
            TileRequest.On(query = 1),
            TileRequest.On(query = 3),
            TileRequest.On(query = 8),
            TileRequest.Off(query = 3),
            TileRequest.Off(query = 9),
        )

        // Make this hot and shared eagerly to assert subscriptions are still held
        val emissions = requests
            .asFlow()
            .onEach { delay(100) }
            .tiles(tiler)
            .flattened()
            .withRequestIndex { requestIndex ->
                assertEquals(
                    if (requestIndex == 4) 0 else 1,
                    tileFlowMap.getValue(requests[requestIndex].query).subscriptionCount.value
                )
            }
            .take(requests.filterIsInstance<TileRequest.On<Int>>().size + 1)
            .toList()

        assertEquals(
            (1.testRange + 3.testRange + 8.testRange).toList(),
            emissions.last()
        )
    }

    @Test
    fun `queries are idempotent`() = runBlocking {
        val requests = listOf(
            TileRequest.On(query = 1),
            TileRequest.On(query = 1),
            TileRequest.On(query = 1),
            TileRequest.On(query = 1),
        )

        // Make this hot and shared eagerly to assert subscriptions are still held
        val emissions = requests
            .asFlow()
            .tiles(tiler)
            .flattened()
            .take(2)
            .toList()

        assertEquals(
            1.testRange.toList(),
            emissions.last()
        )

        assertNull(withTimeoutOrNull(200) {
            requests
                .asFlow()
                .tiles(tiler)
                .sortAndFlatten(Int::compareTo)
                .take(3)
                .toList()
        })
    }
}

private fun Flow<List<Int>>.withRequestIndex(block: (Int) -> Unit) = onEach { items ->
    val requestIndex = items.size.div(1.testRange.toList().size) - 1
    if (requestIndex > -1) block(requestIndex)
}

private fun Flow<IntTiles>.flattened() =
    sortAndFlatten(Int::compareTo)
        .map { it.flatten() }
        .map { it.toList() }