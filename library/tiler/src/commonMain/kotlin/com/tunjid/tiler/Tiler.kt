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

import com.tunjid.tiler.utilities.IntArrayList
import com.tunjid.tiler.utilities.chunkedTiledList
import kotlin.math.abs
import kotlin.math.min

/**
 * Holds information regarding properties that may be useful when flattening a [Map] of [Query]
 * to [Tile] into a [List]
 */
internal class Tiler<Query, Item> private constructor(
    private var order: Tile.Order<Query, Item>,
    private var limiter: Tile.Limiter<Query, Item> = Tile.Limiter(
        maxQueries = Int.MIN_VALUE,
        itemSizeHint = null
    ),
    private val queryItemsMap: MutableMap<Query, List<Item>>
) {
    constructor(
        order: Tile.Order<Query, Item>,
        limiter: Tile.Limiter<Query, Item> = Tile.Limiter(
            maxQueries = Int.MIN_VALUE,
            itemSizeHint = null
        )
    ) : this(
        order = order,
        limiter = limiter,
        queryItemsMap = mutableMapOf()
    )

    private val orderedQueries: MutableList<Query> = mutableListOf()
    private val outputIndices: IntArrayList = IntArrayList(
        if (limiter.maxQueries >= 0) limiter.maxQueries
        else 10
    )
    private var lastTiledItems: TiledList<Query, Item> = emptyTiledList()
    private var last: Tiler<Query, Item>? = null

    fun process(output: Tile.Output<Query, Item>): TiledList<Query, Item>? {
        updateLast()
        when (output) {
            is Tile.Data -> {
                // Only sort queries when they output the first time to amortize the cost of sorting.
                if (!queryItemsMap.contains(output.query)) orderedQueries.insertSorted(
                    query = output.query,
                    comparator = order.comparator
                )
                queryItemsMap[output.query] = output.items
            }

            is Tile.Request.Evict -> {
                val evictionIndex = orderedQueries.binarySearch(
                    element = output.query,
                    comparator = order.comparator
                )
                if (evictionIndex >= 0) orderedQueries.removeAt(evictionIndex)
                queryItemsMap.remove(output.query)
            }

            is Tile.Order-> {
                order = output
                orderedQueries.sortWith(output.comparator)
            }

            is Tile.Limiter -> {
                limiter = output
            }
        }
        computeOutputIndices(queryItemsMap)
        return if (shouldEmitFor(output)) chunkedTiledList(
            chunkSizeHint = limiter.itemSizeHint,
            indices = outputIndices,
            queryLookup = orderedQueries::get,
            itemsLookup = queryItemsMap::getValue
        ).also { lastTiledItems = it }
        else null
    }

    private fun updateLast() = when (val lastSnapshot = last) {
        null -> {
            last = Tiler(
                order = order,
                limiter = limiter,
                // Share the same map instance
                queryItemsMap = queryItemsMap
            )
        }

        else -> {
            lastSnapshot.order = order
            lastSnapshot.limiter = limiter
            lastSnapshot.lastTiledItems = lastTiledItems
            lastSnapshot.orderedQueries.clear()
            lastSnapshot.orderedQueries.addAll(orderedQueries)
            lastSnapshot.outputIndices.clear()
            for (i in 0..outputIndices.lastIndex) {
                lastSnapshot.outputIndices.add(outputIndices[i])
            }
        }
    }

    private fun computeOutputIndices(
        queryItemsMap: Map<Query, List<Item>>,
    ) {
        outputIndices.clear()
        when (val order = order) {
            is Tile.Order.Sorted -> {
                var count = 0
                var index = -1
                val maxNumberOfChunks = min(limitedChunkSize(), orderedQueries.size)
                while (
                    count < maxNumberOfChunks
                    && index <= orderedQueries.lastIndex
                ) {
                    if (++index <= orderedQueries.lastIndex
                        // Skip empty chunks
                        && queryItemsMap.getValue(orderedQueries[index]).isNotEmpty()
                        && ++count <= maxNumberOfChunks
                    ) outputIndices.add(
                        element = index,
                    )
                }
            }

            is Tile.Order.PivotSorted -> {
                if (orderedQueries.isEmpty()) return

                val pivotQuery: Query = order.query ?: return
                val startIndex = orderedQueries.binarySearch(pivotQuery, order.comparator)

                if (startIndex < 0) return

                var leftIndex = startIndex
                var rightIndex = startIndex
                val maxNumberOfChunks = min(limitedChunkSize(), orderedQueries.size)

                // Check if adding the pivot index will cause it to go over the limit
                if (maxNumberOfChunks < 1) return

                val pivotIndexIsEmpty = queryItemsMap.getValue(orderedQueries[startIndex]).isEmpty()
                if (!pivotIndexIsEmpty) outputIndices.add(
                    element = startIndex,
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
                    ) outputIndices.add(
                        index = 0,
                        element = leftIndex,
                    )
                    if (++rightIndex <= orderedQueries.lastIndex
                        // Skip empty chunks
                        && queryItemsMap.getValue(orderedQueries[rightIndex]).isNotEmpty()
                        && ++count <= maxNumberOfChunks
                    ) outputIndices.add(
                        element = rightIndex,
                    )
                }
            }
        }
    }

    private fun shouldEmitFor(output: Tile.Output<Query, Item>) = when (output) {
        is Tile.Data -> when (val existingOrder = order) {
            is Tile.Order.Sorted -> true

            is Tile.Order.PivotSorted -> when {
                // If the pivot item is present, check if the item emitted will be seen
                queryItemsMap.contains(existingOrder.query) -> isInVisibleRange(output.query)
                // If the last emission was empty and nothing will still be emitted, do not emit
                else -> !(lastTiledItems.isEmpty() && existingOrder.query != output.query)
            }
        }
        // Emit only if there's something to evict
        is Tile.Request.Evict -> {
            isInVisibleRange(output.query) && last?.isInVisibleRange(output.query) == true
        }
        // Emit only if there are items to sort, and the order has meaningfully changed
        is Tile.Order -> {
            var willEmit = false
            val areTheSameSize = outputIndices.size == last?.outputIndices?.size
            if (areTheSameSize) when (val currentLast = last) {
                // No last emission, do nothing
                null -> Unit
                // Compare the values at the indices as they may refer to different things
                else -> for (i in 0..outputIndices.lastIndex) {
                    if (outputQueryAt(i) == currentLast.outputQueryAt(i)) continue
                    willEmit = true
                    break
                }
            }
            else willEmit = queryItemsMap.isNotEmpty() && order != last?.order
            willEmit
        }
        // Emit only if the limiter has meaningfully changed
        is Tile.Limiter -> when (val order = order) {
            is Tile.Order.Sorted -> queryItemsMap.isNotEmpty()
                    && outputIndices != last?.outputIndices

            is Tile.Order.PivotSorted -> queryItemsMap.contains(order.query)
                    && outputIndices != last?.outputIndices
        }
    }

    private fun isInVisibleRange(query: Query): Boolean =
        if (outputIndices.isEmpty()) false
        else when (order) {
            is Tile.Order.PivotSorted -> {
                val isGreaterOrEqualToFirst = order.comparator.compare(
                    query,
                    outputQueryAt(0),
                ) >= 0
                val isLessOrEqualToLast = order.comparator.compare(
                    query,
                    outputQueryAt(outputIndices.lastIndex),
                ) <= 0
                isGreaterOrEqualToFirst && isLessOrEqualToLast
            }

            is Tile.Order.Sorted -> true
        }

    private fun outputQueryAt(index: Int) = orderedQueries[outputIndices[index]]

    private fun limitedChunkSize() =
        when (val numQueries = limiter.maxQueries) {
            in Int.MIN_VALUE..-1 -> orderedQueries.size
            else -> numQueries
        }
}

/**
 * Inserts [query] into [this] that has already been sorted by [comparator] with no duplication.
 */
fun <Query> MutableList<Query>.insertSorted(
    query: Query,
    comparator: Comparator<Query>
) {
    // Not in the list, add it
    if (isEmpty()) {
        add(query)
        return
    }
    val index = binarySearch(query, comparator)
    if (index >= 0) return // Already exists

    when (val invertedIndex = abs(index + 1)) {
        in 0..lastIndex -> add(
            index = invertedIndex,
            element = query
        )

        else -> add(
            element = query
        )
    }
}
