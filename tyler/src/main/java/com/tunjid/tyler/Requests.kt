package com.tunjid.tyler

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.scan
import java.util.*

sealed class TileRequest<Query> {
    sealed class Valve<Query>(open val query: Query) : TileRequest<Query>() {
        data class On<Query>(override val query: Query) : Valve<Query>(query)
        data class Off<Query>(override val query: Query) : Valve<Query>(query)
    }

    sealed class Limit<Query> : TileRequest<Query>() {
        data class Eject<Query>(val query: Query) : Limit<Query>()
        data class None<Query>(
            private val id: String = UUID.randomUUID().toString()
        ) : Limit<Query>()
    }
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