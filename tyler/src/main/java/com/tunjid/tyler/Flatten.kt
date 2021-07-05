package com.tunjid.tyler

fun <Query, Item> Map<Query, Tile<Query, Item>>.sortAndFlatten(
    comparator: Comparator<Query>
): Sequence<Item> =
    keys
        .asSequence()
        .sortedWith(comparator)
        .fold(emptySequence()) { sequence, query ->
            val tile = this.getValue(query)
            sequence + sequenceOf(tile.item)
        }

fun <Query, Item> Map<Query, Tile<Query, Item>>.pivotSortAndFlatten(
    comparator: Comparator<Query>
): Sequence<Item> {
    // Sort the keys, should be relatively cheap
    val sorted = keys
        .sortedWith(comparator)

    val mostRecentQuery: Query = values
        .maxByOrNull(Tile<Query, Item>::flowOnAt)
        ?.request
        ?.query
        ?: return emptySequence()

    val startIndex = sorted.indexOf(mostRecentQuery)
    var leftIndex = startIndex
    var rightIndex = startIndex

    val pivotedIndices = mutableListOf(startIndex)

    while (leftIndex >= 0 || rightIndex <= sorted.lastIndex) {
        if (--leftIndex >= 0) pivotedIndices.add(
            index = 0,
            element = leftIndex
        )
        if (++rightIndex <= sorted.lastIndex) pivotedIndices.add(
            element = rightIndex
        )
    }

    return pivotedIndices
        .asSequence()
        .map(sorted::get)
        .map(this::getValue)
        .map(Tile<Query, Item>::item)
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