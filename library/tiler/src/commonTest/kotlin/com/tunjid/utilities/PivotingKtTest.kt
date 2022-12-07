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

import com.tunjid.tiler.utilities.PivotRequest
import com.tunjid.tiler.utilities.PivotResult
import com.tunjid.tiler.utilities.pivotAround
import com.tunjid.tiler.utilities.pivotWith
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals


class PivotingKtTest {

    private val pivotRequest = PivotRequest<Int>(
        onCount = 3,
        offCount = 4,
        nextQuery = { this + 1 },
        previousQuery = { (this - 1).takeIf { it >= 0 } },
    )

    @Test
    fun pivoting_works_on_both_sides() {
        assertEquals(
            expected = PivotResult(
                currentQuery = 7,
                on = listOf(6, 7, 8).sortedByFurthestDistanceFrom(7),
                off = listOf(4, 5, 9, 10).sortedByFurthestDistanceFrom(7),
                evict = emptyList(),
            ),
            actual = pivotRequest.pivotAround(7)
        )
    }

    @Test
    fun pivoting_works_on_one_side() {
        assertEquals(
            expected = PivotResult(
                currentQuery = 0,
                on = listOf(0, 1, 2).sortedByFurthestDistanceFrom(0),
                off = listOf(3, 4, 5, 6).sortedByFurthestDistanceFrom(0),
                evict = emptyList(),
            ),
            actual = pivotRequest.pivotAround(0)
        )
    }

    @Test
    fun flow_of_queries_can_be_pivoted() = runTest {
        val queries = listOf(
            0,
            1,
            2,
            3,
            4,
        ).asFlow()

        val pivotResults = queries.pivotWith(pivotRequest).toList()

        assertEquals(
            PivotResult(),
            pivotResults[0]
        )

        assertEquals(
            PivotResult(
                currentQuery = 0,
                on = listOf(0, 1, 2).sortedByFurthestDistanceFrom(0),
                off = listOf(3, 4, 5, 6).sortedByFurthestDistanceFrom(0),
                evict = emptyList(),
            ),
            pivotResults[1]
        )

        assertEquals(
            PivotResult(
                currentQuery = 1,
                on = listOf(0, 1, 2).sortedByFurthestDistanceFrom(1),
                off = listOf(3, 4, 5, 6).sortedByFurthestDistanceFrom(1),
                evict = emptyList(),
            ),
            pivotResults[2]
        )

        assertEquals(
            PivotResult(
                currentQuery = 2,
                on = listOf(1, 2, 3).sortedByFurthestDistanceFrom(2),
                off = listOf(0, 4, 5, 6).sortedByFurthestDistanceFrom(2),
                evict = emptyList(),
            ),
            pivotResults[3]
        )

        assertEquals(
            PivotResult(
                currentQuery = 3,
                on = listOf(2, 3, 4).sortedByFurthestDistanceFrom(3),
                off = listOf(0, 1, 5, 6).sortedByFurthestDistanceFrom(3),
                evict = emptyList(),
            ),
            pivotResults[4]
        )

        assertEquals(
            PivotResult(
                currentQuery = 4,
                on = listOf(3, 4, 5).sortedByFurthestDistanceFrom(4),
                off = listOf(1, 2, 6, 7).sortedByFurthestDistanceFrom(4),
                evict = listOf(0),
            ),
            pivotResults[5]
        )
    }

