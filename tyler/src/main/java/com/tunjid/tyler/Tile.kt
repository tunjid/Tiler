package com.tunjid.tyler

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.scan

/**
 * class holding meta data about a [Query] for an [Item], the [Item], and when the [Query] was sent
 */
data class Tile<Query, Item : Any?>(
    val flowOnAt: Long,
    val item: Item,
) {

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

    sealed class Order<Query, Item> : Input<Query, Item>,
            (Map<Query, Tile<Query, Item>>) -> List<Item> {

        abstract val comparator: Comparator<Query>
        abstract val sortedQueries: List<Query>

        /**
         * Items will be returned in an unspecified order; the order is whatever the iteration
         * order of the backing map of [Query] to [Item] uses
         */
       internal data class Unspecified<Query, Item>(
            override val comparator: Comparator<Query> = Comparator { _, _ ->  0 },
            override val sortedQueries: List<Query> = listOf(),
            ) : Order<Query, Item>()

        /**
         * Sort items with the specified query [comparator].
         * [limiter] can be used to select a subset of items instead of the whole set
         */
        data class Sorted<Query, Item>(
            override val comparator: Comparator<Query>,
            override val sortedQueries: List<Query> = listOf(),
            val limiter: (List<Item>) -> Boolean = { false },
        ) : Order<Query, Item>()

        /**
         * Sort items with the specified [comparator] but pivoted around the last time a
         * [Tile.Request.On] was sent. This allows for showing items that have more priority
         * over others in the current context
         * [limiter] can be used to select a subset of items instead of the whole set
         */
        data class PivotSorted<Query, Item>(
            override val comparator: Comparator<Query>,
            override val sortedQueries: List<Query> = listOf(),
            val limiter: (List<Item>) -> Boolean = { false },
        ) : Order<Query, Item>()

        /**
         * Flattens tiled items produced whichever way you desire
         */
        data class Custom<Query, Item>(
            override val comparator: Comparator<Query>,
            override val sortedQueries: List<Query> = listOf(),
            val transform: (Map<Query, Tile<Query, Item>>) -> List<Item>,
        ) : Order<Query, Item>()

        override fun invoke(queryToTiles: Map<Query, Tile<Query, Item>>): List<Item> =
            flatten(queryToTiles)
    }


    internal sealed class Output<Query, Item> {
        data class Data<Query, Item>(
            val query: Query,
            val tile: Tile<Query, Item>
        ) : Output<Query, Item>()

        data class Flattener<Query, Item>(
            val order: Order<Query, Item>
        ) : Output<Query, Item>()

        data class Eviction<Query, Item>(
            val query: Query,
        ) : Output<Query, Item>()

        data class Started<Query, Item>(
            val query: Query,
        ) : Output<Query, Item>()
    }
}

/**
 * Converts a [Flow] of [Query] into a [Flow] of a [Map] of [Query] to [Tile]
 */
@FlowPreview
@ExperimentalCoroutinesApi
fun <Query, Item> tiles(
    fetcher: suspend (Query) -> Flow<Item>
): (Flow<Tile.Input<Query, Item>>) -> Flow<Map<Query, Tile<Query, Item>>> = { requests ->
    rawTiler(fetcher = fetcher)
        .invoke(requests)
        .map { it.queryToTiles }
}

/**
 * Converts a [Flow] of [Query] into a [Flow] of [List] [Item]
 */
@FlowPreview
@ExperimentalCoroutinesApi
fun <Query, Item> flattenedTiles(
    order: Tile.Order<Query, Item> = Tile.Order.Unspecified(),
    fetcher: suspend (Query) -> Flow<Item>
): (Flow<Tile.Input<Query, Item>>) -> Flow<List<Item>> = { requests ->
    rawTiler(order = order, fetcher = fetcher)
        .invoke(requests)
        .map(Tiler<Query, Item>::items)
}

/**
 * Convenience method to convert a [Flow] of [Tile.Input] to a [Flow] of a [List] of [Item]s
 */
fun <Query, Item> Flow<Tile.Input<Query, Item>>.flattenWith(
    tiler: (Flow<Tile.Input<Query, Item>>) -> Flow<List<Item>>
) = tiler(this)

/**
 * Convenience method to convert a [Flow] of [Tile.Input] to a [Flow] of a [Map] of [Query] to [Item]s
 */
fun <Query, Item> Flow<Tile.Input<Query, Item>>.tileWith(
    tiles: (Flow<Tile.Input<Query, Item>>) -> Flow<Map<Query, Tile<Query, Item>>>
) = tiles(this)

@FlowPreview
@ExperimentalCoroutinesApi
internal fun <Query, Item> rawTiler(
    order: Tile.Order<Query, Item> = Tile.Order.Unspecified(),
    fetcher: suspend (Query) -> Flow<Item>
): (Flow<Tile.Input<Query, Item>>) -> Flow<Tiler<Query, Item>> = { requests ->
    requests
        .scan(
            initial = TileFactory(fetcher = fetcher),
            operation = TileFactory<Query, Item>::process
        )
        // Collect each tile flow independently and merge the results
        .flatMapMerge(
            concurrency = Int.MAX_VALUE,
            transform = { it.flow }
        )
        .scan(
            initial = Tiler(order = order),
            operation = Tiler<Query, Item>::add
        )
        .filter { it.shouldEmit  }
}
