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

internal fun <Query, Item> Tile.Metadata<Query, Item>.toTiledList(
    queryItemsMap: Map<Query, List<Item>>,
): TiledList<Query, Item> {
    val orderedQueries = orderedQueries

    return when (val order = order) {
        is Tile.Order.Unspecified -> queryItemsMap.keys
            .foldWhile(MutablePairedTiledList(), limiter.check) { mutableTiledList, query ->
                val items = queryItemsMap.getValue(query)
                mutableTiledList.addAll(query = query, items = items)
                mutableTiledList
            }

        is Tile.Order.Sorted -> orderedQueries
            .foldWhile(MutablePairedTiledList(), limiter.check) { mutableTiledList, query ->
                val items = queryItemsMap.getValue(query)
                mutableTiledList.addAll(query = query, items = items)
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

            val tiledList = MutablePairedTiledList<Query, Item>()
            tiledList.addAll(query = query, items = items)

            while (!limiter.check(tiledList) && (leftIndex >= 0 || rightIndex <= orderedQueries.lastIndex)) {
                if (--leftIndex >= 0) {
                    query = orderedQueries[leftIndex]
                    items = queryItemsMap.getValue(query)
                    tiledList.addAll(index = 0, query = query, items = items)
                }
                if (++rightIndex <= orderedQueries.lastIndex && !limiter.check(tiledList)) {
                    query = orderedQueries[rightIndex]
                    items = queryItemsMap.getValue(query)
                    tiledList.addAll(query = query, items = items)
                }
            }
            tiledList
        }

        is Tile.Order.Custom -> order.transform(this, queryItemsMap)
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