    @Test
    fun flow_of_queries_can_be_pivoted_with_jumps() = runTest {
        val queries = listOf(
            0,
            3,
            7,
            17,
            0,
        ).asFlow()
        val pivotResults = queries.pivotWith(pivotRequest).toList()

        assertEquals(
            PivotResult(),
            pivotResults[0]
        )

        assertEquals(
            PivotResult(
                currentQuery = 0,
                on = listOf(0, 1, 2).sortedByFurthestDistanceFrom(0),
                off = listOf(3, 4, 5, 6).sortedByFurthestDistanceFrom(0),
                evict = emptyList(),
            ), pivotResults[1]
        )

        assertEquals(
            PivotResult(
                currentQuery = 3,
                on = listOf(2, 3, 4).sortedByFurthestDistanceFrom(3),
                off = listOf(0, 1, 5, 6).sortedByFurthestDistanceFrom(3),
                evict = emptyList(),
            ), pivotResults[2]
        )

        assertEquals(
            PivotResult(
                currentQuery = 7,
                on = listOf(6, 7, 8).sortedByFurthestDistanceFrom(7),
                off = listOf(4, 5, 9, 10).sortedByFurthestDistanceFrom(7),
                evict = listOf(2, 3, 0, 1).sortedByFurthestDistanceFrom(3),
            ), pivotResults[3]
        )

        assertEquals(
            PivotResult(
                currentQuery = 17,
                on = listOf(16, 17, 18).sortedByFurthestDistanceFrom(17),
                off = listOf(14, 15, 19, 20).sortedByFurthestDistanceFrom(17),
                evict = listOf(6, 7, 8, 4, 5, 9, 10).sortedByFurthestDistanceFrom(7),
            ), pivotResults[4]
        )

        assertEquals(
            PivotResult(
                currentQuery = 0,
                on = listOf(0, 1, 2).sortedByFurthestDistanceFrom(0),
                off = listOf(3, 4, 5, 6).sortedByFurthestDistanceFrom(0),
                evict = listOf(16, 17, 18, 14, 15, 19, 20).sortedByFurthestDistanceFrom(17),
            ), pivotResults[5]
        )
    }

    @Test
    fun flow_of_queries_can_be_pivoted_with_flow_of_pivot_requests() = runTest {
        val queriesAndRequests = listOf(
            0,
            1,
            pivotRequest.copy(onCount = 5),
            2,
            pivotRequest,
        )
            .asFlow()
            .onEach { delay(100) }

            .shareIn(
                scope = this,
                started = SharingStarted.Lazily,
                replay = 6
            )

        val queries = queriesAndRequests.filterIsInstance<Int>()
            .onStart { emit(0) }
        val pivotRequests = queriesAndRequests
            .filterIsInstance<PivotRequest<Int>>()
            .onStart { emit(pivotRequest) }

        val pivotResults = queries.pivotWith(pivotRequests)
            .take(6)
            .toList()

        assertEquals(
            PivotResult(),
            pivotResults[0]
        )

        assertEquals(
            PivotResult(
                currentQuery = 0,
                on = listOf(0, 1, 2).sortedByFurthestDistanceFrom(0),
                off = listOf(3, 4, 5, 6).sortedByFurthestDistanceFrom(0),
                evict = emptyList(),
            ), pivotResults[1]
        )

        assertEquals(
            PivotResult(
                currentQuery = 1,
                on = listOf(0, 1, 2).sortedByFurthestDistanceFrom(1),
                off = listOf(3, 4, 5, 6).sortedByFurthestDistanceFrom(1),
                evict = emptyList(),
            ), pivotResults[2]
        )

        // The expanded pivot request should allow for more on queries
        assertEquals(
            PivotResult(
                currentQuery = 1,
                on = listOf(0, 1, 2, 3, 4).sortedByFurthestDistanceFrom(1),
                off = listOf(5, 6, 7, 8).sortedByFurthestDistanceFrom(1),
                evict = emptyList(),
            ), pivotResults[3]
        )

        assertEquals(
            PivotResult(
                currentQuery = 2,
                on = listOf(0, 1, 2, 3, 4).sortedByFurthestDistanceFrom(2),
                off = listOf(5, 6, 7, 8).sortedByFurthestDistanceFrom(2),
                evict = emptyList(),
            ), pivotResults[4]
        )

        // The contracted pivot request should allow for less on queries
        assertEquals(
            PivotResult(
                currentQuery = 2,
                on = listOf(1, 2, 3).sortedByFurthestDistanceFrom(2),
                off = listOf(0, 4, 5, 6).sortedByFurthestDistanceFrom(2),
                evict = listOf(7, 8).sortedByFurthestDistanceFrom(2),
            ),
            pivotResults[5]
        )
    }
}

// Even though the order of the list doesn't matter, I need to enforce it for equality tests
private fun List<Int>.sortedByFurthestDistanceFrom(pivot: Int) = sortedWith(
    compareBy<Int> { item ->
        val distance = pivot - item
        if (distance < 0) abs(distance).times(2)
        else distance.times(2) + 1
    }.reversed()
)