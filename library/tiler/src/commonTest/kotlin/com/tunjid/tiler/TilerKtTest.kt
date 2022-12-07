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

import kotlin.test.*

class TilerKtTest {

    @Test
    fun maintains_all_items() {
        val tiled =
            (1..9)
                .mapIndexed { index, int ->
                    Tile.Output.Data(
                        query = int,
                        tile = Tile(
                            flowOnAt = index.toLong(),
                            items = int.testRange.toList()
                        )
                    )
                }
                .fold(
                    initial = Tiler(
                        limiter = Tile.Limiter { false },
                        order = Tile.Order.Sorted(comparator = Int::compareTo)
                    ),
                    operation = Tiler<Int, Int>::add
                )
                .output()

        assertEquals(
            (1..9).map(Int::testRange).flatten(),
            tiled
        )
    }

    @Test
    fun pivots_around_specific_query_when_limit_exists() {
        val tiles =
            (1..9).mapIndexed { index, int ->
                listOf(
                    Tile.Output.Data(
                        query = int,
                        tile = Tile(
                            flowOnAt = index.toLong(),
                            items = int.testRange.toList()
                        )
                    ),
                )
            }
                .flatten()
                .fold(
                    initial = Tiler(
                        limiter = Tile.Limiter { items -> items.size >= 50 },
                        order = Tile.Order.PivotSorted(
                            query = 4,
                            comparator = Int::compareTo
                        )
                    ),
                    operation = Tiler<Int, Int>::add
                )
                .output()

        assertEquals(
            (2..6).map(Int::testRange).flatten(),
            tiles
        )
    }

}
