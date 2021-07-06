package com.tunjid.tyler


internal data class Flatten<Query, Item>(
    val itemOrder: TileRequest.ItemOrder<Query, Item> = TileRequest.ItemOrder.Unspecified(),
    val queryToTiles: MutableMap<Query, Tile<Query, Item>> = mutableMapOf(),
) {

    fun add(result: Result<Query, Item>): Flatten<Query, Item> = when (result) {
        is Result.Data -> copy().apply { queryToTiles[result.query] = result.tile }
        is Result.None -> copy().apply { queryToTiles.remove(result.query) }.copy()
        is Result.Order -> copy(itemOrder = result.itemOrder)
    }

    fun items(): List<Item> {
        return when (itemOrder) {
            is TileRequest.ItemOrder.Unspecified -> queryToTiles.keys
                .fold(mutableListOf()) { list, query ->
                    list.add(element = queryToTiles.getValue(query).item)
                    list
                }
            is TileRequest.ItemOrder.Sort -> queryToTiles.keys
                .sortedWith(itemOrder.comparator)
                .foldWhile(mutableListOf(), itemOrder.limiter) { list, query ->
                    list.add(element = queryToTiles.getValue(query).item)
                    list
                }
            is TileRequest.ItemOrder.PivotedSort -> {
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