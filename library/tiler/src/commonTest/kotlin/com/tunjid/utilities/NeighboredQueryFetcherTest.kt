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

package com.tunjid.utilities

import com.tunjid.tiler.invoke
import com.tunjid.tiler.tiledTestRange
import com.tunjid.tiler.toListWithTimeout
import com.tunjid.tiler.utilities.NeighboredFetchResult
import com.tunjid.tiler.utilities.NeighbouredQueryFetcher
import com.tunjid.tiler.utilities.neighboredQueryFetcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NeighboredQueryFetcherTest {

    @Test
    fun can_fetch_seed_item() = runTest {
        val fetcher = testTokenizedQueryFetcher()
        assertEquals(
            expected = 0.tiledTestRange(),
            actual = fetcher.invoke(query = 0).first()
        )
    }

    @Test
    fun cannot_fetch_query_till_adjacent_query_is_available() = runTest {
        val fetcher = testTokenizedQueryFetcher()

        // 0 is unavailable, so 1 should produce nothing
        assertTrue(
            fetcher.invoke(query = 1)
                .toListWithTimeout(10)
                .isEmpty()
        )

        // Fetch for 0
        assertEquals(
            expected = 0.tiledTestRange(),
            actual = fetcher.invoke(query = 0).first()
        )

        // Fetch for 1
        assertEquals(
            expected = 1.tiledTestRange(),
            actual = fetcher.invoke(query = 1).first()
        )
    }

    @Test
    fun tokens_are_evicted_LIFO() = runTest {
        val fetcher = testTokenizedQueryFetcher(maxTokens = 5)
        (0..6).forEach { page ->
            assertEquals(
                expected = page.tiledTestRange(),
                actual = fetcher.invoke(query = page).first()
            )
        }
        val tokenizedQueryFetcher = fetcher as NeighbouredQueryFetcher<*, *, *>

        assertEquals(
            expected = 5,
            actual = tokenizedQueryFetcher.queriesToTokens.value.keys.size
        )
        assertEquals(
            expected = (3..7).toList(),
            actual = tokenizedQueryFetcher.queriesToTokens.value.keys.toList()
        )
    }

    @Test
    fun exception_is_thrown_if_no_seed() = runTest {
        val fetcher = testTokenizedQueryFetcher(maxTokens = 5)
        (0..6).forEach { page ->
            assertEquals(
                expected = page.tiledTestRange(),
                actual = fetcher.invoke(query = page).first()
            )
        }
        val tokenizedQueryFetcher = fetcher as NeighbouredQueryFetcher<*, *, *>

        assertEquals(
            expected = 5,
            actual = tokenizedQueryFetcher.queriesToTokens.value.keys.size
        )
        assertEquals(
            expected = (3..7).toList(),
            actual = tokenizedQueryFetcher.queriesToTokens.value.keys.toList()
        )
    }
}

private fun testTokenizedQueryFetcher(
    maxTokens: Int = 5,
) = neighboredQueryFetcher(
    maxTokens = maxTokens,
    seedQueryTokenMap = mapOf(0 to "a token"),
    fetcher = { page, token ->
        val mockApiResult = MockApiResult(
            nextPageToken = token.hashCode().toString(),
            previousPageToken = if (page >= 0) token.reversed().hashCode().toString() else null,
            items = page.tiledTestRange()
        )
        flowOf(
            NeighboredFetchResult(
                neighborQueriesToTokens = listOfNotNull(
                    mockApiResult.nextPageToken?.let { page + 1 to it },
                    mockApiResult.previousPageToken?.let { page - 1 to it },
                )
                    .toMap(),
                items = mockApiResult.items
            )
        )
    }
)

private data class MockApiResult(
    val nextPageToken: String?,
    val previousPageToken: String?,
    val items: List<Int>,
)