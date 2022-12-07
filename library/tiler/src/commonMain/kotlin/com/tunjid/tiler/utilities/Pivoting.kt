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

import com.tunjid.tiler.Tile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapConcat
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

fun <Query> PivotRequest<Query>.pivotAround(
    query: Query
): PivotResult<Query> = QuotaContext(query).let { loopContext ->
    PivotResult(
        on = mutableListOf(query).meetQuota(
            maxSize = onCount,
            context = loopContext,
            increment = nextQuery,
            decrement = previousQuery
        ),
        off = mutableListOf<Query>().meetQuota(
            maxSize = offCount,
            context = loopContext,
            increment = nextQuery,
            decrement = previousQuery
        ),
        currentQuery = query
    )
}

/**
 * Creates a [Flow] of [PivotResult] where the requests are pivoted around the most recent emission of [Query]
 */
fun <Query> Flow<Query>.pivotWith(pivotRequest: PivotRequest<Query>): Flow<PivotResult<Query>> =
    distinctUntilChanged()
        .scan(PivotResult<Query>()) { previousResult, currentQuery ->
            reducePivotResult(pivotRequest, currentQuery, previousResult)
        }
        .distinctUntilChanged()

/**
 * Creates a [Flow] of [PivotResult] where the requests are pivoted around the most recent emission of [Query] and [pivotRequestFlow]
 */
fun <Query> Flow<Query>.pivotWith(pivotRequestFlow: Flow<PivotRequest<Query>>): Flow<PivotResult<Query>> =
    distinctUntilChanged()
        .combine(
            pivotRequestFlow.distinctUntilChanged(),
            ::Pair
        )
        .scan(PivotResult<Query>()) { previousResult, (currentQuery, pivotRequest) ->
            reducePivotResult(pivotRequest, currentQuery, previousResult)
        }
        .distinctUntilChanged()

private fun <Query> reducePivotResult(
    request: PivotRequest<Query>,
    currentQuery: Query,
    previousResult: PivotResult<Query>
): PivotResult<Query> {
    val newRequest = request.pivotAround(currentQuery)
    val toRemove = (newRequest.on + newRequest.off).toSet()
    return newRequest.copy(
        // Evict everything not in the current active and inactive range
        evict = (previousResult.off + previousResult.on).filterNot(toRemove::contains)
    )
}

fun <Query, Item> Flow<PivotResult<Query>>.toTileInputs(): Flow<Tile.Request<Query, Item>> =
    flatMapConcat { managedRequest ->
        // There's a mild efficiency hit here to make this more readable.
        // A simple list concatenation would be faster.
        listOf<List<Tile.Request<Query, Item>>>(
            managedRequest.on.map { Tile.Request.On(it) },
            managedRequest.off.map { Tile.Request.Off(it) },
            managedRequest.evict.map { Tile.Request.Evict(it) },
        )
            .flatten()
            .asFlow()
    }

private fun <Query> MutableList<Query>.meetQuota(
    maxSize: Int,
    context: QuotaContext<Query>,
    increment: Query.() -> Query?,
    decrement: Query.() -> Query?,
): List<Query> {
    while (size < maxSize && (context.left != null || context.right != null)) {
        if (context.right != null) {
            context.right = context.right?.let(increment)
            context.right?.let(::add)
        }
        if (context.left != null && size < maxSize) {
            context.left = context.left?.let(decrement)
            context.left?.let(::add)
        }
    }
    return this.asReversed()
}


private class QuotaContext<Query>(start: Query) {
    var left: Query? = start
    var right: Query? = start
}