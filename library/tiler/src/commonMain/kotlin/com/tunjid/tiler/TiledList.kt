/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tunjid.tiler

import com.tunjid.tiler.utilities.EmptyTiledList
import com.tunjid.tiler.utilities.SparseTiledList

/**
 * A [List] where each item is backed by the [Query] that fetched it.
 *
 * A [Query] fetches one or more items, this association is called a [Tile]. i.e a [Tile] represents
 * a range of items associated with a particular [Query].
 *
 * Note that [TiledList] instances should not be large. They should only contain enough
 * items to fill the device viewport a few items over to accommodate a user's scroll.
 * This is typically under 500 items.
 */
interface TiledList<out Query, out Item> : List<Item> {
    /**
     * The number of [Tile] instances or query ranges there are in this [TiledList]
     */
    val tileCount: Int

    /**
     * Returns the [Tile] at the specified tile index.
     */
    fun tileAt(tileIndex: Int): Tile

    /**
     * Returns the query at the specified tile index.
     */
    fun queryAtTile(tileIndex: Int): Query

    /**
     * Returns the query that fetched an [Item] at a specified index.
     */
    fun queryAt(index: Int): Query
}

/**
 * A [TiledList] with mutation facilities.
 *
 * Note this exists to facilitate transformations on the outputs of a [ListTiler]
 */
interface MutableTiledList<Query, Item> : TiledList<Query, Item> {
    fun add(index: Int, query: Query, item: Item)

    fun add(query: Query, item: Item): Boolean

    fun addAll(query: Query, items: Collection<Item>): Boolean

    fun addAll(index: Int, query: Query, items: Collection<Item>): Boolean

    fun remove(index: Int): Item
}

/**
 * Returns an empty [TiledList] instance
 */
fun <Query, Item> emptyTiledList(): TiledList<Query, Item> =
    EmptyTiledList

/**
 * Returns a read-only [TiledList] instance
 */
fun <Query, Item> tiledListOf(
    vararg pairs: Pair<Query, Item>
): TiledList<Query, Item> =
    if (pairs.isEmpty()) emptyTiledList() else SparseTiledList(*pairs)

/**
 * Returns a [MutableTiledList] instance
 */
fun <Query, Item> mutableTiledListOf(
    vararg pairs: Pair<Query, Item>
): MutableTiledList<Query, Item> =
    SparseTiledList(*pairs)

/**
 * Builds a new read-only List by populating a MutableList using the given builderAction and returning a read-only list with the same elements.
 */
fun <Query, Item> buildTiledList(
    builderAction: MutableTiledList<Query, Item>.() -> Unit
): TiledList<Query, Item> = mutableTiledListOf<Query, Item>()
    .also(builderAction::invoke)

fun <Query, Item> TiledList<Query, Item>.queryAtOrNull(index: Int) =
    if (index in 0..lastIndex) queryAt(index) else null

operator fun <Query, Item> TiledList<Query, Item>.plus(
    other: TiledList<Query, Item>
): TiledList<Query, Item> = buildTiledList {
    this@plus.forEachIndexed { index, item ->
        add(this@plus.queryAt(index), item)
    }
    other.forEachIndexed { index, item ->
        add(other.queryAt(index), item)
    }
}

fun TiledList<*, *>.strictEquals(other: TiledList<*, *>): Boolean {
    if (other === this) return true
    if (size != other.size) return false
    for (i in 0..lastIndex) {
        if (this[i] != other[i]) return false
        if (queryAt(i) != other.queryAt(i)) return false
    }
    return true
}