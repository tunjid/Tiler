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

internal fun <Query, Item> Map<Query, List<Item>>.tileWith(
    metadata: Tile.Metadata<Query, Item>,
): TiledList<Query, Item> {
    val queryToTiles = this
    val sortedQueries = metadata.sortedQueries

    return when (val order = metadata.order) {
        is Tile.Order.Unspecified -> queryToTiles.keys
            .foldWhile(MutablePairedTiledList(), metadata.limiter.check) { mutableTiledList, query ->
                val items = queryToTiles.getValue(query)
                mutableTiledList.addAll(query = query, items = items)
                mutableTiledList
            }

        is Tile.Order.Sorted -> sortedQueries
            .foldWhile(MutablePairedTiledList(), metadata.limiter.check) { mutableTiledList, query ->
                val items = queryToTiles.getValue(query)
                mutableTiledList.addAll(query = query, items = items)
                mutableTiledList
            }

        is Tile.Order.PivotSorted -> {
            if (sortedQueries.isEmpty()) return emptyTiledList()

            val pivotQuery: Query = order.query ?: return emptyTiledList()
            val startIndex = sortedQueries.binarySearch(pivotQuery, order.comparator)

            if (startIndex < 0) return emptyTiledList()

            var leftIndex = startIndex
            var rightIndex = startIndex
            var query = sortedQueries[startIndex]
            var items = queryToTiles.getValue(query)

            val tiledList = MutablePairedTiledList<Query, Item>()
            tiledList.addAll(query = query, items = items)


            while (!metadata.limiter.check(tiledList) && (leftIndex >= 0 || rightIndex <= sortedQueries.lastIndex)) {
                if (--leftIndex >= 0) {
                    query = sortedQueries[leftIndex]
                    items = queryToTiles.getValue(query)
                    tiledList.addAll(index = 0, query = query, items = items)
                }
                if (++rightIndex <= sortedQueries.lastIndex) {
                    query = sortedQueries[rightIndex]
                    items = queryToTiles.getValue(query)
                    tiledList.addAll(query = query, items = items)
                }
            }
            tiledList
        }

        is Tile.Order.Custom -> order.transform(metadata, queryToTiles)
    }
}

private inline fun <T, R> Iterable<T>.foldWhile(
    initial: R,
    limiter: (R) -> Boolean,
    operation: (acc: R, T) -> R
): R {
    var accumulator = initial
    for (element in this) {
        accumulator = operation(accumulator, element)
        if (limiter(accumulator)) return accumulator
    }
    return accumulator
}
