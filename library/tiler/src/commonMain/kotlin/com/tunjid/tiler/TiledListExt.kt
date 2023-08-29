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

/**
 * Returns all [Query] instances in [this] [TiledList] as a [List]
 */
inline fun <Query> TiledList<Query, *>.queries(): List<Query> =
    (0 until tileCount).map(::queryAtTile)

/**
 * Returns all [Tile] instances in [this] [TiledList] as a [List].
 * Note: Each [Tile] returned will be boxed. If autoboxing is not desired, iterate
 * between 0 and [TiledList.tileCount] instead.
 */
inline fun TiledList<*, *>.tiles(): List<Tile> =
    (0 until tileCount).map(::tileAt)

inline fun <Query, T, R> TiledList<Query, T>.transform(
    transformation: MutableTiledList<Query, R>.(index: Int) -> Unit
): TiledList<Query, R> {
    val output = mutableTiledListOf<Query, R>()
    for (i in 0..lastIndex) transformation(output, i)
    return output
}

/**
 * Equivalent to [List.filterIndexed] for [TiledList]
 */
inline fun <Query, Item> TiledList<Query, Item>.filterIndexed(
    predicate: (Int, Item) -> Boolean
): TiledList<Query, Item> =
    transform { index ->
        val item = this@filterIndexed[index]
        if (predicate(index, item)) add(
            query = this@filterIndexed.queryAt(index),
            item = item
        )
    }

/**
 * Equivalent to [List.filter] for [TiledList]
 */
inline fun <Query, Item> TiledList<Query, Item>.filter(
    predicate: (Item) -> Boolean
): TiledList<Query, Item> =
    filterIndexed { _, item ->
        predicate(item)
    }

/**
 * Equivalent to [List.filterIsInstance] for [TiledList]
 */
@Suppress("UNCHECKED_CAST")
inline fun <Query, reified Item> TiledList<Query, *>.filterIsInstance(
): TiledList<Query, Item> =
    filter { item ->
        item is Item
    } as TiledList<Query, Item>

/**
 * Equivalent to [List.mapIndexed] for [TiledList]
 */
inline fun <Query, T, R> TiledList<Query, T>.mapIndexed(
    mapper: (Int, T) -> R
): TiledList<Query, R> =
    transform { index ->
        val item = this@mapIndexed[index]
        add(
            query = this@mapIndexed.queryAt(index = index),
            item = mapper(index, item)
        )
    }

/**
 * Equivalent to [List.map] for [TiledList]
 */
inline fun <Query, T, R> TiledList<Query, T>.map(
    mapper: (T) -> R
): TiledList<Query, R> =
    mapIndexed { _, item ->
        mapper(item)
    }

/**
 * Equivalent to [List.distinctBy] for [TiledList]
 */
inline fun <Query, T, K> TiledList<Query, T>.distinctBy(
    selector: (T) -> K
): TiledList<Query, T> {
    val set = mutableSetOf<K>()
    return transform { index ->
        val item = this@distinctBy[index]
        val key = selector(item)
        if (!set.contains(key)) {
            set.add(key)
            add(
                query = this@distinctBy.queryAt(index = index),
                item = item
            )
        }
    }
}

/**
 * Equivalent to [List.distinct] for [TiledList]
 */
inline fun <Query, T> TiledList<Query, T>.distinct(): TiledList<Query, T> =
    distinctBy { it }

/**
 * Equivalent to [List.groupBy] for [TiledList]
 */
inline fun <Query, T, K> TiledList<Query, T>.groupBy(
    keySelector: (T) -> K
): Map<K, TiledList<Query, T>> {
    val groupedItems = linkedMapOf<K, MutableTiledList<Query, T>>()
    forEachIndexed { index, item ->
        val mutableTiledList = groupedItems.getOrPut(
            key = keySelector(item),
            defaultValue = ::mutableTiledListOf
        )
        mutableTiledList.add(
            query = queryAt(index),
            item = item
        )
    }
    return groupedItems
}
