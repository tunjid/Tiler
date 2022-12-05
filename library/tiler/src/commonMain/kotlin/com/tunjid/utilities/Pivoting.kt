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
import kotlinx.coroutines.flow.combine
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
    val on = query.meetOnQuota(
        size = onCount,
        increment = nextQuery,
        decrement = previousQuery
    )

    // Keep queries on either end of the active pages in memory
    val off = meetOffQuota(
        size = offCount,
        leftStart = on.firstOrNull(),
        rightStart = on.lastOrNull(),
        increment = nextQuery,
        decrement = previousQuery
    )

    return PivotResult(
        on = on,
        off = off,
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
    return newRequest.copy(
        // Evict everything not in the current active and inactive range
        evict = (previousResult.on + previousResult.off) - (newRequest.on + newRequest.off).toSet()
    )
}

fun <Query, Item> Flow<PivotResult<Query>>.toRequests() =
    flatMapLatest { managedRequest ->
        // There's a mild efficiency hit here to make this more readable.
        // A simple list concatenation would be faster.
        sequenceOf<List<Tile.Request<Query, Item>>>(
            managedRequest.on.map { Tile.Request.On(it) },
            managedRequest.off.map { Tile.Request.Off(it) },
            managedRequest.evict.map { Tile.Request.Evict(it) },
        )
            .flatten()
            .asFlow()
    }

/**
 * Returns a [List] of [Query] that tries to meet the quota defined by [size] for on Requests
 */
private fun <Query> Query.meetOnQuota(
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
 * Returns a [List] of [Query] that tries to meet the quota defined by [size] for off requests
 */
private fun <Query> meetOffQuota(
    size: Int,
    leftStart: Query?,
    rightStart: Query?,
    increment: Query.() -> Query?,
    decrement: Query.() -> Query?,
): List<Query> {
    val result = mutableListOf<Query>()
    var left = leftStart
    var right = rightStart
    var hasRight = true
    var hasLeft = true

    var i = 0
    while (result.size < size && (hasRight || hasLeft)) {
        if (i % 2 != 0) {
            right = right?.let(increment)
            if (right != null) result.add(element = right)
            else hasRight = false
        } else {
            left = left?.let(decrement)
            if (left != null) result.add(index = 0, element = left)
            else hasLeft = false
        }
        i++
    }
    return result
}
