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

import com.tunjid.tiler.Tile
import com.tunjid.tiler.TiledList
import com.tunjid.tiler.strictEquals

/**
 * A sorted read only [TiledList] implementation that offers O(1) retrieval of items if the size
 * is known ahead of time or O(log(n)) time otherwise.
 */
internal inline fun <Query, Item> chunkedTiledList(
    chunkSizeHint: Int?,
    indices: IntArrayList,
    crossinline queryLookup: (Int) -> Query,
    crossinline itemsLookup: (Query) -> List<Item>,
): TiledList<Query, Item> {

    val numberOfChunks = indices.size
    val chunkSizes = IntArray(numberOfChunks)
    val queries = arrayOfNulls<Any>(numberOfChunks)
    val chunkedItems = arrayOfNulls<List<Item>>(numberOfChunks)

    var size = 0

    for (i in 0 until numberOfChunks) {
        val query = queryLookup(indices[i])
        val items = itemsLookup(query)
        size += items.size
        chunkSizes[i] = size
        queries[i] = query
        chunkedItems[i] = items
    }

    return ChunkedTiledList(
        chunkSizeHint = chunkSizeHint,
        size = size,
        chunkSizes = chunkSizes,
        queries = queries,
        chunkedItems = chunkedItems,
    )
}

internal class ChunkedTiledList<Query, Item>(
    override val size: Int,
    private val chunkSizeHint: Int?,
    private val chunkSizes: IntArray,
    private val queries: Array<Any?>,
    private val chunkedItems: Array<List<Item>?>,
) : AbstractList<Item>(), TiledList<Query, Item> {

    override val tileCount: Int = queries.size

    override fun tileAt(index: Int): Tile = Tile(
        start = if (index == 0) 0 else chunkSizes[index - 1],
        end = chunkSizes[index]
    )

    @Suppress("UNCHECKED_CAST")
    override fun queryAtTile(index: Int): Query = queries[index] as Query

    @Suppress("UNCHECKED_CAST")
    override fun queryAt(index: Int): Query = withItemAtIndex(
        index
    ) { chunkIndex, _ -> queries[chunkIndex] as Query }

    @Suppress("UNCHECKED_CAST")
    override fun get(index: Int): Item = withItemAtIndex(
        index
    ) { chunkIndex, indexInChunk -> chunkedItems[chunkIndex]?.get(indexInChunk) as Item }

    override fun hashCode(): Int =
        (31 * "ChunkedTiledList".hashCode()) + super.hashCode()

    override fun equals(other: Any?): Boolean =
        if (other is TiledList<*, *>) strictEquals(other)
        else super.equals(other)

    private inline fun <T> withItemAtIndex(
        index: Int,
        crossinline retriever: (chunkIndex: Int, indexInChunk: Int) -> T
    ): T {
        if (isEmpty()) throw IndexOutOfBoundsException(
            "Trying to read $index in empty TiledList"
        )
        // Get item in constant time
        if (chunkSizeHint != null) return retriever(
            index / chunkSizeHint,
            index % chunkSizeHint
        )
        val chunkIndex = chunkSizes.findIndexInChunkSizes(index)

        // Get Item in O(log(N)) time
        return retriever(
            chunkIndex,
            when (chunkIndex) {
                0 -> index
                else -> index - chunkSizes[chunkIndex - 1]
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