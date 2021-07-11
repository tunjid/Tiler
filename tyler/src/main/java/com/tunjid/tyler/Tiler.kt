package com.tunjid.tyler

/**
 * Flattens a [Map] of [Query] to [Item] to a [List] of [Item]
 */
internal data class Tiler<Query, Item>(
    val shouldEmit: Boolean = false,
    val flattener: Tile.Flattener<Query, Item> = Tile.Flattener.Unspecified(),
    // I'd rather this be immutable, electing against it for performance reasons
    val queryToTiles: MutableMap<Query, Tile<Query, Item>> = mutableMapOf(),
) {

    fun add(output: Tile.Output<Query, Item>): Tiler<Query, Item> = when (output) {
        is Tile.Output.Data -> copy(
            shouldEmit = true,
            // Only sort queries when they output the first time to amortize the cost of sorting.
            flattener = when {
                queryToTiles.contains(output.query) -> flattener
                else -> flattener.updateQueries(
                    flattener.metadata.sortedQueries
                        .plus(output.query)
                        .distinct()
                        .sortedWith(flattener.comparator)
                )
            },
            queryToTiles = queryToTiles.apply { put(output.query, output.tile) }
        )
        is Tile.Output.TurnedOn -> copy(
            // Only emit if there is cached data
            shouldEmit = queryToTiles.contains(output.query),
            flattener = flattener.updateMetadata(flattener.metadata.copy(mostRecentlyTurnedOn = output.query))
        )
        is Tile.Output.Eviction -> copy(
            shouldEmit = true,
            flattener = flattener.updateQueries(flattener.metadata.sortedQueries - output.query),
            queryToTiles = queryToTiles.apply { remove(output.query) }
        )
        is Tile.Output.FlattenChange -> copy(
            shouldEmit = true,
            flattener = output.flattener.updateQueries(flattener.metadata.sortedQueries.sortedWith(output.flattener.comparator))
        )
    }

    fun items(): List<Item> = flattener(queryToTiles)
}

internal fun <Query, Item> Tile.Flattener<Query, Item>.flatten(
    queryToTiles: Map<Query, Tile<Query, Item>>
): List<Item> {
    val sortedQueries = metadata.sortedQueries

    return when (val order = this) {
        is Tile.Flattener.Unspecified -> queryToTiles.keys
            .fold(mutableListOf()) { list, query ->
                list.add(element = queryToTiles.getValue(query).item)
                list
            }
        is Tile.Flattener.Sorted -> sortedQueries
            .foldWhile(mutableListOf(), order.limiter) { list, query ->
                list.add(element = queryToTiles.getValue(query).item)
                list
            }
        is Tile.Flattener.PivotSorted -> {
            val mostRecentQuery: Query = metadata.mostRecentlyTurnedOn ?: return emptyList()

            val startIndex = sortedQueries.indexOf(mostRecentQuery)
            var leftIndex = startIndex
            var rightIndex = startIndex
            val result = mutableListOf(queryToTiles.getValue(sortedQueries[startIndex]).item)

            while (!order.limiter(result) && (leftIndex >= 0 || rightIndex <= sortedQueries.lastIndex)) {
                if (--leftIndex >= 0) result.add(
                    index = 0,
                    element = queryToTiles.getValue(sortedQueries[leftIndex]).item
                )
                if (++rightIndex <= sortedQueries.lastIndex) result.add(
                    element = queryToTiles.getValue(sortedQueries[rightIndex]).item
                )
            }
            result
        }
        is Tile.Flattener.Custom -> order.transform(order.metadata, queryToTiles)
    }
}

private fun <Query, Item> Tile.Flattener<Query, Item>.updateQueries(
    queries: List<Query>
): Tile.Flattener<Query, Item> = updateMetadata(metadata.copy(sortedQueries = queries))

private fun <Query, Item> Tile.Flattener<Query, Item>.updateMetadata(
    updatedMetadata: Tile.Metadata<Query>
): Tile.Flattener<Query, Item> = when (val order = this) {
    is Tile.Flattener.Custom -> order.copy(metadata = updatedMetadata)
    is Tile.Flattener.PivotSorted -> order.copy(metadata = updatedMetadata)
    is Tile.Flattener.Sorted -> order.copy(metadata = updatedMetadata)
    is Tile.Flattener.Unspecified -> order.copy(metadata = updatedMetadata)
}

private inline fun <T, R> Iterable<T>.foldWhile(
    initial: R,
    limiter: (R) -> Boolean,
    operation: (acc: R, T) -> R
): R {
    var accumulator = initial
    for (element in this) {
        accumulator = operation(accumulator, element)
        if (limiter(accumulator)) return accumulator
    }
    return accumulator
}