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

/**
 * A [TiledList] implementation that offers:
 * 1. Quick insertion for items at either end for a specified [Query] as shifting of items is done in chunks.
 * 2. Reasonably quick retrieval for large chunk sizes, when optimized, retrieval is constant time.
 *
 * For example given a list of 1000 items with a chunk size of 100. Adding another chunk of 100 items to the head of
 * the list will only require shifting 10 items instead of 1000 with a conventional list.
 * Retrieving the 1000th item however will take 10 iterations.
 *
 * This is fine provided that tiled lists should be limited to items that can reasonably fit in a UI container.
 */
internal class ChunkedTiledList<Query, Item>(
    private val chunkSize: Int?,
    private val maxNumberOfChunks: Int,
) : AbstractList<Item>(), TiledList<Query, Item> {

    override var size: Int = 0
        private set

    // TODO: Is it better to allocate two separate lists, or to allocate a single list and
    //  create a new [Pair] for every chunk inserted?
    private val queries = ArrayList<Query>(maxNumberOfChunks)
    private val chunkedItems = ArrayList<List<Item>>(maxNumberOfChunks)

    fun addLeft(query: Query, items: List<Item>) {
        size += items.size
        queries.add(index = 0, element = query)
        chunkedItems.add(index = 0, element = items)
    }

    fun addRight(query: Query, items: List<Item>) {
        size += items.size
        queries.add(element = query)
        chunkedItems.add(element = items)
    }

    override fun queryAt(index: Int): Query = withItemAtIndex(
        index
    ) { chunkIndex, _ -> queries[chunkIndex] }

    override fun get(index: Int): Item = withItemAtIndex(
        index
    ) { chunkIndex, sum -> chunkedItems[chunkIndex][index - sum] }

    private inline fun <T> withItemAtIndex(
        index: Int,
        retriever: ChunkIndexRetriever<T>
    ): T {
        if (chunkSize != null) return retriever.get(
            chunkIndex = index / chunkSize,
            sum = index - (index % chunkSize)
        )
        var sum = 0
        for (chunkIndex in 0..lastIndex) {
            if (sum + chunkedItems[chunkIndex].size <= index) sum += chunkedItems[chunkIndex].size
            else return retriever.get(chunkIndex, sum)
        }
        throw IndexOutOfBoundsException("Tried to retrieve index $index in List of size $size")
    }

}

private fun interface ChunkIndexRetriever<T> {
    fun get(chunkIndex: Int, sum: Int): T
}
