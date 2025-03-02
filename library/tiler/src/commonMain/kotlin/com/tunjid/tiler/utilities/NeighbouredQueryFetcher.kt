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

import com.tunjid.tiler.QueryFetcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlin.math.max

/**
 * Defines the result of a fetch that provides [Token] instances to be fed back to other fetches
 * that neighbor this one.
 */
data class NeighboredFetchResult<Query: Any, Item, Token>(
    /**
     * A [Map] of [Query] to [Token] for queries adjacent to the query that fetched this
     * [NeighboredFetchResult]. They will be used to provide tokens for the adjacent queries.
     */
    val neighborQueriesToTokens: Map<Query, Token>,
    /**
     * The list of items for a particular query
     */
    val items: List<Item>,
)

/**
 * Returns a [QueryFetcher] for fetching queries that depend on the results of queries neighboring
 * it. Typically this is a paginated remote API that returns tokens in each
 * [NeighboredFetchResult], however it may also be used to enforce that queries are fetched by
 * proximity to a certain query.
 *
 *
 * @param maxTokens the maximum number of tokens to keep in memory. They are evicted on a LIFO basis.
 * @param seedQueryTokenMap the initial tokens present. The first [Query] should be contained in
 * this at a minimum.
 * @param fetcher fetches a [NeighboredFetchResult] for a given [Query] and [Token]
 */
fun <Query: Any, Item, Token> neighboredQueryFetcher(
    maxTokens: Int,
    seedQueryTokenMap: Map<Query, Token>,
    fetcher: suspend (Query, Token) -> Flow<NeighboredFetchResult<Query, Item, Token>>,
): QueryFetcher<Query, Item> = NeighbouredQueryFetcher(
    maxTokens = maxTokens,
    seedQueryTokenMap = seedQueryTokenMap,
    fetcher = fetcher,
)

internal class NeighbouredQueryFetcher<Query: Any, Item, Token>(
    private val maxTokens: Int,
    seedQueryTokenMap: Map<Query, Token>,
    val fetcher: suspend (Query, Token) -> Flow<NeighboredFetchResult<Query, Item, Token>>,
) : QueryFetcher<Query, Item> {

    internal val queriesToTokens = MutableStateFlow(LinkedHashMap(seedQueryTokenMap))

    init {
        if (seedQueryTokenMap.isEmpty()) throw IllegalArgumentException(
            "seed queries and tokens are empty, no items will ever be fetched."
        )
    }

    override suspend fun fetch(query: Query): Flow<List<Item>> =
        queriesToTokens.mapNotNull { it[query] }
            .distinctUntilChanged()
            .flatMapLatest { token ->
                fetcher(query, token).map { result ->
                    result.items.also {
                        if (result.neighborQueriesToTokens.isEmpty()) return@also
                        queriesToTokens.update { queue ->
                            val updatedLinkedMap = LinkedHashMap(queue)
                            updatedLinkedMap.putAll(result.neighborQueriesToTokens)
                            val diff = max(a = 0, b = updatedLinkedMap.size - maxTokens)
                            // Get the oldest items
                            val toEvict = updatedLinkedMap.keys.take(diff)
                            // Remove the oldest items
                            toEvict.forEach(updatedLinkedMap::remove)
                            updatedLinkedMap
                        }
                    }
                }
            }
}