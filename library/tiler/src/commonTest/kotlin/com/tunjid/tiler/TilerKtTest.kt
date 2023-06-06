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

import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class TilerKtTest {

    @Test
    fun maintains_all_items() = runTest {
        val metadata = Metadata<Int, Int>(
            limiter = Tile.Limiter(
                maxQueries = Int.MIN_VALUE,
                itemSizeHint = 10
            ),
            order = Tile.Order.Sorted(comparator = Int::compareTo)
        )
        val tiled =
            (1..9)
                .map { int ->
                    Tile.Output.Data(
                        query = int,
                        items = int.testRange().toList()
                    )
                }
                .asFlow()
                .map(metadata::process)
                .last()

        assertEquals(
            (1..9)
                .map(Int::tiledTestRange)
                .fold(tiledListOf(), TiledList<Int, Int>::plus),
            tiled
        )
    }

    @Test
    fun pivots_around_specific_query_when_limit_exists() = runTest {
        val metadata = Metadata<Int, Int>(
            limiter = Tile.Limiter(
                maxQueries = 5,
                itemSizeHint = 10
            ),
            order = Tile.Order.PivotSorted(
                query = 4,
                comparator = Int::compareTo
            )
        )
        val tiles =
            (1..9).map { int ->
                listOf(
                    Tile.Output.Data(
                        query = int,
                        items = int.testRange().toList()
                    ),
                )
            }
                .flatten()
                .asFlow()
                .map(metadata::process)
                .toList()

        assertEquals(
            listOf(
                // 1 - 3, pivot hasn't been seen so null will be emitted
                null,
                null,
                null,
                // 4, Take as many items that can be be seen in the pivot range
                (1..4)
                    .map(Int::tiledTestRange)
                    .fold(tiledListOf(), TiledList<Int, Int>::plus),
                // 5, Take as many items that can be be seen in the pivot range
                (1..5)
                    .map(Int::tiledTestRange)
                    .fold(tiledListOf(), TiledList<Int, Int>::plus),
                // 6, Balanced pivot
                (2..6)
                    .map(Int::tiledTestRange)
                    .fold(tiledListOf(), TiledList<Int, Int>::plus),
                // 7 - 9, outside of visible limiter range so null will be emitted
                null,
                null,
                null,
            ),
            tiles
        )
    }

    @Test
    fun insertingAQueryMaintainsOrder() {
        assertEquals(
            expected = listOf(1, 2, 3, 4),
            actual = mutableListOf(1, 2, 3, 4).apply {
                insertSorted(
                    query = 2,
                    comparator = Int::compareTo
                )
            },
            message = "inserting ordered query fails at de-duplicating"
        )

        assertEquals(
            expected = listOf(1, 2, 3, 4),
            actual = mutableListOf(1, 2, 4).apply {
                insertSorted(
                    query = 3,
                    comparator = Int::compareTo
                )
            },
            message = "inserting ordered query fails at inserting in the middle"
        )

        assertEquals(
            expected = listOf(0, 1, 2, 4),
            actual = mutableListOf(1, 2, 4).apply {
                insertSorted(
                    query = 0,
                    comparator = Int::compareTo
                )
            },
            message = "inserting ordered query fails at inserting in the beginning"
        )

        assertEquals(
            expected = listOf(1, 2, 4, 5),
            actual = mutableListOf(1, 2, 4).apply {
                insertSorted(
                    query = 5,
                    comparator = Int::compareTo
                )
            },
            message = "inserting ordered query fails at inserting at the end"
        )
    }
}
