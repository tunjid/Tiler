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

import com.tunjid.utilities.MutablePairedTiledList
import com.tunjid.utilities.PivotedTiledList
import kotlin.math.min

internal fun <Query, Item> Tile.Metadata<Query, Item>.toTiledList(
    queryItemsMap: Map<Query, List<Item>>,
): TiledList<Query, Item> {
    val orderedQueries = orderedQueries

    return when (val order = order) {

        is Tile.Order.Sorted -> (0 until min(limitedSize(), orderedQueries.size))
            .fold(MutablePairedTiledList()) { mutableTiledList, index ->
                mutableTiledList.addAll(
                    query = orderedQueries[index],
                    items = queryItemsMap.getValue(orderedQueries[index])
                )
                mutableTiledList
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

            val pivotedTiledList = PivotedTiledList<Query, Item>()

            val max = min(limitedSize(), orderedQueries.size)

            // Check if adding the pivot index will cause it to go over the limit
            if (max < 1) return pivotedTiledList
            pivotedTiledList.addLeft(query = query, items = items)

            var count = 1
            while (count < max && (leftIndex >= 0 || rightIndex <= orderedQueries.lastIndex)) {
                if (--leftIndex >= 0 && ++count <= max) {
                    query = orderedQueries[leftIndex]
                    items = queryItemsMap.getValue(query)
                    pivotedTiledList.addLeft(query = query, items = items)
                }
                if (++rightIndex <= orderedQueries.lastIndex && ++count <= max) {
                    query = orderedQueries[rightIndex]
                    items = queryItemsMap.getValue(query)
                    pivotedTiledList.addRight(query = query, items = items)
                }
            }
            pivotedTiledList
        }

        is Tile.Order.Custom -> order.transform(this, queryItemsMap)
    }
}

private fun <Query, Item> Tile.Metadata<Query, Item>.limitedSize() =
    when (val numQueries = limiter.maxQueries) {
        in Int.MIN_VALUE..-1 -> orderedQueries.size
        else -> numQueries
    }
