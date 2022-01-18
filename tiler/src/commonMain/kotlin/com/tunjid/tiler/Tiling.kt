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

@Suppress("UNCHECKED_CAST")
internal fun <Query, Item, Output> Map<Query, Tile<Query, Item>>.tileWith(
    metadata: Tile.Metadata<Query>,
    order: Tile.Order<Query, Item>,
    limiter: Tile.Limiter<Query, Item, Output>
): Output = when (limiter) {
    is Tile.Limiter.List -> listTiling(
        metadata = metadata,
        order = order,
        limiter = limiter
    ) as Output
    is Tile.Limiter.Map -> mapTiling(
        metadata = metadata,
        order = order,
        limiter = limiter
    ) as Output
}

private fun <Query, Item> Map<Query, Tile<Query, Item>>.listTiling(
    metadata: Tile.Metadata<Query>,
    order: Tile.Order<Query, Item>,
    limiter: Tile.Limiter.List<Query, Item>
): List<Item> {
    val queryToTiles = this
    val sortedQueries = metadata.sortedQueries

    return when (order) {
        is Tile.Order.Unspecified -> queryToTiles.keys
            .foldWhile(mutableListOf(), limiter.check) { list, query ->
                list.add(element = queryToTiles.getValue(query).item)
                list
            }
        is Tile.Order.Sorted -> sortedQueries
            .foldWhile(mutableListOf(), limiter.check) { list, query ->
                list.add(element = queryToTiles.getValue(query).item)
                list
            }
        is Tile.Order.PivotSorted -> {
            if (sortedQueries.isEmpty()) return emptyList()

            val mostRecentQuery: Query = metadata.mostRecentlyTurnedOn ?: return emptyList()
            val startIndex = sortedQueries.binarySearch(mostRecentQuery, order.comparator)

            if (startIndex < 0) return emptyList()

            var leftIndex = startIndex
            var rightIndex = startIndex
            val result = mutableListOf(queryToTiles.getValue(sortedQueries[startIndex]).item)

            while (!limiter.check(result) && (leftIndex >= 0 || rightIndex <= sortedQueries.lastIndex)) {
                if (--leftIndex >= 0) result.add(
                    index = 0,
                    element = queryToTiles.getValue(sortedQueries[leftIndex]).item
                )
                if (++rightIndex <= sortedQueries.lastIndex) result.add(
                    element = queryToTiles.getValue(sortedQueries[rightIndex]).item
                )
            }
            result
        }
        is Tile.Order.CustomList -> order.transform(metadata, queryToTiles)
        // This should be an impossible state to reach using the external API
        is Tile.Order.CustomMap -> throw IllegalArgumentException(
            """
            Cannot flatten into a List using CustomMap. 
        """.trimIndent()
        )
    }
}

/**
 * Produces a [Map] that adheres to the semantics of the specified [order]
 */
private fun <Query, Item> Map<Query, Tile<Query, Item>>.mapTiling(
    metadata: Tile.Metadata<Query>,
    order: Tile.Order<Query, Item>,
    limiter: Tile.Limiter.Map<Query, Item>
): Map<Query, Item> {
    val queryToTiles = this
    val sortedQueries = metadata.sortedQueries

    return when (order) {
        is Tile.Order.Unspecified -> queryToTiles.keys
            .foldWhile(mutableMapOf(), limiter.check) { map, query ->
                map[query] = queryToTiles.getValue(query).item
                map
            }
        is Tile.Order.Sorted -> sortedQueries
            .foldWhile(mutableMapOf(), limiter.check) { map, query ->
                map[query] = queryToTiles.getValue(query).item
                map
            }
        is Tile.Order.PivotSorted -> {
            if (sortedQueries.isEmpty()) return emptyMap()

            val mostRecentQuery: Query = metadata.mostRecentlyTurnedOn ?: return emptyMap()
            val startIndex = sortedQueries.binarySearch(mostRecentQuery, order.comparator)

            if (startIndex < 0) return emptyMap()

            var leftIndex = startIndex
            var rightIndex = startIndex
            var query = sortedQueries[startIndex]
            val iterationOrder = mutableListOf(query)
            val result = mutableMapOf<Query, Item>()
            result[query] = queryToTiles.getValue(query).item

            while (!limiter.check(result) && (leftIndex >= 0 || rightIndex <= sortedQueries.lastIndex)) {
                if (--leftIndex >= 0) {
                    query = sortedQueries[leftIndex]
                    iterationOrder.add(0, query)
                    result[query] = queryToTiles.getValue(query).item
                }
                if (++rightIndex <= sortedQueries.lastIndex) {
                    query = sortedQueries[rightIndex]
                    iterationOrder.add(query)
                    result[query] = queryToTiles.getValue(query).item
                }
            }
            OrderedMap(
                orderedKeys = iterationOrder,
                backing = result
            )
        }
        // This should be an impossible state to reach using the external API
        is Tile.Order.CustomList -> throw IllegalArgumentException(
            """
            Cannot flatten into a Map using CustomList. 
        """.trimIndent()
        )
        is Tile.Order.CustomMap -> order.transform(metadata, queryToTiles)
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

/**
 * Hideous class to guarantee iteration order of keys in a [Map]. It's inefficient, gross and I hate it.
 */
private class OrderedMap<K, V>(
    val orderedKeys: List<K>,
    val backing: Map<K, V>
) : Map<K, V> by backing {
    override val keys: Set<K> by lazy { orderedKeys.toSet() }
    override val entries: Set<Map.Entry<K, V>> by lazy {
        orderedKeys.fold(mutableSetOf()) { set, key ->
            set.add(Entry(
                key = key,
                value = backing.getValue(key)
            ))
            set
        }
    }
}

private data class Entry<K, V>(
    override val key: K,
    override val value: V
) : Map.Entry<K, V>
