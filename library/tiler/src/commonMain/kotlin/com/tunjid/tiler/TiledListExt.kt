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

inline fun <Query, T, R> TiledList<Query, T>.transform(
    transformation: MutableTiledList<Query, R>.(index: Int) -> Unit
): TiledList<Query, R> {
    val output = mutableTiledListOf<Query, R>()
    for (i in 0..lastIndex) transformation(output, i)
    return output
}

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

inline fun <Query, Item> TiledList<Query, Item>.filter(
    predicate: (Item) -> Boolean
): TiledList<Query, Item> =
    filterIndexed { _, item ->
        predicate(item)
    }

@Suppress("UNCHECKED_CAST")
inline fun <Query, reified Item> TiledList<Query, *>.filterIsInstance(
): TiledList<Query, Item> =
    filter { item ->
        item is Item
    } as TiledList<Query, Item>

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

inline fun <Query, T, R> TiledList<Query, T>.map(
    mapper: (T) -> R
): TiledList<Query, R> =
    mapIndexed { _, item ->
        mapper(item)
    }

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

inline fun <Query, T> TiledList<Query, T>.distinct(): TiledList<Query, T> =
    distinctBy { it }