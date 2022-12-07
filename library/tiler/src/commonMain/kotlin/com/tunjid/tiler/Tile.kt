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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

typealias ListTiler<Query, Item> = (Flow<Tile.Input<Query, Item>>) -> Flow<TiledList<Query, Item>>

/**
 * class holding metadata about a [Query] for an [Item], the [Item], and when the [Query] was sent
 */
class Tile<Query, Item : Any?> {

    /**
     * Holds information regarding properties that may be useful when flattening a [Map] of [Query]
     * to [Tile] into a [List]
     */
    data class Metadata<Query> internal constructor(
        val sortedQueries: List<Query> = listOf(),
        val pivotQuery: Query? = null,
        val mostRecentlyEmitted: Query? = null,
    )

    /**
     * Defines input parameters for the [tiledList] function
     */
    sealed interface Input<Query, Item>

    /**
     * Requests for controlling the output of a [Query]
     */
    sealed interface ValveRequest<Query, Item>

    /**
     * [Tile.Input] type for managing data in the [tiledList] function
     */
    sealed class Request<Query, Item> : Input<Query, Item> {
        abstract val query: Query

        /**
         * Starts collecting from the backing [Flow] for the specified [query].
         * Requesting this is idempotent; multiple requests have no side effects.
         */
        data class On<Query, Item>(override val query: Query) : Request<Query, Item>(), ValveRequest<Query, Item>

        /**
         * Stops collecting from the backing [Flow] for the specified [query].
         * The items previously fetched by this query are still kept in memory and will be
         * in the [List] of items returned
         * Requesting this is idempotent; multiple requests have no side effects.
         */
        data class Off<Query, Item>(override val query: Query) : Request<Query, Item>(), ValveRequest<Query, Item>

        /**
         * Stops collecting from the backing [Flow] for the specified [query] and also evicts
         * the items previously fetched by the [query] from memory.
         * Requesting this is idempotent; multiple requests have no side effects.
         */
        data class Evict<Query, Item>(override val query: Query) : Request<Query, Item>(), ValveRequest<Query, Item>

        /**
         * Pivots the produced [TiledList] around [query]. This is only valid when using the [Order.PivotSorted]
         * [Order].
         */
        data class PivotAround<Query, Item>(override val query: Query) : Request<Query, Item>()
    }

    /**
     * Describes the order of output items from the tiling functions
     */
    sealed class Order<Query, Item> : Input<Query, Item> {

        abstract val comparator: Comparator<Query>

        /**
         * Items will be returned in an unspecified order; the order is whatever the iteration
         * order of the backing map of [Query] to [Item] uses
         */
        internal data class Unspecified<Query, Item>(
            override val comparator: Comparator<Query> = Comparator { _, _ -> 0 },
        ) : Order<Query, Item>(), Input<Query, Item>

        /**
         * Sort items with the specified query [comparator].
         */
        data class Sorted<Query, Item>(
            override val comparator: Comparator<Query>,
        ) : Order<Query, Item>(), Input<Query, Item>

        /**
         * Sort items with the specified [comparator] but pivoted around a specific query.
         * This allows for showing items that have more priority over others in the current context
         */
        data class PivotSorted<Query, Item>(
            val query: Query,
            override val comparator: Comparator<Query>,
        ) : Order<Query, Item>(), Input<Query, Item>

        /**
         * Flattens tiled items produced in to a [List] whichever way you desire
         */
        data class Custom<Query, Item>(
            override val comparator: Comparator<Query>,
            val transform: Metadata<Query>.(Map<Query, List<Item>>) -> TiledList<Query, Item>,
        ) : Order<Query, Item>(), Input<Query, Item>

    }

    /**
     * Limits the output of the [tiledList] for [tiledList] functions
     */
    data class Limiter<Query, Item>(
        val check: (TiledList<Query, Item>) -> Boolean
    ) : Input<Query, Item>

    /**
     * Summary of changes that can occur as a result of tiling
     */
    internal sealed class Output<Query, Item> {
        data class Data<Query, Item>(
            val query: Query,
            val items: List<Item>
        ) : Output<Query, Item>()

        data class OrderChange<Query, Item>(
            val order: Order<Query, Item>
        ) : Output<Query, Item>()

        data class LimiterChange<Query, Item>(
            val limiter: Limiter<Query, Item>
        ) : Output<Query, Item>()

        data class Eviction<Query, Item>(
            val query: Query,
        ) : Output<Query, Item>()

        data class UpdatePivot<Query, Item>(
            val query: Query,
        ) : Output<Query, Item>()
    }
}

/**
 * Convenience method to convert a [Flow] of [Tile.Input] to a [Flow] of a [TiledList] of [Item]s
 */
fun <Query, Item> Flow<Tile.Input<Query, Item>>.toTiledList(
    transform: ListTiler<Query, Item>
): Flow<TiledList<Query, Item>> = transform(this)


/**
 * Converts a [Flow] of [Query] into a [Flow] of [TiledList] [Item]
 */
fun <Query, Item> tiledList(
    limiter: Tile.Limiter<Query, Item> = Tile.Limiter { false },
    order: Tile.Order<Query, Item> = Tile.Order.Unspecified(),
    fetcher: suspend (Query) -> Flow<List<Item>>
): ListTiler<Query, Item> = { requests ->
    tilerFactory(
        limiter = limiter,
        order = order,
        fetcher = fetcher
    )
        .invoke(requests)
        .map(Tiler<Query, Item>::output)
}

