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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.transformWhile

internal fun <Query, Item> tilerFactory(
    limiter: Tile.Limiter<Query, Item>,
    order: Tile.Order<Query, Item> = Tile.Order.Unspecified(),
    fetcher: suspend (Query) -> Flow<List<Item>>
): (Flow<Tile.Input<Query, Item>>) -> Flow<Tiler<Query, Item>> = { requests ->
    requests
        .toOutput(fetcher)
        .flatMapMerge(
            concurrency = Int.MAX_VALUE,
            transform = { it }
        )
        .scan(
            initial = Tiler(
                metadata = Tile.Metadata(
                    limiter = limiter,
                    order = order
                )
            ),
            operation = Tiler<Query, Item>::add
        )
        .filter { it.shouldEmit }
}

/**
 * Effectively a function of [Flow] [Tile.Input] to [Flow] [Tile.Output].
 * It keeps track of concurrent [Query]s allowing to pause, resume or discard of them at will.
 *
 * Each [Tile.Input] is mapped to an instance of an [InputValve] which manages the lifecycle
 * of the resultant [Flow].
 */
private fun <Query, Item> Flow<Tile.Input<Query, Item>>.toOutput(
    fetcher: suspend (Query) -> Flow<List<Item>>
): Flow<Flow<Tile.Output<Query, Item>>> = flow channelFlow@{
    val queriesToValves = mutableMapOf<Query, InputValve<Query, Item>>()

    this@toOutput.collect { input ->
        when (input) {
            is Tile.Order -> emit(
                flowOf(Tile.Output.OrderChange(order = input))
            )

            is Tile.Request.Evict -> {
                queriesToValves[input.query]?.push?.invoke(input)
                queriesToValves.remove(input.query)
            }

            is Tile.Request.Off -> queriesToValves[input.query]?.push?.invoke(input)
            is Tile.Request.On -> when (val existingValve = queriesToValves[input.query]) {
                null -> {
                    val valve = InputValve(
                        query = input.query,
                        fetcher = fetcher,
                    )
                    queriesToValves[input.query] = valve
                    emit(valve.flow)
                }

                else -> existingValve.push(input)
            }

            is Tile.Limiter -> emit(
                flowOf(Tile.Output.LimiterChange(limiter = input))
            )
        }
    }
}

/**
 * Allows for turning on, off and terminating the [Flow] specified by a given fetcher
 */
private class InputValve<Query, Item>(
    query: Query,
    fetcher: suspend (Query) -> Flow<List<Item>>
) {

    private val mutableSharedFlow = MutableSharedFlow<Tile.Request<Query, Item>>()

    val push: suspend (Tile.Request<Query, Item>) -> Unit = { request ->
        // Suspend till the downstream is connected
        mutableSharedFlow.subscriptionCount.first { it > 0 }
        mutableSharedFlow.emit(request)
    }

    val flow: Flow<Tile.Output<Query, Item>> = mutableSharedFlow
        .onSubscription { emit(Tile.Request.On(query = query)) }
        .distinctUntilChanged()
        .flatMapLatest { input ->
            when (input) {
                // Eject the query downstream
                is Tile.Request.Evict -> flowOf(
                    Tile.Output.Eviction<Query, Item>(
                        query = input.query
                    )
                )
                // Stop collecting from the fetcher
                is Tile.Request.Off<Query, Item> -> emptyFlow()
                // Start collecting from the fetcher, keeping track of when the flow was turned on
                is Tile.Request.On<Query, Item> -> fetcher.invoke(input.query)
                    .map<List<Item>, Tile.Output<Query, Item>> { items ->
                        Tile.Output.Data(
                            query = input.query,
                            items = items
                        )
                    }
            }
        }
        .transformWhile { toggle: Tile.Output<Query, Item> ->
            emit(toggle)
            // Terminate this flow entirely when the eviction signal is sent
            toggle !is Tile.Output.Eviction<Query, Item>
        }
}
