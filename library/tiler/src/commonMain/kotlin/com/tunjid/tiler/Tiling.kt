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

import com.tunjid.tiler.utilities.chunkedTiledList
import kotlin.math.abs
import kotlin.math.min

/**
 * Holds information regarding properties that may be useful when flattening a [Map] of [Query]
 * to [Tile] into a [List]
 */
internal class Metadata<Query, Item> private constructor(
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
    private var lastTiledItems: TiledList<Query, Item> = emptyTiledList()
    private var outputIndices: List<Int> = emptyList()
    private var last: Metadata<Query, Item>? = null

    var shouldEmit: Boolean = false
        private set

    fun update(output: Tile.Output<Query, Item>) {
        updateLast()
        when (output) {
            is Tile.Output.Data -> {
                // Only sort queries when they output the first time to amortize the cost of sorting.
                if (!queryItemsMap.contains(output.query)) orderedQueries.insertOrderedQuery(
                    query = output.query,
                    comparator = order.comparator
                )
                queryItemsMap[output.query] = output.items
            }

            is Tile.Output.Eviction -> {
                val evictionIndex = orderedQueries.binarySearch(
                    element = output.query,
                    comparator = order.comparator
                )
                if (evictionIndex >= 0) orderedQueries.removeAt(evictionIndex)
                queryItemsMap.remove(output.query)
            }

            is Tile.Output.OrderChange -> {
                order = output.order
                orderedQueries.sortWith(output.order.comparator)
            }

            is Tile.Output.LimiterChange -> {
                limiter = output.limiter
            }
        }
        outputIndices = computeOutputIndices(queryItemsMap)
        shouldEmit = shouldEmitFor(output)
    }

    fun tiledItems(): TiledList<Query, Item> {
        lastTiledItems = chunkedTiledList(
            chunkSizeHint = limiter.itemSizeHint,
            indices = outputIndices,
            queryLookup = orderedQueries::get,
            itemsLookup = queryItemsMap::getValue
        )
        return lastTiledItems
    }

    private fun updateLast() = when (val lastSnapshot = last) {
        null -> {
            last = Metadata(
                order = order,
                limiter = limiter,
                // Share the same map instance
                queryItemsMap = queryItemsMap
            )
        }

        else -> {
            lastSnapshot.order = order
            lastSnapshot.limiter = limiter
            lastSnapshot.shouldEmit = shouldEmit
            lastSnapshot.outputIndices = outputIndices
            lastSnapshot.orderedQueries.clear()
            lastSnapshot.orderedQueries.addAll(orderedQueries)
            lastSnapshot.lastTiledItems = lastTiledItems
        }
    }

    private fun computeOutputIndices(
        queryItemsMap: Map<Query, List<Item>>,
    ): List<Int> {
        return when (val order = order) {
            is Tile.Order.Sorted -> {
                var count = 0
                var index = -1
                val maxNumberOfChunks = min(limitedChunkSize(), orderedQueries.size)
                val indexList = ArrayList<Int>(maxNumberOfChunks)
                while (
                    count < maxNumberOfChunks
                    && index <= orderedQueries.lastIndex
                ) {
                    if (++index <= orderedQueries.lastIndex
                        // Skip empty chunks
                        && queryItemsMap.getValue(orderedQueries[index]).isNotEmpty()
                        && ++count <= maxNumberOfChunks
                    ) indexList.add(
                        element = index,
                    )
                }
                indexList
            }

            is Tile.Order.PivotSorted -> {
                if (orderedQueries.isEmpty()) return emptyList()

                val pivotQuery: Query = order.query ?: return emptyList()
                val startIndex = orderedQueries.binarySearch(pivotQuery, order.comparator)

                if (startIndex < 0) return emptyList()

                var leftIndex = startIndex
                var rightIndex = startIndex
                val maxNumberOfChunks = min(limitedChunkSize(), orderedQueries.size)

                val indexList = ArrayList<Int>(maxNumberOfChunks)

                // Check if adding the pivot index will cause it to go over the limit
                if (maxNumberOfChunks < 1) return indexList

                val pivotIndexIsEmpty = queryItemsMap.getValue(orderedQueries[startIndex]).isEmpty()
                if (!pivotIndexIsEmpty) indexList.add(
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
                    ) indexList.add(
                        index = 0,
                        element = leftIndex,
                    )
                    if (++rightIndex <= orderedQueries.lastIndex
                        // Skip empty chunks
                        && queryItemsMap.getValue(orderedQueries[rightIndex]).isNotEmpty()
                        && ++count <= maxNumberOfChunks
                    ) indexList.add(
                        element = rightIndex,
                    )
                }
                indexList
            }
        }
    }

    private fun shouldEmitFor(output: Tile.Output<Query, Item>) = when (output) {
        is Tile.Output.Data -> when (val existingOrder = order) {
            is Tile.Order.Sorted -> true

            is Tile.Order.PivotSorted -> when {
                // If the pivot item is present, check if the item emitted will be seen
                queryItemsMap.contains(existingOrder.query) -> isInVisibleRange(output.query)
                // If the last emission was empty and nothing will still be emitted, do not emit
                else -> !(lastTiledItems.isEmpty() && existingOrder.query != output.query)
            }
        }
        // Emit only if there's something to evict
        is Tile.Output.Eviction -> {
            isInVisibleRange(output.query) && last?.isInVisibleRange(output.query) == true
        }
        // Emit only if there are items to sort, and the order has meaningfully changed
        is Tile.Output.OrderChange -> {
            var willEmit = false
            // Compare the values at the indices as they may refer to different things
            if (outputIndices.size == last?.outputIndices?.size) when (val currentLast =
                last) {
                null -> Unit
                else -> for (i in outputIndices.indices) {
                    if (outputQueryAt(i) == currentLast.outputQueryAt(i)) continue
                    willEmit = true
                    break
                }
            }
            else willEmit = queryItemsMap.isNotEmpty() && order != last?.order
            willEmit
        }
        // Emit only if the limiter has meaningfully changed
        is Tile.Output.LimiterChange -> when (val order = order) {
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
 * Inserts a new query into ordered queries in O(N) time.
 *
 * If the List were mutable, binary search could've been used to find the insertion index
 * but requiring immutability restricts to O(N).
 */
 fun <Query> MutableList<Query>.insertOrderedQuery(
    query: Query,
    comparator: Comparator<Query>
) {
    if (isEmpty()) add(query)
    else when (val insertionIndex = binarySearch(query, comparator)) {
        in Int.MIN_VALUE..0 -> when (val invertedInsertionIndex = abs(insertionIndex + 1)) {
            in 0..lastIndex -> add(
                index = invertedInsertionIndex,
                element = query
            )

            else -> add(
                element = query
            )
        }
        // No duplicates
        else -> if (comparator.compare(query, get(insertionIndex)) != 0) add(
            index = insertionIndex,
            element = query
        )
    }
}
