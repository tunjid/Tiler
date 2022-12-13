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

/**
 * Produces [TiledList] from the current tiling state
 */
internal data class Tiler<Query, Item>(
    val metadata: Tile.Metadata<Query, Item>,
    val shouldEmit: Boolean = false,
    val queryItemsMap: Map<Query, List<Item>> = mapOf(),
)

internal fun <Query, Item> Tiler<Query, Item>.add(
    output: Tile.Output<Query, Item>
): Tiler<Query, Item> = when (output) {
    is Tile.Output.Data -> copy(
        shouldEmit = true,
        // Only sort queries when they output the first time to amortize the cost of sorting.
        metadata = when {
            queryItemsMap.contains(output.query) -> metadata.copy(
                mostRecentlyEmitted = output.query
            )
            else -> metadata.copy(
                orderedQueries = metadata.insertOrderedQuery(output.query),
                mostRecentlyEmitted = output.query,
            )
        },
        queryItemsMap = queryItemsMap + (output.query to output.items)
    )

    is Tile.Output.Eviction -> copy(
        shouldEmit = true,
        metadata = metadata.copy(
            orderedQueries = metadata.orderedQueries - output.query
        ),
        queryItemsMap = queryItemsMap - output.query
    )

    is Tile.Output.OrderChange -> copy(
        shouldEmit = metadata.order != output.order,
        metadata = metadata.copy(
            order = output.order,
            orderedQueries = metadata.orderedQueries.sortedWith(output.order.comparator)
        )
    )

    is Tile.Output.LimiterChange -> copy(
        shouldEmit = true,
        metadata = metadata.copy(limiter = output.limiter)
    )
}

internal fun <Query, Item> Tiler<Query, Item>.output(): TiledList<Query, Item> = metadata.toTiledList(
    queryItemsMap = queryItemsMap,
)

/**
 * Inserts a new query into ordered queries in O(N) time.
 *
 * If the List were mutable, binary search could've been used to find the insertion index
 * but requiring immutability restricts to O(N).
 */
internal fun <Query, Item> Tile.Metadata<Query, Item>.insertOrderedQuery(
    middleQuery: Query
): List<Query> = when {
    orderedQueries.isEmpty() -> listOf(middleQuery)
    else -> buildList(orderedQueries.size + 1) {
        var added = false

        for (index in orderedQueries.indices) {
            val leftQuery = orderedQueries[index]

            if (added) {
                add(leftQuery)
                continue
            }

            val rightQuery = orderedQueries.getOrNull(index + 1)
            val leftComparison = order.comparator.compare(leftQuery, middleQuery)
            val rightComparison = when (rightQuery) {
                null -> Int.MAX_VALUE
                else -> order.comparator.compare(rightQuery, middleQuery)
            }
            val addBefore = leftComparison > 0
            val isDuplicate = leftComparison == 0
            val addAfter = leftComparison < 0 && rightComparison > 0

            if (addBefore) add(middleQuery)
            add(leftQuery)
            if (addAfter) add(middleQuery)

            added = addBefore || isDuplicate || addAfter
        }
    }
}