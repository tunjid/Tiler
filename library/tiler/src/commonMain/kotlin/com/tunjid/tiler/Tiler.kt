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

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal sealed interface Tiler<Query, Item> {
    /**
     * Whether there's [Tiler] any meaningful change in the [Tiler] state requiring it emit data
     */
    suspend fun shouldEmit(): Boolean

    /**
     * The [TiledList] produced my this [Tiler]
     */
    suspend fun tiledItems(): TiledList<Query, Item>

    /**
     * Processes the [Tile.Output] for some [Tile.Input] earlier in the tiling pipeline
     */
    suspend fun process(
        output: Tile.Output<Query, Item>
    ): Tiler<Query, Item>
}

/**
 * Class used as seed for the tiling pipeline
 */
internal data class ImmutableTiler<Query, Item>(
    val metadata: Tile.Metadata<Query, Item>,
) : Tiler<Query, Item> {
    override suspend fun shouldEmit(): Boolean =
        false

    override suspend fun tiledItems(): TiledList<Query, Item> =
        emptyTiledList()

    override suspend fun process(output: Tile.Output<Query, Item>): Tiler<Query, Item> =
        MutableTiler(metadata).process(output)
}


/**
 * Mutable [Tiler] implementation for efficient tiling
 */
internal class MutableTiler<Query, Item>(
    private var metadata: Tile.Metadata<Query, Item>,
) : Tiler<Query, Item> {
    private val mutex = Mutex()

    private var shouldEmit: Boolean = false
    private var lastTiledItems: TiledList<Query, Item> = emptyTiledList()
    private val queryItemsMap: MutableMap<Query, List<Item>> = mutableMapOf()

    override suspend fun shouldEmit() = mutex.withLock { shouldEmit }

    override suspend fun tiledItems(): TiledList<Query, Item> = mutex.withLock {
        lastTiledItems = metadata.toTiledList(queryItemsMap = queryItemsMap)
        return lastTiledItems
    }

    /**
     * Mutates this [MutableTiler] with [Tile.Output] produced from the tiling process.
     * As tiling collects from multiple flows concurrently, it is imperative a mutex is used to
     * synchronize access to the variables modified.
     */
    override suspend fun process(
        output: Tile.Output<Query, Item>
    ): MutableTiler<Query, Item> = mutex.withLock {
        when (output) {
            is Tile.Output.Data -> {
                shouldEmit = shouldEmit(output)
                // Only sort queries when they output the first time to amortize the cost of sorting.
                metadata = when {
                    queryItemsMap.contains(output.query) -> metadata.copy(
                        mostRecentlyEmitted = output.query
                    )

                    else -> metadata.copy(
                        orderedQueries = metadata.insertOrderedQuery(output.query),
                        mostRecentlyEmitted = output.query,
                    )
                }
                queryItemsMap[output.query] = output.items
            }

            is Tile.Output.Eviction -> {
                shouldEmit = shouldEmit(output)
                metadata = metadata.copy(
                    orderedQueries = metadata.orderedQueries - output.query
                )
                queryItemsMap.remove(output.query)
            }

            is Tile.Output.OrderChange -> {
                shouldEmit = shouldEmit(output)
                metadata = metadata.copy(
                    order = output.order,
                    orderedQueries = metadata.orderedQueries.sortedWith(output.order.comparator)
                )
            }

            is Tile.Output.LimiterChange -> {
                shouldEmit = shouldEmit(output)
                metadata = metadata.copy(limiter = output.limiter)
            }
        }
        this
    }

    private fun shouldEmit(
        output: Tile.Output<Query, Item>
    ): Boolean = when (output) {
        is Tile.Output.Data -> when (val order = metadata.order) {
            is Tile.Order.Custom,
            is Tile.Order.Sorted -> true
            is Tile.Order.PivotSorted -> when {
                // Always emit if the pivot item is present
                queryItemsMap.contains(order.query) -> true
                // If the last emission was empty and nothing will still be emitted, do not emit
                else -> !(lastTiledItems.isEmpty() && order.query != output.query)
            }
        }
        // Emit only if there's something to evict
        is Tile.Output.Eviction -> queryItemsMap.contains(output.query)
        // Emit only if there are items to sort, and the order has meaningfully changed
        is Tile.Output.OrderChange -> queryItemsMap.isNotEmpty() && output.order != metadata.order
        // Emit only if the limiter has meaningfully changed
        is Tile.Output.LimiterChange -> when (val order = metadata.order) {
            is Tile.Order.Custom,
            is Tile.Order.Sorted -> queryItemsMap.isNotEmpty() && output.limiter != metadata.limiter

            is Tile.Order.PivotSorted -> queryItemsMap.contains(order.query) && output.limiter != metadata.limiter
        }
    }
}

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