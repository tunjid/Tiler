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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.scan

/**
 * A class defining the parameters for pivoted pagination
 */
data class PivotRequest<Query>(
    /** The amount of concurrent queries to collect from at any one time. */
    val onCount: Int,
    /** The amount of queries to keep in memory, but not collect from. */
    val offCount: Int = onCount,
    /** The [Comparator] used for sorting queries while pivoting. */
    val comparator: Comparator<Query>,
    /** A function to get the query consecutive to an existing query in ascending order. */
    val nextQuery: Query.() -> Query?,
    /** A function to get the query consecutive to an existing query in descending order. */
    val previousQuery: Query.() -> Query?,
)

/**
 * A summary of [Query] parameters that are pivoted around [currentQuery]
 */
data class PivotResult<Query>(
    val currentQuery: Query,
    /** The [Comparator] used for sorting queries while pivoting. */
    val comparator: Comparator<Query>,
    /** Pages actively being collected and loaded from. */
    val on: List<Query> = listOf(),
    /** Pages whose emissions are in memory, but are not being collected from. */
    val off: List<Query> = listOf(),
    /** Pages to remove from memory. */
    val evict: List<Query> = listOf(),
)

/**
 * Creates a [Flow] of [PivotResult] where the requests are pivoted around the most recent emission of [Query]
 */
fun <Query> Flow<Query>.pivotWith(
    pivotRequest: PivotRequest<Query>
): Flow<PivotResult<Query>> =
    distinctUntilChanged()
        .scan<Query, PivotResult<Query>?>(
            initial = null
        ) { previousResult, currentQuery ->
            reducePivotResult(
                request = pivotRequest,
                currentQuery = currentQuery,
                previousResult = previousResult
            )
        }
        .filterNotNull()
        .distinctUntilChanged()

/**
 * Creates a [Flow] of [PivotResult] where the requests are pivoted around the most recent emission of [Query] and [pivotRequestFlow]
 */
fun <Query> Flow<Query>.pivotWith(
    pivotRequestFlow: Flow<PivotRequest<Query>>
): Flow<PivotResult<Query>> =
    distinctUntilChanged()
        .combine(
            pivotRequestFlow.distinctUntilChanged(),
            ::Pair
        )
        .scan<Pair<Query, PivotRequest<Query>>, PivotResult<Query>?>(
            initial = null
        ) { previousResult, (currentQuery, pivotRequest) ->
            reducePivotResult(
                request = pivotRequest,
                currentQuery = currentQuery,
                previousResult = previousResult
            )
        }
        .filterNotNull()
        .distinctUntilChanged()

fun <Query, Item> Flow<PivotResult<Query>>.toTileInputs(): Flow<Tile.Input<Query, Item>> =
    flatMapConcat { managedRequest ->
        buildList<Tile.Input<Query, Item>> {
            managedRequest.on.forEach { add(Tile.Request.On(it)) }
            managedRequest.off.forEach { add(Tile.Request.Off(it)) }
            managedRequest.evict.forEach { add(Tile.Request.Evict(it)) }
            managedRequest.on.lastOrNull()?.let {
                add(Tile.Order.PivotSorted(query = it, comparator = managedRequest.comparator))
            }
        }
            .asFlow()
    }
internal fun <Query> PivotRequest<Query>.pivotAround(
    query: Query
): PivotResult<Query> = PivotContext(query).let { loopContext ->
    PivotResult(
        currentQuery = query,
        comparator = comparator,
        on = mutableListOf(query).meetSizeQuota(
            maxSize = onCount,
            context = loopContext,
            increment = nextQuery,
            decrement = previousQuery
        ),
        off = mutableListOf<Query>().meetSizeQuota(
            maxSize = offCount,
            context = loopContext,
            increment = nextQuery,
            decrement = previousQuery
        ),
    )
}

private fun <Query> reducePivotResult(
    currentQuery: Query,
    request: PivotRequest<Query>,
    previousResult: PivotResult<Query>?
): PivotResult<Query> {
    val newRequest = request.pivotAround(currentQuery)
    val toRemove = (newRequest.on + newRequest.off).toSet()
    val previousQueries = when (previousResult) {
        null -> emptyList()
        else -> previousResult.off + previousResult.on
    }
    return newRequest.copy(
        // Evict everything not in the current active and inactive range
        evict = when {
            previousQueries.isEmpty() -> previousQueries
            else -> previousQueries.filterNot(toRemove::contains)
        }
    )
}

/**
 * Meets the size quota defined by [maxSize] if possible using the provided [PivotContext]
 */
private fun <Query> MutableList<Query>.meetSizeQuota(
    maxSize: Int,
    context: PivotContext<Query>,
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
    // Reverse to the pivoted query is at the end of the list
    return this.asReversed()
}

private class PivotContext<Query>(start: Query) {
    var left: Query? = start
    var right: Query? = start
}