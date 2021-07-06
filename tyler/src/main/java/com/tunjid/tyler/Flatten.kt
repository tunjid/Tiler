package com.tunjid.tyler


internal data class Flatten<Query, Item>(
    val get: TileRequest.Get<Query, Item> = TileRequest.Get.InsertionOrder(),
    val queryToTiles: MutableMap<Query, Tile<Query, Item>> = mutableMapOf(),
) {

    fun add(result: Result<Query, Item>): Flatten<Query, Item> = when (result) {
        is Result.Data -> copy().apply { queryToTiles[result.query] = result.tile }
        is Result.None -> copy().apply { queryToTiles.remove(result.query) }.copy()
        is Result.Order -> copy(get = result.get)
    }

    fun items(): List<Item> {
        return when (get) {
            is TileRequest.Get.InsertionOrder -> queryToTiles.keys
                .fold(mutableListOf()) { list, query ->
                    list.add(element = queryToTiles.getValue(query).item)
                    list
                }
            is TileRequest.Get.StrictOrder -> queryToTiles.keys
                .sortedWith(get.comparator)
                .foldWhile(mutableListOf(), get.limiter) { list, query ->
                    list.add(element = queryToTiles.getValue(query).item)
                    list
                }
            is TileRequest.Get.Pivoted -> {
                // Sort the keys, should be relatively cheap
                val sorted = queryToTiles.keys
                    .sortedWith(get.comparator)

                val mostRecentQuery: Query = queryToTiles.keys
                    .maxByOrNull { queryToTiles.getValue(it).flowOnAt }
                    ?: return emptyList()

                val result = mutableListOf<Item>()
                val startIndex = sorted.indexOf(mostRecentQuery)
                var leftIndex = startIndex
                var rightIndex = startIndex

                while (!get.limiter(result) && (leftIndex >= 0 || rightIndex <= sorted.lastIndex)) {
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

//while (result.size < maxCount && (leftIndex >= 0 || rightIndex <= sorted.lastIndex)) {
//    if (--leftIndex >= 0) result.add(
//        index = 0,
//        element = queriesToTile.getValue(sorted[leftIndex]).item
//    )
//    if (++rightIndex <= sorted.lastIndex) result.add(
//        element = queriesToTile.getValue(sorted[rightIndex]).item
//    )
//}

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