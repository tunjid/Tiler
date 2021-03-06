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
    fun `maintains all items`() {
        val tiled =
            (1..9)
                .mapIndexed { index, int ->
                    Tile.Output.Data(
                        query = int,
                        tile = Tile(
                            flowOnAt = index.toLong(),
                            item = int.testRange.toList()
                        )
                    )
                }
                .fold(
                    initial = Tiler(
                        limiter = Tile.Limiter.List { false },
                        order = Tile.Order.Sorted(comparator = Int::compareTo)
                    ),
                    operation = Tiler<Int, List<Int>, List<List<Int>>>::add
                )
                .output()
                .flatten()

        assertEquals(
            (1..9).map(Int::testRange).flatten(),
            tiled
        )
    }

    @Test
    fun `pivots around most recent when limit exists`() {
        val tiles =
            ((1..9).mapIndexed { index, int ->
                listOf(
                    Tile.Output.Data(
                        query = int,
                        tile = Tile(
                            flowOnAt = index.toLong(),
                            item = int.testRange.toList()
                        )
                    ),
                    Tile.Output.TurnedOn(
                        query = int
                    )
                )
            }
                .flatten()
                    + listOf(Tile.Output.TurnedOn(query = 4))
                    )
                .fold(
                    initial = Tiler(
                        limiter = Tile.Limiter.List { items -> items.fold(0) { count, list -> count + list.size } >= 50 },
                        order = Tile.Order.PivotSorted(comparator = Int::compareTo)
                    ),
                    operation = Tiler<Int, List<Int>, List<List<Int>>>::add
                )
                .output()
                .flatten()

        assertEquals(
            (2..6).map(Int::testRange).flatten(),
            tiles
        )
    }

}
