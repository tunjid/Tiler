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
 * Experimental [TiledList] implementation that provides near constant time access for items
 * at an index as well as the query that fetched it
 */
@Suppress("unused")
internal class MutableMappedTiledList<Query, Item>(
    private val orderedKeys: MutableList<Query> = mutableListOf(),
    private val queryItemMap: MutableMap<Query, List<Item>> = mutableMapOf(),
) : AbstractList<Item>(), TiledList<Query, Item> {

    override val size: Int get() = queryItemMap.values.sumOf(List<Item>::size)

    // Provides O(n/chunkSize) access to items, however given the viewport of mobile devices,
    // n is typically < 6. This allows for practically constant time access.
    override fun get(index: Int): Item = infoForIndex(index).second

    override fun queryFor(index: Int): Query = infoForIndex(index).first

    private fun infoForIndex(index: Int): Pair<Query, Item> {
        var left = 0
        var right = 0
        var keyIndex = 0
        val size = size
        while (right < size) {
            val currentChunkQuery = orderedKeys[keyIndex]
            val currentChunk = queryItemMap.getValue(currentChunkQuery)

            right += currentChunk.size

            // Find item
            if (right > index) {
                val difference = index - left
                return currentChunkQuery to currentChunk[difference]
            }

            ++keyIndex
            left = right
        }
        throw IndexOutOfBoundsException("Trying to access index $index in TiledList.kt of size $size")
    }
}