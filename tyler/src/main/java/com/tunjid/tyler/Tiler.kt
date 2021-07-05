package com.tunjid.tyler

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.scan

sealed class TileRequest<Query> {
    abstract val query: Query

    data class On<Query>(override val query: Query) : TileRequest<Query>()
    data class Off<Query>(override val query: Query) : TileRequest<Query>()
    data class Eject<Query>(override val query: Query) : TileRequest<Query>()
}

/**
 * Converts a [Flow] of [Query] into a [Flow] of a [Map] of [Query] to tiles representing that
 * [Item]
 */
@FlowPreview
@ExperimentalCoroutinesApi
fun <Query, Item> tiles(
    fetcher: suspend (Query) -> Flow<Item>
): (Flow<TileRequest<Query>>) -> Flow<Map<Query, Pair<Long, Item>>> = { requests ->
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
            initial = mutableMapOf(),
            operation = { mutableMap, result ->
                when (result) {
                    is Result.Data -> mutableMap.apply { put(result.query, result.tile.toPair) }
                    is Result.None -> mutableMap.apply { remove(result.query) }
                }
            }
        )
}

/**
 * Convenience method to convert a [Flow] of [TileRequest] to a [Flow] of [Tile]s
 */
fun <Query, Item> Flow<TileRequest<Query>>.tiles(
    tiler: (Flow<TileRequest<Query>>) ->Flow<Map<Query, Pair<Long, Item>>>
) = tiler(this)