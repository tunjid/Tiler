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

    override fun queryAt(index: Int): Query {
        if (isEmpty() || index !in 0..lastIndex) throw IndexOutOfBoundsException()
        return queryRanges.find(index) ?: throw IndexOutOfBoundsException()
    }

    override val size: Int get() = items.size

    override fun get(index: Int): Item = items[index]

    override fun add(index: Int, query: Query, item: Item) {
        insertQuery(
            index = index,
            query = query,
            size = 1
        )
        this.items.add(index, item)
    }

    override fun add(query: Query, item: Item): Boolean {
        appendQuery(
            query = query,
            size = 1
        )
        return this.items.add(item)
    }

    override fun addAll(query: Query, items: Collection<Item>): Boolean {
        if (items.isEmpty()) return false
        appendQuery(
            query = query,
            size = items.size
        )
        this.items.addAll(items)
        return true
    }

    override fun addAll(index: Int, query: Query, items: Collection<Item>): Boolean {
        if (items.isEmpty()) return false
        insertQuery(
            index = index,
            query = query,
            size = items.size
        )
        this.items.addAll(index = index, elements = items)
        return true
    }

    override fun remove(index: Int): Item {
        queryRanges.deleteAt(index)
        return items.removeAt(index)
    }

    private fun appendQuery(query: Query, size: Int) {
        val lastIndex = items.lastIndex
        // If the queries are consecutive, simply replace the last one
        val replaced = isNotEmpty() && queryRanges.replaceIfMatches(
            index = lastIndex,
            query = query
        ) { oldRange ->
            QueryRange(
                start = oldRange.start,
                end = oldRange.end + size
            )
        }
        if (replaced) return

        // Append at the end
        queryRanges[
            QueryRange(
                start = lastIndex + 1,
                end = lastIndex + size + 1
            )
        ] = query

    }

    private fun insertQuery(index: Int, query: Query, size: Int) {
        val replaced = queryRanges.replaceIfMatches(
            index = index,
            query = query
        ) { oldRange ->
            QueryRange(
                start = oldRange.start,
                end = oldRange.end + size
            )
        }
        if (replaced) return

        queryRanges.insert(
            index = index,
            newRange = QueryRange(
                start = index,
                end = index + size
            ),
            query = query
        )
    }

    override fun equals(other: Any?): Boolean =
        if (other is TiledList<*, *>) strictEquals(other)
        else super.equals(other)

}
