package com.tunjid.tyler

fun <Query, Item> Map<Query, Pair<Long, Item>>.sortAndFlatten(
    comparator: Comparator<Query>
): Sequence<Item> =
    keys
        .asSequence()
        .sortedWith(comparator)
        .fold(emptySequence()) { sequence, query ->
            val tile = this.getValue(query)
            sequence + sequenceOf(tile.second)
        }

fun <Query, Item> Map<Query, Pair<Long, Item>>.pivotSortAndFlatten(
    comparator: Comparator<Query>
): Sequence<Item> {
    // Sort the keys, should be relatively cheap
    val sorted = keys
        .sortedWith(comparator)

    val mostRecentQuery: Query = keys
        .maxByOrNull { getValue(it).first }
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
        .map(Pair<Long, Item>::second)
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