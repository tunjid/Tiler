package com.tunjid.tyler

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan

sealed class TileRequest<Query, Item> {

    sealed class Request<Query, Item> : TileRequest<Query, Item>() {
        abstract val query: Query

        data class On<Query, Item>(override val query: Query) : Request<Query, Item>()
        data class Off<Query, Item>(override val query: Query) : Request<Query, Item>()
        data class Eject<Query, Item>(override val query: Query) : Request<Query, Item>()
    }

    sealed class Get<Query, Item> : TileRequest<Query, Item>() {
        data class InsertionOrder<Query, Item>(
            val id: String = "",
        ) : Get<Query, Item>()

        data class StrictOrder<Query, Item>(
            val comparator: Comparator<Query>,
            val limiter: (List<Item>) -> Boolean = { false },
        ) : Get<Query, Item>()

        data class Pivoted<Query, Item>(
            val comparator: Comparator<Query>,
            val limiter: (List<Item>) -> Boolean = { false },
        ) : Get<Query, Item>()
    }
}

/**
 * Converts a [Flow] of [Query] into a [Flow] of a [Map] of [Query] to tiles representing that
 * [Item] and when it was requested
 */
@FlowPreview
@ExperimentalCoroutinesApi
fun <Query, Item> tiles(
    fetcher: suspend (Query) -> Flow<Item>
): (Flow<TileRequest<Query, Item>>) -> Flow<List<Item>> = { requests ->
    requests
        .scan(
            initial = Tiles(fetcher = fetcher),
            operation = Tiles<Query, Item>::add
        )
        // Collect each tile flow independently and merge the results
        .flatMapMerge(
            concurrency = Int.MAX_VALUE,
            transform = { it.flow }
        )
        .scan(
            // This is a Mutable map solely for performance reasons. It is exposed as an immutable map
            initial = Flatten(),
            operation = Flatten<Query, Item>::add
        )
        .map(Flatten<Query, Item>::items)
}

/**
 * Convenience method to convert a [Flow] of [TileRequest] to a [Flow] of [Tile]s
 */
fun <Query, Item> Flow<TileRequest<Query, Item>>.tiles(
    tiler: (Flow<TileRequest<Query, Item>>) -> Flow<List<Item>>
) = tiler(this)