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

import com.tunjid.utilities.MutablePairedTiledList
import com.tunjid.utilities.FilterTransformedTiledList

/**
 * A [List] where each item is backed by the [Query] that fetched it.
 *
 * Note that [TiledList] instances should not be large. They should only contain enough
 * items to fill the device viewport a few items over to accommodate a user's scroll.
 * This is typically under 100 items.
 */
interface TiledList<Query, Item> : List<Item> {
    /**
     * Returns the query that fetched an [Item] at a specified index.
     */
    fun queryFor(index: Int): Query
}

/**
 * A [TiledList] with mutation facilities
 */
interface MutableTiledList<Query, Item> : TiledList<Query, Item> {
    /**
     * Returns the query that fetched an [Item]
     */
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
    object : TiledList<Query, Item>, List<Item> by emptyList() {
        override fun queryFor(index: Int): Query =
            throw IndexOutOfBoundsException("The TiledList is empty")
    }

/**
 * Returns a read-only [TiledList] instance
 */
fun <Query, Item> tiledList(
    vararg pairs: Pair<Query, Item>
): TiledList<Query, Item> =
    MutablePairedTiledList(*pairs)

/**
 * Returns a [MutableTiledList] instance
 */
fun <Query, Item> mutableTiledList(
    vararg pairs: Pair<Query, Item>
): MutableTiledList<Query, Item> =
    MutablePairedTiledList(*pairs)

/**
 * Builds a new read-only List by populating a MutableList using the given builderAction and returning a read-only list with the same elements.
 */
fun <Query, Item> buildTiledList(
    builderAction: MutableTiledList<Query, Item>.() -> Unit
): TiledList<Query, Item> = mutableTiledList<Query, Item>()
    .also(builderAction::invoke)

/**
 * filters a [TiledList] with the [filterTransformer] provided.
 * Every item in the returned list must be present in the original list ([this])
 */
fun <Query, Item> TiledList<Query, Item>.filterTransform(
    filterTransformer: List<Item>.() -> List<Item>
): TiledList<Query, Item> = FilterTransformedTiledList(
    originalList = this,
    transformedList = filterTransformer(this)
)
