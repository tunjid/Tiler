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

import com.tunjid.tiler.TiledList
import com.tunjid.tiler.assertTiledListEquals
import com.tunjid.tiler.tiledListOf
import com.tunjid.tiler.utilities.chunkedTiledList
import kotlin.test.Test

class ChunkedTiledListTest {

    @Test
    fun chunked_tiled_indexing_works() {
        (0..10).forEach {  chunkSize ->
            val (constantTimeChunkedTiledList, binarySearchChunkedTiledList) =
                optimizedAndIterativeChunkedLists(chunkSize)

            val expectedTiledList = consecutiveIntegerTiledList(
                chunkSize = chunkSize
            )
            assertTiledListEquals(
                expected = expectedTiledList,
                actual = constantTimeChunkedTiledList
            )
            assertTiledListEquals(
                expected = constantTimeChunkedTiledList,
                actual = binarySearchChunkedTiledList
            )
        }
    }
}

private fun optimizedAndIterativeChunkedLists(
    chunkSize: Int
): Pair<TiledList<Int, Int>, TiledList<Int, Int>> {
    val indices = (0 until chunkSize).toList()

    val constantTimeChunkedTiledList = chunkedTiledList(
        chunkSizeHint = chunkSize,
        indices = indices,
        queryLookup = indices::get,
        itemsLookup = { index ->
            val offset = index * chunkSize
            (0 until chunkSize).map(offset::plus)
        }
    )
    val binarySearchChunkedTiledList = chunkedTiledList(
        chunkSizeHint = null,
        indices = indices,
        queryLookup = indices::get,
        itemsLookup = { index ->
            val offset = index * chunkSize
            (0 until chunkSize).map(offset::plus)
        }
    )
    return Pair(constantTimeChunkedTiledList, binarySearchChunkedTiledList)
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
