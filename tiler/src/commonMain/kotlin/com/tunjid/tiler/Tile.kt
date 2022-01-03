/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tunjid.tiler

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
    sealed class Flattener<Query, Item> : Input<Query, Item> {

        abstract val comparator: Comparator<Query>

        /**
         * Items will be returned in an unspecified order; the order is whatever the iteration
         * order of the backing map of [Query] to [Item] uses
         */
        internal data class Unspecified<Query, Item>(
            override val comparator: Comparator<Query> = Comparator { _, _ -> 0 },
        ) : Flattener<Query, Item>(), Input.Agnostic<Query, Item>

        /**
         * Sort items with the specified query [comparator].
         */
        data class Sorted<Query, Item>(
            override val comparator: Comparator<Query>,
        ) : Flattener<Query, Item>(), Input.Agnostic<Query, Item>

        /**
         * Sort items with the specified [comparator] but pivoted around the last query a
         * [Tile.Request.On] was sent for. This allows for showing items that have more priority
         * over others in the current context
         */
        data class PivotSorted<Query, Item>(
            override val comparator: Comparator<Query>,
        ) : Flattener<Query, Item>(), Input.Agnostic<Query, Item>

        /**
         * Flattens tiled items produced in to a [List] whichever way you desire
         */
        data class CustomList<Query, Item>(
            override val comparator: Comparator<Query>,
            val transform: Metadata<Query>.(Map<Query, Tile<Query, Item>>) -> List<Item>,
        ) : Flattener<Query, Item>(), Input.List<Query, Item>

        /**
         * Flattens tiled items produced in to a [Map] whichever way you desire
         */
        data class CustomMap<Query, Item>(
            override val comparator: Comparator<Query>,
            val transform: Metadata<Query>.(Map<Query, Tile<Query, Item>>) -> Map<Query, Item>,
        ) : Flattener<Query, Item>(), Input.Map<Query, Item>
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
            val flattener: Flattener<Query, Item>
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
 * Convenience method to convert a [Flow] of [Tile.Input] to a [Flow] of a [List] of [Item]s
 */
fun <Query, Item> Flow<Tile.Input.List<Query, Item>>.flattenWith(
    transform: (Flow<Tile.Input.List<Query, Item>>) -> Flow<List<Item>>
): Flow<List<Item>> = transform(this)

/**
 * Converts a [Flow] of [Query] into a [Flow] of [List] [Item]
 */
@FlowPreview
@ExperimentalCoroutinesApi
fun <Query, Item> tiledList(
    limiter: Tile.Limiter.List<Query, Item> = Tile.Limiter.List { false },
    flattener: Tile.Flattener<Query, Item> = Tile.Flattener.Unspecified(),
    fetcher: suspend (Query) -> Flow<Item>
): (Flow<Tile.Input.List<Query, Item>>) -> Flow<List<Item>> = { requests ->
    tileFactory(
        limiter = limiter,
        flattener = flattener,
        fetcher = fetcher
    )
        .invoke(requests)
        .map(Tiler<Query, Item, List<Item>>::output)
}

/**
 * Converts a [Flow] of [Query] into a [Flow] of [Map] [Query] to [Item]
 */
@FlowPreview
@ExperimentalCoroutinesApi
fun <Query, Item> tiledMap(
    limiter: Tile.Limiter.Map<Query, Item> = Tile.Limiter.Map { false },
    flattener: Tile.Flattener<Query, Item> = Tile.Flattener.Unspecified(),
    fetcher: suspend (Query) -> Flow<Item>
): (Flow<Tile.Request<Query, Item>>) -> Flow<Map<Query, Item>> = { requests ->
    tileFactory(
        limiter = limiter,
        flattener = flattener,
        fetcher = fetcher
    )
        .invoke(requests)
        .map(Tiler<Query, Item, Map<Query, Item>>::output)
}
