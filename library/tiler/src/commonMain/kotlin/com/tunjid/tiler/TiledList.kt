package com.tunjid.tiler

import com.tunjid.utilities.TransformedTiledList

/**
 * A [List] where each item is backed by a [Query].
 *
 * Note that [TiledList] instances should not be large. They should only contain enough
 * items to fill the device viewport a few items over to accommodate a user's scroll.
 * This is typically under 100 items.
 */
interface TiledList<Query, Item> : List<Item> {
    /**
     * Returns the query that fetched an [Item]
     */
    fun queryFor(index: Int): Query
}

fun <Query, Item> emptyTiledList(): TiledList<Query, Item> =
    object : TiledList<Query, Item>, List<Item> by emptyList() {
        override fun queryFor(index: Int): Query {
            throw IndexOutOfBoundsException("The TiledList is empty")
        }
    }

/**
 * Transforms a [TiledList] to another
 */
fun <Query, Item> TiledList<Query, Item>.transform(
    transformer: List<Item>.() -> List<Item>
): TiledList<Query, Item> = TransformedTiledList(
    originalList = this,
    transformedList = transformer(this)
)