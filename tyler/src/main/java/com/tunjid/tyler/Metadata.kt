package com.tunjid.tyler

internal fun <Query, Item> Tile.Order<Query, Item>.updateQueries(
    queries: List<Query>
): Tile.Order<Query, Item> = updateMetadata(metadata.copy(sortedQueries = queries))

private fun <Query, Item> Tile.Order<Query, Item>.updateMetadata(
    updatedMetadata: Tile.Metadata<Query>
): Tile.Order<Query, Item> = when (val order = this) {
    is Tile.Order.Custom -> order.copy(metadata = updatedMetadata)
    is Tile.Order.PivotSorted -> order.copy(metadata = updatedMetadata)
    is Tile.Order.Sorted -> order.copy(metadata = updatedMetadata)
    is Tile.Order.Unspecified -> order.copy(metadata = updatedMetadata)
}