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
    val limiter: Tile.Limiter<Query, Item>,
    val order: Tile.Order<Query, Item> = Tile.Order.Unspecified(),
    val metadata: Tile.Metadata<Query> = Tile.Metadata(
        pivotQuery = when (order) {
            is Tile.Order.Custom,
            is Tile.Order.Sorted,
            is Tile.Order.Unspecified -> null

            is Tile.Order.PivotSorted -> order.query
        }
    ),
    val shouldEmit: Boolean = false,
    // I'd rather this be immutable, electing against it for performance reasons
    val queryToTiles: MutableMap<Query, Tile<Query, Item>> = mutableMapOf(),
) {

    fun add(output: Tile.Output<Query, Item>): Tiler<Query, Item> = when (output) {
        is Tile.Output.Data -> copy(
            shouldEmit = true,
            // Only sort queries when they output the first time to amortize the cost of sorting.
            metadata = when {
                queryToTiles.contains(output.query) -> metadata.copy(mostRecentlyEmitted = output.query)
                else -> metadata.copy(
                    sortedQueries = metadata.sortedQueries
                        .plus(output.query)
                        .distinct()
                        .sortedWith(order.comparator),
                    mostRecentlyEmitted = output.query,
                )
            },
            queryToTiles = queryToTiles.apply { put(output.query, output.tile) }
        )

        is Tile.Output.UpdatePivot -> when (order) {
            is Tile.Order.PivotSorted -> copy(
                shouldEmit = metadata.pivotQuery != output.query,
                metadata = metadata.copy(pivotQuery = output.query)
            )

            else -> throw IllegalArgumentException("The Tiler is not configured for pivoting")
        }

        is Tile.Output.Eviction -> copy(
            shouldEmit = true,
            metadata = metadata.copy(
                sortedQueries = metadata.sortedQueries - output.query
            ),
            queryToTiles = queryToTiles.apply { remove(output.query) }
        )

        is Tile.Output.FlattenChange -> copy(
            shouldEmit = true,
            order = output.order,
            metadata = metadata.copy(
                pivotQuery = when (output.order) {
                    is Tile.Order.Custom,
                    is Tile.Order.Sorted,
                    is Tile.Order.Unspecified -> null

                    is Tile.Order.PivotSorted -> output.order.query
                },
                sortedQueries = metadata.sortedQueries.sortedWith(output.order.comparator)
            )
        )

        is Tile.Output.LimiterChange -> copy(
            limiter = output.limiter
        )
    }

    fun output(): TiledList<Query, Item> = queryToTiles.tileWith(
        metadata = metadata,
        order = order,
        limiter = limiter
    )
}
