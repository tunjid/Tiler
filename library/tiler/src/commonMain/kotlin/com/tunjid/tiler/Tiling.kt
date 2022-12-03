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

internal fun <Query, Item> Map<Query, Tile<Query, Item>>.tileWith(
    metadata: Tile.Metadata<Query>,
    order: Tile.Order<Query, Item>,
    limiter: Tile.Limiter<Query, Item>
): TiledList<Query, Item> = listTiling(
    metadata = metadata,
    order = order,
    limiter = limiter
)

private fun <Query, Item> Map<Query, Tile<Query, Item>>.listTiling(
    metadata: Tile.Metadata<Query>,
    order: Tile.Order<Query, Item>,
    limiter: Tile.Limiter<Query, Item>
): TiledList<Query, Item> {
    val queryToTiles = this
    val sortedQueries = metadata.sortedQueries

    return when (order) {
        is Tile.Order.Unspecified -> queryToTiles.keys
            .foldWhile(MutablePairedTiledList(), limiter.check) { mutableTiledList, query ->
                val items = queryToTiles.getValue(query).items
                mutableTiledList.queryItemPairs.addAll(elements = query.pairWith(items))
                mutableTiledList
            }

        is Tile.Order.Sorted -> sortedQueries
            .foldWhile(MutablePairedTiledList(), limiter.check) { mutableTiledList, query ->
                val items = queryToTiles.getValue(query).items
                mutableTiledList.queryItemPairs.addAll(elements = query.pairWith(items))
                mutableTiledList
            }

        is Tile.Order.PivotSorted -> {
            if (sortedQueries.isEmpty()) return emptyTiledList()

            val mostRecentQuery: Query = metadata.mostRecentlyTurnedOn ?: return emptyTiledList()
            val startIndex = sortedQueries.binarySearch(mostRecentQuery, order.comparator)

            if (startIndex < 0) return emptyTiledList()

            var leftIndex = startIndex
            var rightIndex = startIndex
            var query = sortedQueries[startIndex]
            var items = queryToTiles.getValue(query).items

            val tiledList = MutablePairedTiledList<Query, Item>()
            tiledList.queryItemPairs.addAll(elements = query.pairWith(items))


            while (!limiter.check(tiledList) && (leftIndex >= 0 || rightIndex <= sortedQueries.lastIndex)) {
                if (--leftIndex >= 0) {
                    query = sortedQueries[leftIndex]
                    items = queryToTiles.getValue(query).items
                    tiledList.queryItemPairs.addAll(index = 0, elements = query.pairWith(items))
                }
                if (++rightIndex <= sortedQueries.lastIndex) {
                    query = sortedQueries[rightIndex]
                    items = queryToTiles.getValue(query).items
                    tiledList.queryItemPairs.addAll(elements = query.pairWith(items))
                }
            }
            tiledList
        }

        is Tile.Order.Custom -> order.transform(metadata, queryToTiles)
    }
}

private fun <Item, Query> Query.pairWith(
    items: List<Item>
) = items.map { this to it }

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
