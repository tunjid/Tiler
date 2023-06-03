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

package com.tunjid.tiler.utilities

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
internal inline fun <Query, Item> chunkedTiledList(
    chunkSizeHint: Int?,
    indices: IntArrayList,
    crossinline queryLookup: (Int) -> Query,
    crossinline itemsLookup: (Query) -> List<Item>,
): TiledList<Query, Item> = object : AbstractList<Item>(), TiledList<Query, Item> {

    private var sizeIndex = -1
    private val sizes = IntArray(indices.size)

    // TODO: Is it better to allocate two separate lists, or to allocate a single list and
    //  create a new [Pair] for every chunk inserted?
    private val queries = ArrayList<Query>(indices.size)
    private val chunkedItems = ArrayList<List<Item>>(indices.size)

    override var size: Int = 0
        private set

    init {
        for (i in 0..indices.lastIndex) {
            val query = queryLookup(indices[i])
            val items = itemsLookup(query)
            size += items.size
            sizes[++sizeIndex] = size
            queries.add(element = query)
            chunkedItems.add(element = items)
        }
    }

    override fun queryAt(index: Int): Query = withItemAtIndex(
        index
    ) { chunkIndex, _ -> queries[chunkIndex] }

    override fun get(index: Int): Item = withItemAtIndex(
        index
    ) { chunkIndex, indexInChunk -> chunkedItems[chunkIndex][indexInChunk] }

    private inline fun <T> withItemAtIndex(
        index: Int,
        crossinline retriever: (chunkIndex: Int, indexInChunk: Int) -> T
    ): T {
        // Get item in constant time
        if (chunkSizeHint != null) return retriever(
            index / chunkSizeHint,
            index % chunkSizeHint
        )
        val chunkIndex = sizes.findIndexInChunkSizes(index)

        // Get Item in O(log(N)) time
        return retriever(
            chunkIndex,
            when (chunkIndex) {
                0 -> index
                else -> index - sizes[chunkIndex - 1]
            }
        )
    }
}

private fun IntArray.findIndexInChunkSizes(
    index: Int,
): Int {
    var low = 0
    var high = size - 1
    while (low <= high) {
        val mid = (low + high).ushr(1)
        val comparison = get(mid).compareTo(index)

        if (comparison < 0) low = mid + 1
        else if (comparison > 0) high = mid - 1
        else return mid + 1 // Found, the item is in the next chunk
    }

    return low
}