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

package com.tunjid.tiler.utilities

import com.tunjid.tiler.MutableTiledList
import com.tunjid.tiler.TiledList
import com.tunjid.tiler.strictEquals

/**
 * A [TiledList] implementation that associates each [Item] with its [Query] with a [Pair]
 */
internal class MutablePairedTiledList<Query, Item>(
    vararg pairs: Pair<Query, Item>
) : AbstractList<Item>(), MutableTiledList<Query, Item> {

    private val queryItemPairs: MutableList<Pair<Query, Item>> = mutableListOf(*pairs)

    override fun queryAt(index: Int): Query = queryItemPairs[index].first

    override val size: Int get() = queryItemPairs.size
    override fun get(index: Int): Item = queryItemPairs[index].second

    override fun add(index: Int, query: Query, item: Item) =
        queryItemPairs.add(index, query to item)

    override fun add(query: Query, item: Item): Boolean =
        queryItemPairs.add(query to item)

    override fun addAll(query: Query, items: Collection<Item>): Boolean =
        queryItemPairs.addAll(elements = query.pairWith(items))

    override fun addAll(index: Int, query: Query, items: Collection<Item>): Boolean =
        queryItemPairs.addAll(index = index, elements = query.pairWith(items))

    override fun remove(index: Int): Item =
        queryItemPairs.removeAt(index).second

    override fun equals(other: Any?): Boolean =
        if (other is TiledList<*, *>) strictEquals(other)
        else super.equals(other)

}

private fun <Item, Query> Query.pairWith(
    items: Collection<Item>
) = items.map { this to it }