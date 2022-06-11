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

package com.tunjid.utilities

import com.tunjid.tiler.Tile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.scan

/**
 * A class defining the parameters for pivoted pagination
 */
data class PivotRequest<Query>(
    /** The amount of concurrent queries to collect from at any one time. */
    val onCount: Int,
    /** The amount of queries to keep in memory, but not collect from. */
    val offCount: Int = onCount,
    /** A function to get the query consecutive to an existing query in ascending order. */
    val nextQuery: Query.() -> Query?,
    /** A function to get the query consecutive to an existing query in descending order. */
    val previousQuery: Query.() -> Query?,
)

/**
 * A summary of [Query] parameters that are pivoted around [currentQuery]
 */
data class PivotResult<Query>(
    val currentQuery: Query? = null,
    /** Pages actively being collected and loaded from. */
    val on: List<Query> = listOf(),
    /** Pages whose emissions are in memory, but are not being collected from. */
    val off: List<Query> = listOf(),
    /** Pages to remove from memory. */
    val evict: List<Query> = listOf(),
)

fun <Query> PivotRequest<Query>.pivotAround(query: Query): PivotResult<Query> {
    val on = query.meetQuota(
        size = onCount,
        increment = nextQuery,
        decrement = previousQuery
    )

    val offLimit = offCount / 2
    // Keep queries on either end of the active pages in memory
    val off = previousQuery.toSequence(on.firstOrNull()?.let(previousQuery)).take(offLimit)
        .plus(nextQuery.toSequence(on.lastOrNull()?.let(nextQuery)).take(offLimit))
        .toList()

    return PivotResult(
        on = on,
        off = off,
        currentQuery = query
    )
}

/**
 * Creates a [Flow] of [PivotResult] where the requests are pivoted around the most recent emission of [Query]
 */
fun <Query> Flow<Query>.pivotWith(request: PivotRequest<Query>): Flow<PivotResult<Query>> =
    distinctUntilChanged()
        .scan(PivotResult<Query>()) { previousResult, currentQuery ->
            val newRequest = request.pivotAround(currentQuery)
            newRequest.copy(
                // Evict everything not in the current active and inactive range
                evict = (previousResult.on + previousResult.off) - (newRequest.on + newRequest.off).toSet()
            )
        }
        .distinctUntilChanged()

fun <Query, Item> Flow<PivotResult<Query>>.toRequests() =
    flatMapLatest { managedRequest ->
        sequenceOf<List<Tile.Request<Query, Item>>>(
            managedRequest.on.map { Tile.Request.On(it) },
            managedRequest.off.map { Tile.Request.Off(it) },
            managedRequest.evict.map { Tile.Request.Evict(it) },
        )
            .flatten()
            .asFlow()
    }

/**
 * Returns a [List] of [Query] that tries to meet the quota defined by [size]
 */
private fun <Query> Query.meetQuota(
    size: Int,
    increment: Query.() -> Query?,
    decrement: Query.() -> Query?,
): List<Query> {
    val result = mutableListOf(this)
    var hasRight = true
    var hasLeft = true

    var i = 0
    while (result.size < size && (hasRight || hasLeft)) {
        if (i % 2 != 0) increment(result.last()).let {
            if (it != null) result.add(element = it)
            else hasRight = false
        }
        else decrement(result.first()).let {
            if (it != null) result.add(index = 0, element = it)
            else hasLeft = false
        }
        i++
    }
    return result
}

/**
 * Generates a sequence of [Query] from [this]
 */
private inline fun <Query> (Query.() -> Query?).toSequence(seed: Query?) =
    generateSequence(
        seed = seed,
        nextFunction = this
    )
