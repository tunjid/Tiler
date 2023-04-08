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

import com.tunjid.tiler.assertTiledListEquals
import com.tunjid.tiler.buildTiledList
import com.tunjid.tiler.tiledListOf
import kotlin.test.Test

class ChunkedTiledListTest {

    @Test
    fun chunked_tiled_indexing_works() {
        (0..10).forEach {  chunkSize ->
            val (optimizedChunkedTiledList, iterativeChunkedTiledList) =
                optimizedAndIterativeChunkedLists(chunkSize)

            val expectedTiledList = consecutiveIntegerTiledList(
                chunkSize = chunkSize
            )
            assertTiledListEquals(
                expected = expectedTiledList,
                actual = optimizedChunkedTiledList
            )
            assertTiledListEquals(
                expected = optimizedChunkedTiledList,
                actual = iterativeChunkedTiledList
            )
        }
    }

    @Test
    fun chunked_tiled_indexing_works_if_last_chunk_is_partially_filled() {
        val (optimizedChunkedTiledList, iterativeChunkedTiledList) =
            optimizedAndIterativeChunkedLists(chunkSize = 10)

        // Add items at the end that are not equal to chunk size
        optimizedChunkedTiledList.addRight(
            query = 10,
            items = listOf(100, 101, 102)
        )
        iterativeChunkedTiledList.addRight(
            query = 10,
            items = listOf(100, 101, 102)
        )
        val expectedTiledList = consecutiveIntegerTiledList(
            chunkSize = 10,
            upUntilInt = 103
        )

        assertTiledListEquals(
            expected = expectedTiledList,
            actual = optimizedChunkedTiledList
        )
        assertTiledListEquals(
            expected = optimizedChunkedTiledList,
            actual = iterativeChunkedTiledList
        )
    }

    @Test
    fun chunked_tiled_pivoting_works() {
        val (optimizedChunkedTiledList, iterativeChunkedTiledList) =
            optimizedAndIterativeChunkedLists(chunkSize = 3)

        optimizedChunkedTiledList.addLeft(
            query = -1,
            items = listOf(-3, -2, -1)
        )
        iterativeChunkedTiledList.addLeft(
            query = -1,
            items = listOf(-3, -2, -1)
        )
        val expectedTiledList = buildTiledList {
            addAll(
                query = -1,
                items = listOf(-3, -2, -1)
            )
            consecutiveIntegerTiledList(
                chunkSize = 3
            ).let { tiledList ->
                tiledList. forEachIndexed { index, item -> add(tiledList.queryAt(index), item) }
            }
        }
        assertTiledListEquals(
            expected = expectedTiledList,
            actual = optimizedChunkedTiledList
        )
        assertTiledListEquals(
            expected = optimizedChunkedTiledList,
            actual = iterativeChunkedTiledList
        )
    }
}

private fun optimizedAndIterativeChunkedLists(
    chunkSize: Int
): Pair<ChunkedTiledList<Int, Int>, ChunkedTiledList<Int, Int>> {
    val optimizedChunkedTiledList = ChunkedTiledList<Int, Int>(
        chunkSizeHint = chunkSize,
        maxNumberOfChunks = chunkSize
    )
    val iterativeChunkedTiledList = ChunkedTiledList<Int, Int>(
        chunkSizeHint = null,
        maxNumberOfChunks = chunkSize
    )

    (0 until chunkSize).forEach { index ->
        val offset = index * chunkSize
        optimizedChunkedTiledList.addRight(
            query = index,
            items = (0 until chunkSize).map(offset::plus)
        )
        iterativeChunkedTiledList.addRight(
            query = index,
            items = (0 until chunkSize).map(offset::plus)
        )
    }
    return Pair(optimizedChunkedTiledList, iterativeChunkedTiledList)
}

private fun consecutiveIntegerTiledList(
    chunkSize: Int,
    upUntilInt: Int = chunkSize * chunkSize
) = tiledListOf(
    *(0 until upUntilInt)
        .map { item ->
            val query = item / chunkSize
            query to item
        }
        .toTypedArray()
)
