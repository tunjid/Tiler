package com.tunjid.tyler

/**
 * Flattens a [Map] of [Query] to [Item] to a [List]
 */
internal data class Tiler<Query, Item>(
    val itemOrder: Tile.ItemOrder<Query, Item> = Tile.ItemOrder.Unspecified(),
    // I'd rather this be immutable, electing against it for performance reasons
    val queryToTiles: MutableMap<Query, TileData<Query, Item>> = mutableMapOf(),
) {

    fun add(output: Output<Query, Item>): Tiler<Query, Item> = when (output) {
        is Output.Data -> copy(queryToTiles = queryToTiles.apply { put(output.query, output.tile) })
        is Output.Evict -> copy(queryToTiles = queryToTiles.apply { remove(output.query) })
        is Output.Order -> copy(itemOrder = output.itemOrder)
    }

    fun items(): List<Item> {
        return when (itemOrder) {
            is Tile.ItemOrder.Unspecified -> queryToTiles.keys
                .fold(mutableListOf()) { list, query ->
                    list.add(element = queryToTiles.getValue(query).item)
                    list
                }
            is Tile.ItemOrder.Sort -> queryToTiles.keys
                .sortedWith(itemOrder.comparator)
                .foldWhile(mutableListOf(), itemOrder.limiter) { list, query ->
                    list.add(element = queryToTiles.getValue(query).item)
                    list
                }
            is Tile.ItemOrder.PivotedSort -> {
                // Sort the keys, should be relatively cheap
                val sorted = queryToTiles.keys
                    .sortedWith(itemOrder.comparator)

                val mostRecentQuery: Query = queryToTiles.keys
                    .maxByOrNull { queryToTiles.getValue(it).flowOnAt }
                    ?: return emptyList()

                val startIndex = sorted.indexOf(mostRecentQuery)
                var leftIndex = startIndex
                var rightIndex = startIndex
                val result = mutableListOf(queryToTiles.getValue(sorted[startIndex]).item)

                while (!itemOrder.limiter(result) && (leftIndex >= 0 || rightIndex <= sorted.lastIndex)) {
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
        }
    }
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