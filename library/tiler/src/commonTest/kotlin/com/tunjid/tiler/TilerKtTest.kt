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
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class TilerKtTest {

    @Test
    fun maintains_all_items() = runTest {
        val tiled =
            (1..9)
                .map { int ->
                    Tile.Output.Data(
                        query = int,
                        items = int.testRange.toList()
                    )
                }
                .asFlow()
                .scan(
                    initial = ImmutableTiler(
                        metadata = Tile.Metadata(
                            limiter = Tile.Limiter { false },
                            order = Tile.Order.Sorted(comparator = Int::compareTo)
                        )
                    ),
                    operation = Tiler<Int, Int>::process
                )
                .last()
                .tiledItems()

        assertEquals(
            (1..9)
                .map(Int::tiledTestRange)
                .fold(tiledListOf(), TiledList<Int, Int>::plus),
            tiled
        )
    }

    @Test
    fun pivots_around_specific_query_when_limit_exists() = runTest {
        val tiles =
            (1..9).map { int ->
                listOf(
                    Tile.Output.Data(
                        query = int,
                        items = int.testRange.toList()
                    ),
                )
            }
                .flatten()
                .asFlow()
                .scan(
                    initial = ImmutableTiler(
                        metadata = Tile.Metadata(
                            limiter = Tile.Limiter { items -> items.size >= 50 },
                            order = Tile.Order.PivotSorted(
                                query = 4,
                                comparator = Int::compareTo
                            )
                        )
                    ),
                    operation = Tiler<Int, Int>::process
                )
                .last()
                .tiledItems()

        assertEquals(
            (2..6)
                .map(Int::tiledTestRange)
                .fold(tiledListOf(), TiledList<Int, Int>::plus),
            tiles
        )
    }

    @Test
    fun insertingAQueryMaintainsOrder() {
        assertEquals(
            expected = listOf(1, 2, 3, 4),
            actual = Tile.Metadata<Int, Int>(
                orderedQueries = listOf(1, 2, 3, 4),
                order = Tile.Order.Sorted(Int::compareTo)
            ).insertOrderedQuery(2),
            message = "inserting ordered query fails at de-duplicating"
        )

        assertEquals(
            expected = listOf(1, 2, 3, 4),
            actual = Tile.Metadata<Int, Int>(
                orderedQueries = listOf(1, 2, 4),
                order = Tile.Order.Sorted(Int::compareTo)
            ).insertOrderedQuery(3),
            message = "inserting ordered query fails at inserting in the middle"
        )

        assertEquals(
            expected = listOf(0, 1, 2, 4),
            actual = Tile.Metadata<Int, Int>(
                orderedQueries = listOf(1, 2, 4),
                order = Tile.Order.Sorted(Int::compareTo)
            ).insertOrderedQuery(0),
            message = "inserting ordered query fails at inserting in the beginning"
        )

        assertEquals(
            expected = listOf(1, 2, 4, 5),
            actual = Tile.Metadata<Int, Int>(
                orderedQueries = listOf(1, 2, 4),
                order = Tile.Order.Sorted(Int::compareTo)
            ).insertOrderedQuery(5),
            message = "inserting ordered query fails at inserting at the end"
        )
    }
}
