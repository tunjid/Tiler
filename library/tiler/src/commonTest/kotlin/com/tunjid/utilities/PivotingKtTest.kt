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

import com.tunjid.tiler.PivotRequest
import com.tunjid.tiler.Pivot
import com.tunjid.tiler.pivotWith
import com.tunjid.tiler.toPivotedTileInputs
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

    private val comparator: Comparator<Int> = Comparator<Int>(Int::compareTo)

    private val pivotRequest: PivotRequest<Int, Int> = PivotRequest(
        onCount = 3,
        offCount = 4,
        comparator = comparator,
        nextQuery = { this + 1 },
        previousQuery = { (this - 1).takeIf { it >= 0 } },
    )

    @Test
    fun pivoting_works_on_both_sides() {
        Pivot(
            query = 7,
            pivotRequest = pivotRequest,
            previousResult = null,
        ).assertEquals(
            query = 7,
            comparator = comparator,
            on = listOf(6, 7, 8).sortedByFurthestDistanceFrom(7),
            off = listOf(4, 5, 9, 10).sortedByFurthestDistanceFrom(7),
            evict = emptyList(),
        )
    }

    @Test
    fun pivoting_works_on_one_side() {
        Pivot(
            query = 0,
            pivotRequest = pivotRequest,
            previousResult = null,
        ).assertEquals(
            query = 0,
            comparator = comparator,
            on = listOf(0, 1, 2).sortedByFurthestDistanceFrom(0),
            off = listOf(3, 4, 5, 6).sortedByFurthestDistanceFrom(0),
            evict = emptyList(),
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

        pivotResults[0].assertEquals(
            query = 0,
            comparator = comparator,
            on = listOf(0, 1, 2).sortedByFurthestDistanceFrom(0),
            off = listOf(3, 4, 5, 6).sortedByFurthestDistanceFrom(0),
            evict = emptyList(),
        )

        pivotResults[1].assertEquals(
            query = 1,
            comparator = comparator,
            on = listOf(0, 1, 2).sortedByFurthestDistanceFrom(1),
            off = listOf(3, 4, 5, 6).sortedByFurthestDistanceFrom(1),
            evict = emptyList(),
        )

        pivotResults[2].assertEquals(
            query = 2,
            comparator = comparator,
            on = listOf(1, 2, 3).sortedByFurthestDistanceFrom(2),
            off = listOf(0, 4, 5, 6).sortedByFurthestDistanceFrom(2),
            evict = emptyList(),

            )

        pivotResults[3].assertEquals(
            query = 3,
            comparator = comparator,
            on = listOf(2, 3, 4).sortedByFurthestDistanceFrom(3),
            off = listOf(0, 1, 5, 6).sortedByFurthestDistanceFrom(3),
            evict = emptyList(),
        )

        pivotResults[4].assertEquals(
            query = 4,
            comparator = comparator,
            on = listOf(3, 4, 5).sortedByFurthestDistanceFrom(4),
            off = listOf(1, 2, 6, 7).sortedByFurthestDistanceFrom(4),
            evict = listOf(0),
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

        pivotResults[0].assertEquals(
            query = 0,
            comparator = comparator,
            on = listOf(0, 1, 2).sortedByFurthestDistanceFrom(0),
            off = listOf(3, 4, 5, 6).sortedByFurthestDistanceFrom(0),
            evict = emptyList(),
        )

        pivotResults[1].assertEquals(
            query = 3,
            comparator = comparator,
            on = listOf(2, 3, 4).sortedByFurthestDistanceFrom(3),
            off = listOf(0, 1, 5, 6).sortedByFurthestDistanceFrom(3),
            evict = emptyList(),
        )

        pivotResults[2].assertEquals(
            query = 7,
            comparator = comparator,
            on = listOf(6, 7, 8).sortedByFurthestDistanceFrom(7),
            off = listOf(4, 5, 9, 10).sortedByFurthestDistanceFrom(7),
            evict = listOf(2, 3, 0, 1).sortedByFurthestDistanceFrom(3),
        )

        pivotResults[3].assertEquals(
            query = 17,
            comparator = comparator,
            on = listOf(16, 17, 18).sortedByFurthestDistanceFrom(17),
            off = listOf(14, 15, 19, 20).sortedByFurthestDistanceFrom(17),
            evict = listOf(6, 7, 8, 4, 5, 9, 10).sortedByFurthestDistanceFrom(7),
        )

        pivotResults[4].assertEquals(
            query = 0,
            comparator = comparator,
            on = listOf(0, 1, 2).sortedByFurthestDistanceFrom(0),
            off = listOf(3, 4, 5, 6).sortedByFurthestDistanceFrom(0),
            evict = listOf(16, 17, 18, 14, 15, 19, 20).sortedByFurthestDistanceFrom(17),
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
                replay = 5
            )

        val queries = queriesAndRequests.filterIsInstance<Int>()
            .onStart { emit(0) }
        val pivotRequests = queriesAndRequests
            .filterIsInstance<PivotRequest<Int, Int>>()
            .onStart { emit(pivotRequest) }

        val pivotResults = queries.pivotWith(pivotRequests)
            .take(5)
            .toList()

        pivotResults[0].assertEquals(
            query = 0,
            comparator = comparator,
            on = listOf(0, 1, 2).sortedByFurthestDistanceFrom(0),
            off = listOf(3, 4, 5, 6).sortedByFurthestDistanceFrom(0),
            evict = emptyList(),
        )

        pivotResults[1].assertEquals(
            query = 1,
            comparator = comparator,
            on = listOf(0, 1, 2).sortedByFurthestDistanceFrom(1),
            off = listOf(3, 4, 5, 6).sortedByFurthestDistanceFrom(1),
            evict = emptyList(),
        )

        // The expanded pivot request should allow for more on queries
        pivotResults[2].assertEquals(
            query = 1,
            comparator = comparator,
            on = listOf(0, 1, 2, 3, 4).sortedByFurthestDistanceFrom(1),
            off = listOf(5, 6, 7, 8).sortedByFurthestDistanceFrom(1),
            evict = emptyList(),
        )

        pivotResults[3].assertEquals(
            query = 2,
            comparator = comparator,
            on = listOf(0, 1, 2, 3, 4).sortedByFurthestDistanceFrom(2),
            off = listOf(5, 6, 7, 8).sortedByFurthestDistanceFrom(2),
            evict = emptyList(),
        )

        // The contracted pivot request should allow for less on queries
        pivotResults[4].assertEquals(
            query = 2,
            comparator = comparator,
            on = listOf(1, 2, 3).sortedByFurthestDistanceFrom(2),
            off = listOf(0, 4, 5, 6).sortedByFurthestDistanceFrom(2),
            evict = listOf(7, 8).sortedByFurthestDistanceFrom(2),
        )
    }

    @Test
    fun pivoting_evicts_first_then_turns_off_before_turning_on_and_finally_ordering() = runTest {
        val queries = listOf(
            9,
            5,
        ).asFlow()

        val inputs = queries.pivotWith(pivotRequest)
            .toList()

        val firstPivotInput = Pivot(
            query = 9,
            pivotRequest = pivotRequest,
            previousResult = null,
        )
        firstPivotInput.assertEquals(
            query = 9,
            comparator = comparator,
            on = listOf(8, 9, 10).sortedByFurthestDistanceFrom(9),
            off = listOf(6, 7, 11, 12).sortedByFurthestDistanceFrom(9),
            evict = emptyList(),
        )
        assertEquals(
            expected = firstPivotInput,
            actual = inputs[0],
        )

        val secondPivotInput = Pivot(
            query = 5,
            pivotRequest = pivotRequest,
            previousResult = firstPivotInput,
        )
        secondPivotInput.assertEquals(
            query = 5,
            comparator = comparator,
            on = listOf(4, 5, 6).sortedByFurthestDistanceFrom(5),
            off = listOf(2, 3, 7, 8).sortedByFurthestDistanceFrom(5),
            evict = listOf(9, 10, 11, 12).sortedByFurthestDistanceFrom(5),
        )
        assertEquals(
            expected = secondPivotInput,
            actual = inputs[1],
        )
    }

    @Test
    fun to_tiled_inputs_calls_on_pivotWith() = runTest {
        val queries = listOf(
            9,
            5,
        ).asFlow()

        assertEquals(
            expected = queries.pivotWith(pivotRequest).toList(),
            actual = queries.toPivotedTileInputs(pivotRequest).toList()
        )
    }
}

// Pivoting requests are sent in the order of increasing distance from the pivot
private fun List<Int>.sortedByFurthestDistanceFrom(pivot: Int) = sortedWith(
    compareBy<Int> { item ->
        val distance = pivot - item
        if (distance < 0) abs(distance).times(2)
        else distance.times(2) + 1
    }.reversed()
)

private fun <Query, Item> Pivot<Query, Item>.assertEquals(
    query: Query,
    comparator: Comparator<Query>,
    on: List<Query>,
    off: List<Query>,
    evict: List<Query>,
) {
    assertEquals(
        expected = query,
        actual = this.query
    )
    assertEquals(
        expected = comparator,
        actual = this.comparator
    )
    assertEquals(
        expected = on,
        actual = this.on
    )
    assertEquals(
        expected = off,
        actual = this.off
    )
    assertEquals(
        expected = evict,
        actual = this.evict
    )
}