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
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.transformWhile

/**
 * Processes [Tile.Input] requests concurrently to produce [TiledList] instances.
 * A mutex is used to synchronize access to the [Tiler] modified.
 */
fun <Query, Item> concurrentListTiler(
    order: Tile.Order<Query, Item>,
    limiter: Tile.Limiter<Query, Item>,
    fetcher: QueryFetcher<Query, Item>
): ListTiler<Query, Item> = ListTiler { requests ->
    requests
        .toOutput(fetcher)
        .flatMapMerge(
            concurrency = Int.MAX_VALUE,
            transform = { it }
        )
        .mapNotNull(
            Tiler(
                limiter = limiter,
                order = order
            )::process
        )
}

/**
 * Effectively a function of [Flow] [Tile.Input] to [Flow] [Tile.Output].
 * It keeps track of concurrent [Query]s allowing to pause, resume or discard of them at will.
 *
 * Each [Tile.Input] is mapped to an instance of an [QueryFlowValve] which manages the lifecycle
 * of the resultant [Flow].
 */
private fun <Query, Item> Flow<Tile.Input<Query, Item>>.toOutput(
    fetcher: QueryFetcher<Query, Item>
): Flow<Flow<Tile.Output<Query, Item>>> = flow flow@{
    val queriesToValves = mutableMapOf<Query, QueryFlowValve<Query, Item>>()

    this@toOutput.collect { input ->
        when (input) {
            is Tile.Order -> emit(
                flowOf(input)
            )

            is Tile.Request.Evict -> queriesToValves.remove(input.query)?.invoke(input)

            is Tile.Request.Off -> queriesToValves[input.query]?.invoke(input)

            is Tile.Request.On -> when (val existingValve = queriesToValves[input.query]) {
                null -> QueryFlowValve(fetcher = fetcher).let { valve ->
                    queriesToValves[input.query] = valve
                    // Emit the Flow from the valve, so it can be subscribed to
                    emit(valve.flow)
                    // Turn on the valve before processing other inputs
                    valve(input)
                }

                else -> existingValve(input)
            }

            is Tile.Limiter -> emit(
                flowOf(input)
            )
        }
    }
}

/**
 * Allows for turning on, off and terminating the [Flow] specified by a given fetcher
 */
private class QueryFlowValve<Query, Item>(
    fetcher: QueryFetcher<Query, Item>
) : suspend (Tile.Request<Query, Item>) -> Unit {

    private val mutableSharedFlow = MutableSharedFlow<Tile.Request<Query, Item>>()
    private val connectedSignal: suspend (Int) -> Boolean = { it > 0 }

    val flow: Flow<Tile.Output<Query, Item>> = mutableSharedFlow
        .distinctUntilChanged()
        .transformWhile(terminationSignal())
        .flatMapLatest(outputFlow(fetcher))

    override suspend fun invoke(request: Tile.Request<Query, Item>) {
        // Suspend till the downstream is connected
        mutableSharedFlow.subscriptionCount.first(connectedSignal)
        mutableSharedFlow.emit(request)
    }
}

private fun <Query, Item> outputFlow(
    fetcher: QueryFetcher<Query, Item>
): suspend (Tile.Request<Query, Item>) -> Flow<Tile.Output<Query, Item>> =
    { input ->
        when (input) {
            // Eject the query downstream
            is Tile.Request.Evict -> flowOf(input)
            // Stop collecting from the fetcher
            is Tile.Request.Off<Query, Item> -> emptyFlow()
            // Start collecting from the fetcher, keeping track of when the flow was turned on
            is Tile.Request.On<Query, Item> -> fetcher.invoke(input.query)
                .map<List<Item>, Tile.Output<Query, Item>> { items ->
                    Tile.Data(
                        query = input.query,
                        items = items
                    )
                }
        }
    }

private fun <Query, Item> terminationSignal():
        suspend FlowCollector<Tile.Request<Query, Item>>.(Tile.Request<Query, Item>) -> Boolean =
    { request ->
        emit(request)
        // Terminate this flow entirely when the eviction signal is sent
        request !is Tile.Request.Evict<Query, Item>
    }
