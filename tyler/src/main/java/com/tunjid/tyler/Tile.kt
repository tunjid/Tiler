package com.tunjid.tyler

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan

/**
 * class holding meta data about a [Query] for an [Item], the [Item], and when the [Query] was sent
 */
internal data class TileData<Query, Item : Any?>(
    val flowOnAt: Long,
    val query: Query,
    val item: Item,
)

sealed class Tile<Query, Item> {

    sealed class Request<Query, Item> : Tile<Query, Item>() {
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

    sealed class ItemOrder<Query, Item> : Tile<Query, Item>() {
        /**
         * Items will be returned in an unspecified order; the order is whatever the iteration
         * order of the backing map of [Query] to [Item] uses
         */
        data class Unspecified<Query, Item>(
            private val id: String = "",
        ) : ItemOrder<Query, Item>()

        /**
         * Sort items with the specified query [comparator].
         * [limiter] can be used to select a subset of items instead of the whole set
         */
        data class Sort<Query, Item>(
            val comparator: Comparator<Query>,
            val limiter: (List<Item>) -> Boolean = { false },
        ) : ItemOrder<Query, Item>()

        /**
         * Sort items with the specified [comparator] but pivoted around the last time a
         * [Tile.Request.On] was sent. This allows for showing items that have more priority
         * over others in the current context
         * [limiter] can be used to select a subset of items instead of the whole set
         */
        data class PivotedSort<Query, Item>(
            val comparator: Comparator<Query>,
            val limiter: (List<Item>) -> Boolean = { false },
        ) : ItemOrder<Query, Item>()
    }
}

/**
 * Converts a [Flow] of [Query] into a [Flow] of a [Map] of [Query] to tiles representing that
 * [Item] and when it was requested
 */
@FlowPreview
@ExperimentalCoroutinesApi
fun <Query, Item> tiler(
    itemOrder: Tile.ItemOrder<Query, Item> = Tile.ItemOrder.Unspecified(),
    fetcher: suspend (Query) -> Flow<Item>
): (Flow<Tile<Query, Item>>) -> Flow<List<Item>> = { requests ->
    requests
        .scan(
            initial = TileFactory(fetcher = fetcher),
            operation = TileFactory<Query, Item>::add
        )
        // Collect each tile flow independently and merge the results
        .flatMapMerge(
            concurrency = Int.MAX_VALUE,
            transform = { it.flow }
        )
        .scan(
            initial = Tiler(itemOrder = itemOrder),
            operation = Tiler<Query, Item>::add
        )
        .map(Tiler<Query, Item>::items)
}

/**
 * Convenience method to convert a [Flow] of [Tile] to a [Flow] of [TileData]s
 */
fun <Query, Item> Flow<Tile<Query, Item>>.tiledWith(
    tiler: (Flow<Tile<Query, Item>>) -> Flow<List<Item>>
) = tiler(this)