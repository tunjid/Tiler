package com.tunjid.tyler

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
fun <Query, Item> tiles(
    fetcher: suspend (Query) -> Flow<List<Item>>
): (Flow<TileRequest<Query>>) -> Flow<Map<Query, Tile<Query, Item>>> = { requests ->
    requests
        .scan(
            initial = Tiles(fetcher = fetcher),
            operation = Tiles<Query, Item>::add
        )
        // Collect each tile flow independently and merge the results
        .flatMapMerge(
            concurrency = Int.MAX_VALUE,
            transform = Tiles<Query, Item>::flow
        )
        .scan(
            // This is a Mutable map solely for performance reasons. It is exposed as an immutable map
            initial = mutableMapOf(),
            operation = { mutableMap, tile ->
                mutableMap.apply {
                    when (tile.request) {
                        is TileRequest.Eject -> remove(tile.request.query)
                        // Should not happen, the flow should be off
                        is TileRequest.Off -> put(tile.request.query, tile)
                        is TileRequest.On -> put(tile.request.query, tile)
                    }
                }
            }
        )
}
