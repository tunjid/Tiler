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
    val orderedQueries = orderedQueries

    return when (val order = order) {

        is Tile.Order.Sorted -> (0 until min(limitedChunkSize(), orderedQueries.size)).let { range ->
            range.fold(
                ChunkedTiledList(
                    chunkSize = limiter.queryItemsSize,
                    maxNumberOfChunks = range.last - range.first
                )
            ) { chunkedTiledList, index ->
                chunkedTiledList.addRight(
                    query = orderedQueries[index],
                    items = queryItemsMap.getValue(orderedQueries[index])
                )
                chunkedTiledList
            }
        }

        is Tile.Order.PivotSorted -> {
            if (orderedQueries.isEmpty()) return emptyTiledList()

            val pivotQuery: Query = order.query ?: return emptyTiledList()
            val startIndex = orderedQueries.binarySearch(pivotQuery, order.comparator)

            if (startIndex < 0) return emptyTiledList()

            var leftIndex = startIndex
            var rightIndex = startIndex
            var query = orderedQueries[startIndex]
            var items = queryItemsMap.getValue(query)
            val maxNumberOfChunks = min(limitedChunkSize(), orderedQueries.size)

            val chunkedTiledList = ChunkedTiledList<Query, Item>(
                chunkSize = limiter.queryItemsSize,
                maxNumberOfChunks = maxNumberOfChunks
            )

            // Check if adding the pivot index will cause it to go over the limit
            if (maxNumberOfChunks < 1) return chunkedTiledList
            chunkedTiledList.addLeft(query = query, items = items)

            var count = 1
            while (count < maxNumberOfChunks && (leftIndex >= 0 || rightIndex <= orderedQueries.lastIndex)) {
                if (--leftIndex >= 0 && ++count <= maxNumberOfChunks) {
                    query = orderedQueries[leftIndex]
                    items = queryItemsMap.getValue(query)
                    chunkedTiledList.addLeft(query = query, items = items)
                }
                if (++rightIndex <= orderedQueries.lastIndex && ++count <= maxNumberOfChunks) {
                    query = orderedQueries[rightIndex]
                    items = queryItemsMap.getValue(query)
                    chunkedTiledList.addRight(query = query, items = items)
                }
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
