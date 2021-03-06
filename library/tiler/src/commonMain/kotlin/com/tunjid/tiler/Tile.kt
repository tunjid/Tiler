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

typealias ListTiler<Query, Item> = (Flow<Tile.Input.List<Query, Item>>) -> Flow<List<Item>>

typealias MapTiler<Query, Item> = (Flow<Tile.Input.Map<Query, Item>>) -> Flow<Map<Query, Item>>

/**
 * class holding metadata about a [Query] for an [Item], the [Item], and when the [Query] was sent
 */
data class Tile<Query, Item : Any?>(
    val flowOnAt: Long,
    val item: Item,
) {

    /**
     * Holds information regarding properties that may be useful when flattening a [Map] of [Query]
     * to [Tile] into a [List]
     */
    data class Metadata<Query> internal constructor(
        val sortedQueries: List<Query> = listOf(),
        val mostRecentlyTurnedOn: Query? = null,
        val mostRecentlyEmitted: Query? = null,
    )

    /**
     * Defines input parameters for the tiling functions [tiledList] and [tiledMap]
     */
    sealed interface Input<Query, Item> {
        /**
         * Inputs that can only be used with the [tiledList] function
         */
        sealed interface List<Query, Item> : Input<Query, Item>
        /**
         * Inputs that can only be used with the [tiledMap] function
         */
        sealed interface Map<Query, Item> : Input<Query, Item>
        /**
         * Inputs that can be used with either the [tiledList] or [tiledMap] functions
         */
        sealed interface Agnostic<Query, Item> : List<Query, Item>, Map<Query, Item>
    }

    /**
     * [Tile.Input] type for managing data in the [tiledList] or [tiledMap] functions
     */
    sealed class Request<Query, Item> : Input.Agnostic<Query, Item> {
        abstract val query: Query

        /**
         * Starts collecting from the backing [Flow] for the specified [query].
         * Requesting this is idempotent; multiple requests have no side effects.
         */
        data class On<Query, Item>(override val query: Query) : Request<Query, Item>()

        /**
         * Stops collecting from the backing [Flow] for the specified [query].
         * The items previously fetched by this query are still kept in memory and will be
         * in the [List] of items returned
         * Requesting this is idempotent; multiple requests have no side effects.
         */
        data class Off<Query, Item>(override val query: Query) : Request<Query, Item>()

        /**
         * Stops collecting from the backing [Flow] for the specified [query] and also evicts
         * the items previously fetched by the [query] from memory.
         * Requesting this is idempotent; multiple requests have no side effects.
         */
        data class Evict<Query, Item>(override val query: Query) : Request<Query, Item>()
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
        ) : Order<Query, Item>(), Input.Agnostic<Query, Item>

        /**
         * Sort items with the specified query [comparator].
         */
        data class Sorted<Query, Item>(
            override val comparator: Comparator<Query>,
        ) : Order<Query, Item>(), Input.Agnostic<Query, Item>

        /**
         * Sort items with the specified [comparator] but pivoted around the last query a
         * [Tile.Request.On] was sent for. This allows for showing items that have more priority
         * over others in the current context
         */
        data class PivotSorted<Query, Item>(
            override val comparator: Comparator<Query>,
        ) : Order<Query, Item>(), Input.Agnostic<Query, Item>

        /**
         * Flattens tiled items produced in to a [List] whichever way you desire
         */
        data class CustomList<Query, Item>(
            override val comparator: Comparator<Query>,
            val transform: Metadata<Query>.(Map<Query, Tile<Query, Item>>) -> List<Item>,
        ) : Order<Query, Item>(), Input.List<Query, Item>

        /**
         * Flattens tiled items produced in to a [Map] whichever way you desire
         */
        data class CustomMap<Query, Item>(
            override val comparator: Comparator<Query>,
            val transform: Metadata<Query>.(Map<Query, Tile<Query, Item>>) -> Map<Query, Item>,
        ) : Order<Query, Item>(), Input.Map<Query, Item>
    }

    /**
     * Limits the output of the [tiledList] or [tiledMap] functions
     */
    sealed class Limiter<Query, Item, Output> : Input<Query, Item> {
        data class List<Query, Item>(
            val check: (
                kotlin.collections.List<Item>
            ) -> Boolean
        ) : Limiter<Query, Item, kotlin.collections.List<Item>>(), Input.List<Query, Item>

        data class Map<Query, Item>(
            val check: (
                kotlin.collections.Map<Query, Item>
            ) -> Boolean
        ) : Limiter<Query, Item, kotlin.collections.Map<Query, Item>>(), Input.Map<Query, Item>
    }

    /**
     * Summary of changes that can occur as a result of tiling
     */
    internal sealed class Output<Query, Item> {
        data class Data<Query, Item>(
            val query: Query,
            val tile: Tile<Query, Item>
        ) : Output<Query, Item>()

        data class FlattenChange<Query, Item>(
            val order: Order<Query, Item>
        ) : Output<Query, Item>()

        data class LimiterChange<Query, Item>(
            val limiter: Limiter<Query, Item, *>
        ) : Output<Query, Item>()

        data class Eviction<Query, Item>(
            val query: Query,
        ) : Output<Query, Item>()

        data class TurnedOn<Query, Item>(
            val query: Query,
        ) : Output<Query, Item>()
    }
}

/**
 * Convenience method to convert a [Flow] of [Tile.Input.List] to a [Flow] of a [List] of [Item]s
 */
fun <Query, Item> Flow<Tile.Input.List<Query, Item>>.toTiledList(
    transform: ListTiler<Query, Item>
): Flow<List<Item>> = transform(this)

/**
 * Convenience method to convert a [Flow] of [Tile.Input.Map] to a [Flow] of a [Map] of [Query] to
 * [Item]s
 */
fun <Query, Item> Flow<Tile.Input.Map<Query, Item>>.toTiledMap(
    transform: MapTiler<Query, Item>
): Flow<Map<Query, Item>> = transform(this)

/**
 * Converts a [Flow] of [Query] into a [Flow] of [List] [Item]
 */
fun <Query, Item> tiledList(
    limiter: Tile.Limiter.List<Query, Item> = Tile.Limiter.List { false },
    order: Tile.Order<Query, Item> = Tile.Order.Unspecified(),
    fetcher: suspend (Query) -> Flow<Item>
): ListTiler<Query, Item> = { requests ->
    tilerFactory(
        limiter = limiter,
        order = order,
        fetcher = fetcher
    )
        .invoke(requests)
        .map(Tiler<Query, Item, List<Item>>::output)
}

/**
 * Converts a [Flow] of [Query] into a [Flow] of [Map] [Query] to [Item]
 */
fun <Query, Item> tiledMap(
    limiter: Tile.Limiter.Map<Query, Item> = Tile.Limiter.Map { false },
    order: Tile.Order<Query, Item> = Tile.Order.Unspecified(),
    fetcher: suspend (Query) -> Flow<Item>
): MapTiler<Query, Item> = { requests ->
    tilerFactory(
        limiter = limiter,
        order = order,
        fetcher = fetcher
    )
        .invoke(requests)
        .map(Tiler<Query, Item, Map<Query, Item>>::output)
}
