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

fun <Query, Item> tiles(
    fetcher: suspend (Query) -> Flow<List<Item>>
): (Flow<TileRequest<Query>>) -> Flow<Map<Query, Tile<Query, Item>>> = { requests ->
    requests
        .scan(
            initial = Wall(fetcher = fetcher),
            operation = Wall<Query, Item>::add
        )
        // Collect each tile flow independently and merge the results
        .flatMapMerge(
            concurrency = Int.MAX_VALUE,
            transform = Wall<Query, Item>::flow
        )
        .scan(
            initial = Tiles(),
            operation = Tiles<Query, Item>::add
        )
}

/**
 * Flattens
 */
 internal data class Tiles<Query, Item>(
    // This is a Mutable map solely for performance reasons
    val queriesToTile: MutableMap<Query, Tile<Query, Item>> = mutableMapOf()
) : Map<Query, Tile<Query, Item>> by queriesToTile {

    fun add(tile: Tile<Query, Item>) = copy(
        queriesToTile = queriesToTile.apply {
            when(tile.request) {
                is TileRequest.Eject -> remove(tile.request.query)
                // Should not happen, the flow should be off
                is TileRequest.Off -> put(tile.request.query, tile)
                is TileRequest.On -> put(tile.request.query, tile)
            }
        }
    )
}

data class Tile<Query, Item>(
    val flowOnAt: Long,
    val request: TileRequest<Query>,
    val items: List<Item>,
)