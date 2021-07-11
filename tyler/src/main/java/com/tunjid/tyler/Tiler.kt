package com.tunjid.tyler

/**
 * Flattens a [Map] of [Query] to [Item] to a [List] of [Item]
 */
internal data class Tiler<Query, Item>(
    val shouldEmit: Boolean = false,
    val order: Tile.Order<Query, Item> = Tile.Order.Unspecified(),
    // I'd rather this be immutable, electing against it for performance reasons
    val queryToTiles: MutableMap<Query, Tile<Query, Item>> = mutableMapOf(),
) {

    fun add(output: Tile.Output<Query, Item>): Tiler<Query, Item> = when (output) {
        is Tile.Output.Data -> copy(
            shouldEmit = true,
            queryToTiles = queryToTiles.apply { put(output.query, output.tile) }
        )
        is Tile.Output.Started -> copy(
            shouldEmit = false,
            order = order.updateQueries(
                order.metadata.sortedQueries
                    .plus(output.query)
                    .distinct()
                    .sortedWith(order.comparator)
            )
        )
        is Tile.Output.Eviction -> copy(
            shouldEmit = true,
            order = order.updateQueries(order.metadata.sortedQueries - output.query),
            queryToTiles = queryToTiles.apply { remove(output.query) }
        )
        is Tile.Output.Flattener -> copy(
            shouldEmit = true,
            order = output.order.updateQueries(order.metadata.sortedQueries.sortedWith(output.order.comparator))
        )
    }

    fun items(): List<Item> = order(queryToTiles)
}

internal fun <Query, Item> Tile.Order<Query, Item>.flatten(
    queryToTiles: Map<Query, Tile<Query, Item>>
): List<Item> {
    return when (val order = this) {
        is Tile.Order.Unspecified -> queryToTiles.keys
            .fold(mutableListOf()) { list, query ->
                list.add(element = queryToTiles.getValue(query).item)
                list
            }
        is Tile.Order.Sorted -> queryToTiles.keys
            .sortedWith(order.comparator)
            .foldWhile(mutableListOf(), order.limiter) { list, query ->
                list.add(element = queryToTiles.getValue(query).item)
                list
            }
        is Tile.Order.PivotSorted -> {
            val sorted = order.metadata.sortedQueries

            // TODO: Amortize this as well.
            val mostRecentQuery: Query = queryToTiles.keys
                .maxByOrNull { queryToTiles.getValue(it).flowOnAt }
                ?: return emptyList()

            val startIndex = sorted.indexOf(mostRecentQuery)
            var leftIndex = startIndex
            var rightIndex = startIndex
            val result = mutableListOf(queryToTiles.getValue(sorted[startIndex]).item)

            while (!order.limiter(result) && (leftIndex >= 0 || rightIndex <= sorted.lastIndex)) {
                if (--leftIndex >= 0) result.add(
                    index = 0,
                    element = queryToTiles.getValue(sorted[leftIndex]).item
                )
                if (++rightIndex <= sorted.lastIndex) result.add(
                    element = queryToTiles.getValue(sorted[rightIndex]).item
                )
            }
            result
        }
        is Tile.Order.Custom -> order.transform(queryToTiles)
    }
}

internal inline fun <T, R> Iterable<T>.foldWhile(
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