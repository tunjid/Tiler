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
 * A [TiledList] implementation that associates each [Item] with its [Query] using a
 * [SparseQueryArray]
 */
internal class SparseTiledList<Query, Item>(
    vararg pairs: Pair<Query, Item>
) : AbstractList<Item>(), MutableTiledList<Query, Item> {

    val queryRanges = SparseQueryArray<Query>(pairs.size)
    private val items: MutableList<Item> = mutableListOf()

    init {
        for (pair in pairs) add(
            query = pair.first,
            item = pair.second
        )
    }

    override fun queryAt(index: Int): Query {
        if (isEmpty() || index !in 0..lastIndex) throw IndexOutOfBoundsException()
        return queryRanges.find(index) ?: throw IndexOutOfBoundsException()
    }

    override val size: Int get() = items.size

    override fun get(index: Int): Item = items[index]

    override fun add(index: Int, query: Query, item: Item) {
        queryRanges.insertQuery(
            index = index,
            query = query,
            count = 1
        )
        this.items.add(index, item)
    }

    override fun add(query: Query, item: Item): Boolean {
        queryRanges.appendQuery(
            query = query,
            count = 1
        )
        return this.items.add(item)
    }

    override fun addAll(query: Query, items: Collection<Item>): Boolean {
        if (items.isEmpty()) return false
        queryRanges.appendQuery(
            query = query,
            count = items.size
        )
        this.items.addAll(items)
        return true
    }

    override fun addAll(index: Int, query: Query, items: Collection<Item>): Boolean {
        if (items.isEmpty()) return false
        queryRanges.insertQuery(
            index = index,
            query = query,
            count = items.size
        )
        this.items.addAll(index = index, elements = items)
        return true
    }

    override fun remove(index: Int): Item {
        queryRanges.deleteAt(index)
        return items.removeAt(index)
    }

    override fun equals(other: Any?): Boolean =
        if (other is TiledList<*, *>) strictEquals(other)
        else super.equals(other)

}