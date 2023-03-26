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

        is Tile.Order.Sorted -> orderedQueries
            .foldWhile(MutablePairedTiledList(), limiter.check) { mutableTiledList, query ->
                val items = queryItemsMap.getValue(query)
                for (item in items) {
                    if (limiter.check(mutableTiledList)) break
                    mutableTiledList.add(query = query, item = item)
                }
                mutableTiledList
            }

        is Tile.Order.PivotSorted -> {
            if (orderedQueries.isEmpty()) return emptyTiledList()

            val pivotQuery: Query = order.query ?: return emptyTiledList()
            val startIndex = orderedQueries.binarySearch(pivotQuery, order.comparator)

            if (startIndex < 0) return emptyTiledList()

            val tiledList = MutablePairedTiledList<Query, Item>()
            var i = -1
            while (true) {
                val query = orderedQueries[startIndex]
                val items = queryItemsMap.getValue(query)
                if (limiter.check(tiledList) || ++i > items.lastIndex) break
                tiledList.add(query = query, item = items[i])
            }

            val left = QueryAndList(
                isLeft = true,
                queryIndex = startIndex - 1,
                orderedQueries = orderedQueries,
                queryItemsMap = queryItemsMap,
            )
            val right = QueryAndList(
                isLeft = false,
                queryIndex = startIndex + 1,
                orderedQueries = orderedQueries,
                queryItemsMap = queryItemsMap,
            )

            while (!limiter.check(tiledList) && (left.inBounds() || right.inBounds())) {
                // TODO: This has unnecessary shifting on insertion.
                //  Write a more optimal DS that allows for insertion in the middle and fanning out
                //  Fix: Change signature of Limiter to take a size. Then create an array of that
                //  size and start to populate it from the middle.
                left.read()?.let { item ->
                    tiledList.add(index = 0, query = left.currentQuery(), item = item)
                }
                if (!limiter.check(tiledList)) right.read()?.let { item ->
                    tiledList.add(query = right.currentQuery(), item = item)
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

private class QueryAndList<Query, Item>(
    private var queryIndex: Int,
    val isLeft: Boolean,
    val orderedQueries: List<Query>,
    val queryItemsMap: Map<Query, List<Item>>,
) {

    private var innerIndex: Int = getInnerIndex()

    private val items
        get() = when {
            inBounds() -> queryItemsMap.getValue(orderedQueries[queryIndex])
            else -> emptyList()
        }

    fun inBounds() = queryIndex in orderedQueries.indices

    fun currentQuery() = orderedQueries[queryIndex]
    fun read(): Item? {
        val item = when {
            isLeft -> items.getOrNull(--innerIndex)
            else -> items.getOrNull(++innerIndex)
        }
        if (item == null) {
            if (isLeft) --queryIndex
            else ++queryIndex
            innerIndex = getInnerIndex()
            if (inBounds()) return read()
        }
        return item
    }

    private fun getInnerIndex() = when {
        isLeft -> items.size
        else -> -1
    }
}