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

import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
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
                on = listOf(6, 7, 8),
                off = listOf(4, 5, 9, 10),
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
                on = listOf(0, 1, 2),
                off = listOf(3, 4, 5, 6),
                evict = emptyList(),
            ),
            actual = pivotRequest.pivotAround(0)
        )
    }

    @Test
    fun flow_of_queries_can_be_pivoted() = runTest {
        val requests = listOf(
            0,
            1,
            2,
            3,
            4,
        ).asFlow()

        assertEquals(
            expected = listOf(
                PivotResult(),
                PivotResult(
                    currentQuery = 0,
                    on = listOf(0, 1, 2),
                    off = listOf(3, 4, 5, 6),
                    evict = emptyList(),
                ),
                PivotResult(
                    currentQuery = 1,
                    on = listOf(0, 1, 2),
                    off = listOf(3, 4, 5, 6),
                    evict = emptyList(),
                ),
                PivotResult(
                    currentQuery = 2,
                    on = listOf(1, 2, 3),
                    off = listOf(0, 4, 5, 6),
                    evict = emptyList(),
                ),
                PivotResult(
                    currentQuery = 3,
                    on = listOf(2, 3, 4),
                    off = listOf(0, 1, 5, 6),
                    evict = emptyList(),
                ),
                PivotResult(
                    currentQuery = 4,
                    on = listOf(3, 4, 5),
                    off = listOf(1, 2, 6, 7),
                    evict = listOf(0),
                ),
            ),
            actual = requests.pivotWith(pivotRequest).toList()
        )
    }

    @Test
    fun flow_of_queries_can_be_pivoted_with_jumps() = runTest {
        val requests = listOf(
            0,
            3,
            7,
            17,
            0,
        ).asFlow()

        assertEquals(
            expected = listOf(
                PivotResult(),
                PivotResult(
                    currentQuery = 0,
                    on = listOf(0, 1, 2),
                    off = listOf(3, 4, 5, 6),
                    evict = emptyList(),
                ),
                PivotResult(
                    currentQuery = 3,
                    on = listOf(2, 3, 4),
                    off = listOf(0, 1, 5, 6),
                    evict = emptyList(),
                ),
                PivotResult(
                    currentQuery = 7,
                    on = listOf(6, 7, 8),
                    off = listOf(4, 5, 9, 10),
                    evict = listOf(2, 3, 0, 1),
                ),
                PivotResult(
                    currentQuery = 17,
                    on = listOf(16, 17, 18),
                    off = listOf(14, 15, 19, 20),
                    evict = listOf(6, 7, 8, 4, 5, 9, 10),
                ),

                PivotResult(
                    currentQuery = 0,
                    on = listOf(0, 1, 2),
                    off = listOf(3, 4, 5, 6),
                    evict = listOf(16, 17, 18, 14, 15, 19, 20),
                ),
            ),
            actual = requests.pivotWith(pivotRequest).toList()
        )
    }
}