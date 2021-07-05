package com.tunjid.tyler

/**
 * Flattens
 */
 internal data class Tiles<Query, Item>(
    val limit: TileRequest.Limit<Query> = TileRequest.Limit.None(),
    // This is a Mutable map solely for performance reasons
    val queriesToTile: MutableMap<Query, Tile<Query, Item>> = mutableMapOf()
) : Map<Query, Tile<Query, Item>> by queriesToTile {

    fun add(tile: Tile<Query, Item>) = copy(
        limit = tile.limit,
        queriesToTile = queriesToTile.apply {
            put(tile.query, tile)
        }
    )
}

data class Tile<Query, Item>(
    val flowOnAt: Long,
    val limit: TileRequest.Limit<Query> = TileRequest.Limit.None(),
    val query: Query,
    val items: List<Item>,
)