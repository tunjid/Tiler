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

import kotlinx.coroutines.flow.Flow
import kotlin.jvm.JvmInline

/**
 * Produces [TiledList] from a [Flow] of a user's [Query]
 */
fun interface ListTiler<Query, Item> {
    fun produce(inputs: Flow<Tile.Input<Query, Item>>): Flow<TiledList<Query, Item>>
}

/**
 * Describes how to fetch data for a given [Query]
 */
fun interface QueryFetcher<Query, Item> {
    // This is a broken lint warning. This is supported since Kotlin 1.5
    // [see](https://youtrack.jetbrains.com/issue/KT-45836/Broken-support-of-suspend-function-in-SAM-interface-for-JVM-IR)
    @Suppress("FUN_INTERFACE_WITH_SUSPEND_FUNCTION")
    suspend fun fetch(query: Query): Flow<List<Item>>
}

operator fun <Query, Item> ListTiler<Query, Item>.invoke(
    inputs: Flow<Tile.Input<Query, Item>>
) = produce(inputs)

suspend operator fun <Query, Item> QueryFetcher<Query, Item>.invoke(
    query: Query
) = fetch(query)

/**
 * class holding metadata about a [Query] for an [Item], the [Item], and when the [Query] was sent
 */
class Tile<Query, Item : Any?> {

    /**
     * Defines input parameters for the [listTiler] function
     */
    sealed interface Input<Query, Item>

    /**
     * Summary of changes that can occur as a result of tiling
     */
    sealed interface Output<Query, Item>

    /**
     * [Tile.Input] type for managing data in the [listTiler] function
     */
    sealed interface Request<Query, Item> : Input<Query, Item> {
        val query: Query

        /**
         * Starts collecting from the backing [Flow] for the specified [query].
         * Requesting this is idempotent; multiple requests have no side effects.
         */
        @JvmInline
        value class On<Query, Item>(override val query: Query) : Request<Query, Item>

        /**
         * Stops collecting from the backing [Flow] for the specified [query].
         * The items previously fetched by this query are still kept in memory and will be
         * in the [List] of items returned
         * Requesting this is idempotent; multiple requests have no side effects.
         */
        @JvmInline
        value class Off<Query, Item>(override val query: Query) : Request<Query, Item>

        /**
         * Stops collecting from the backing [Flow] for the specified [query] and also evicts
         * the items previously fetched by the [query] from memory.
         * Requesting this is idempotent; multiple requests have no side effects.
         */
        @JvmInline
        value class Evict<Query, Item>(
            override val query: Query
        ) : Request<Query, Item>, Output<Query, Item>
    }

    /**
     * Describes the order of output items from the tiling functions
     */
    sealed class Order<Query, Item> : Input<Query, Item>, Output<Query, Item> {

        abstract val comparator: Comparator<Query>

        /**
         * Sort items with the specified query [comparator].
         */
        data class Sorted<Query, Item>(
            override val comparator: Comparator<Query>,
        ) : Order<Query, Item>(), Input<Query, Item>

        /**
         * Sort items with the specified [comparator] but pivoted around a specific query.
         * This allows for showing items that have more priority over others in the current context
         */
        data class PivotSorted<Query, Item>(
            val query: Query,
            override val comparator: Comparator<Query>,
        ) : Order<Query, Item>(), Input<Query, Item>

    }

    /**
     * Limits the output of the [listTiler] for [listTiler] functions.
     */
    data class Limiter<Query, Item>(
        /**
         * The maximum number of queries to be read from when returning a tiled list
         */
        val maxQueries: Int,
        /**
         * Optimizes retrieval speed for items fetched. Use only if your queries return a fixed number
         * of items each time, for example sql queries with a limit parameter. It is fine if the items returned
         * by the last query specified by [Tile.Order] returns less than the size specified.
         */
        val itemSizeHint: Int?,
    ) : Input<Query, Item>, Output<Query, Item>

    internal data class Data<Query, Item>(
        val items: List<Item>,
        val query: Query
    ) : Output<Query, Item>
}

/**
 * Convenience method to convert a [Flow] of [Tile.Input] to a [Flow] of a [TiledList] of [Item]s
 */
fun <Query, Item> Flow<Tile.Input<Query, Item>>.toTiledList(
    listTiler: ListTiler<Query, Item>
): Flow<TiledList<Query, Item>> = listTiler(inputs = this)

/**
 * Converts a [Flow] of [Query] into a [Flow] of [TiledList] [Item]
 */
fun <Query, Item> listTiler(
    order: Tile.Order<Query, Item>,
    limiter: Tile.Limiter<Query, Item> = Tile.Limiter(
        maxQueries = Int.MIN_VALUE,
        itemSizeHint = null,
    ),
    fetcher: QueryFetcher<Query, Item>
): ListTiler<Query, Item> =
    concurrentListTiler(
        order = order,
        limiter = limiter,
        fetcher = fetcher
    )
