package com.tunjid.tyler

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * class holding meta data about a [Query] for an [Item], the [Item], and when the [Query] was sent
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

    sealed interface Input<Query, Item>

    sealed class Request<Query, Item> : Input<Query, Item> {
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

    sealed class Flattener<Query, Item> : Input<Query, Item>,
            (Map<Query, Tile<Query, Item>>) -> List<Item> {

        abstract val comparator: Comparator<Query>
        abstract val metadata: Metadata<Query>

        /**
         * Items will be returned in an unspecified order; the order is whatever the iteration
         * order of the backing map of [Query] to [Item] uses
         */
        internal data class Unspecified<Query, Item>(
            override val comparator: Comparator<Query> = Comparator { _, _ -> 0 },
            override val metadata: Metadata<Query> = Metadata(),
        ) : Flattener<Query, Item>()

        /**
         * Sort items with the specified query [comparator].
         * [limiter] can be used to select a subset of items instead of the whole set
         */
        data class Sorted<Query, Item>(
            override val comparator: Comparator<Query>,
            override val metadata: Metadata<Query> = Metadata(),
            val limiter: (List<Item>) -> Boolean = { false },
        ) : Flattener<Query, Item>()

        /**
         * Sort items with the specified [comparator] but pivoted around the last query a
         * [Tile.Request.On] was sent for. This allows for showing items that have more priority
         * over others in the current context
         * [limiter] can be used to select a subset of items instead of the whole set
         */
        data class PivotSorted<Query, Item>(
            override val comparator: Comparator<Query>,
            override val metadata: Metadata<Query> = Metadata(),
            val limiter: (List<Item>) -> Boolean = { false },
        ) : Flattener<Query, Item>()

        /**
         * Flattens tiled items produced whichever way you desire
         */
        data class Custom<Query, Item>(
            override val comparator: Comparator<Query>,
            override val metadata: Metadata<Query> = Metadata(),
            val transform: Metadata<Query>.(Map<Query, Tile<Query, Item>>) -> List<Item>,
        ) : Flattener<Query, Item>()

        override fun invoke(queryToTiles: Map<Query, Tile<Query, Item>>): List<Item> =
            flatten(queryToTiles)
    }


    internal sealed class Output<Query, Item> {
        data class Data<Query, Item>(
            val query: Query,
            val tile: Tile<Query, Item>
        ) : Output<Query, Item>()

        data class FlattenChange<Query, Item>(
            val flattener: Flattener<Query, Item>
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
fun <Query, Item> Flow<Tile.Input<Query, Item>>.flattenWith(
    tiler: (Flow<Tile.Input<Query, Item>>) -> Flow<List<Item>>
): Flow<List<Item>> = tiler(this)

/**
 * Converts a [Flow] of [Query] into a [Flow] of [List] [Item]
 */
@FlowPreview
@ExperimentalCoroutinesApi
fun <Query, Item> tiledList(
    flattener: Tile.Flattener<Query, Item> = Tile.Flattener.Unspecified(),
    fetcher: suspend (Query) -> Flow<Item>
): (Flow<Tile.Input<Query, Item>>) -> Flow<List<Item>> = { requests ->
    tileFactory(flattener = flattener, fetcher = fetcher)
        .invoke(requests)
        .map(Tiler<Query, Item>::items)
}
