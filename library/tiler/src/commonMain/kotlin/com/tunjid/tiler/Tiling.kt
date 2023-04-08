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

import com.tunjid.utilities.ChunkedTiledList
import kotlin.math.min

internal fun <Query, Item> Tile.Metadata<Query, Item>.toTiledList(
    queryItemsMap: Map<Query, List<Item>>,
): TiledList<Query, Item> {
    return when (val order = order) {
        is Tile.Order.Sorted -> {
            var count = 0
            var index = -1
            val maxNumberOfChunks = min(limitedChunkSize(), orderedQueries.size)
            val chunkedTiledList = ChunkedTiledList<Query, Item>(
                chunkSizeHint = limiter.itemSizeHint,
                maxNumberOfChunks = maxNumberOfChunks
            )
            while (
                count < maxNumberOfChunks
                && index <= orderedQueries.lastIndex
            ) {
                if (++index <= orderedQueries.lastIndex
                    // Skip empty chunks
                    && queryItemsMap.getValue(orderedQueries[index]).isNotEmpty()
                    && ++count <= maxNumberOfChunks
                ) chunkedTiledList.addRight(
                    query = orderedQueries[index],
                    items = queryItemsMap.getValue(orderedQueries[index])
                )
            }
            chunkedTiledList
        }

        is Tile.Order.PivotSorted -> {
            if (orderedQueries.isEmpty()) return emptyTiledList()

            val pivotQuery: Query = order.query ?: return emptyTiledList()
            val startIndex = orderedQueries.binarySearch(pivotQuery, order.comparator)

            if (startIndex < 0) return emptyTiledList()

            var leftIndex = startIndex
            var rightIndex = startIndex
            val maxNumberOfChunks = min(limitedChunkSize(), orderedQueries.size)

            val chunkedTiledList = ChunkedTiledList<Query, Item>(
                chunkSizeHint = limiter.itemSizeHint,
                maxNumberOfChunks = maxNumberOfChunks
            )

            // Check if adding the pivot index will cause it to go over the limit
            if (maxNumberOfChunks < 1) return chunkedTiledList

            val pivotIndexIsEmpty = queryItemsMap.getValue(orderedQueries[startIndex]).isEmpty()
            if (!pivotIndexIsEmpty)chunkedTiledList.addLeft(
                query = orderedQueries[startIndex],
                items = queryItemsMap.getValue(orderedQueries[startIndex])
            )

            var count = if (pivotIndexIsEmpty) 0 else 1
            while (
                count < maxNumberOfChunks
                && (leftIndex >= 0 || rightIndex <= orderedQueries.lastIndex)
            ) {
                if (--leftIndex >= 0
                    // Skip empty chunks
                    && queryItemsMap.getValue(orderedQueries[leftIndex]).isNotEmpty()
                    && ++count <= maxNumberOfChunks
                ) chunkedTiledList.addLeft(
                    query = orderedQueries[leftIndex],
                    items = queryItemsMap.getValue(orderedQueries[leftIndex])
                )
                if (++rightIndex <= orderedQueries.lastIndex
                    // Skip empty chunks
                    && queryItemsMap.getValue(orderedQueries[rightIndex]).isNotEmpty()
                    && ++count <= maxNumberOfChunks
                ) chunkedTiledList.addRight(
                    query = orderedQueries[rightIndex],
                    items = queryItemsMap.getValue(orderedQueries[rightIndex])
                )
            }
            chunkedTiledList
        }

        is Tile.Order.Custom -> order.transform(this, queryItemsMap)
    }
}

private fun <Query, Item> Tile.Metadata<Query, Item>.limitedChunkSize() =
    when (val numQueries = limiter.maxQueries) {
        in Int.MIN_VALUE..-1 -> orderedQueries.size
        else -> numQueries
    }
