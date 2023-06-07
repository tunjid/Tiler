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

import kotlinx.coroutines.flow.AbstractFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flattenMerge
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.takeWhile

/**
 * Processes [Tile.Input] requests concurrently to produce [TiledList] instances.
 * A mutex is used to synchronize access to the [Tiler] modified.
 */
fun <Query, Item> concurrentListTiler(
    order: Tile.Order<Query, Item>,
    limiter: Tile.Limiter<Query, Item>,
    fetcher: QueryFetcher<Query, Item>
): ListTiler<Query, Item> = ListTiler { requests ->
    OutputFlow(requests, fetcher)
        .flattenMerge(concurrency = Int.MAX_VALUE)
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
private class OutputFlow<Query, Item>(
    private val flow: Flow<Tile.Input<Query, Item>>,
    private val fetcher: QueryFetcher<Query, Item>
) : AbstractFlow<Flow<Tile.Output<Query, Item>>>() {
    override suspend fun collectSafely(collector: FlowCollector<Flow<Tile.Output<Query, Item>>>) {
        val queriesToValves = mutableMapOf<Query, QueryFlowValve<Query, Item>>()
        flow.collect { input ->
            when (input) {
                is Tile.Order -> collector.emit(flowOf(input))

                is Tile.Request.Evict -> evict(
                    queriesToValves = queriesToValves,
                    query = input.query,
                    collector = collector
                )

                is Tile.Request.Off -> turnOff(
                    queriesToValves = queriesToValves,
                    query = input.query
                )

                is Tile.Request.On -> turnOn(
                    queriesToValves = queriesToValves,
                    query = input.query,
                    collector = collector
                )

                is Tile.Limiter -> collector.emit(
                    flowOf(input)
                )

                is PivotResult -> {
                    // Evict first because order will be invalid if queries that are not part
                    // of this pivot are ordered with its order
                    for (query in input.evict) evict(
                        queriesToValves = queriesToValves,
                        query = query,
                        collector = collector
                    )
                    for (query in input.off) turnOff(
                        queriesToValves = queriesToValves,
                        query = query
                    )
                    for (query in input.on) turnOn(
                        queriesToValves = queriesToValves,
                        query = query,
                        collector = collector
                    )
                    collector.emit(flowOf(input.order))
                }
            }
        }
    }

    private suspend fun evict(
        queriesToValves: MutableMap<Query, QueryFlowValve<Query, Item>>,
        query: Query,
        collector: FlowCollector<Flow<Tile.Output<Query, Item>>>
    ) {
        val valve = queriesToValves.remove(query) ?: return
        valve.invoke(terminate)
        collector.emit(flowOf(Tile.Request.Evict(query)))
    }

    private suspend fun turnOff(
        queriesToValves: MutableMap<Query, QueryFlowValve<Query, Item>>,
        query: Query
    ) {
        queriesToValves[query]?.invoke(off)
    }

    private suspend fun turnOn(
        queriesToValves: MutableMap<Query, QueryFlowValve<Query, Item>>,
        query: Query,
        collector: FlowCollector<Flow<Tile.Output<Query, Item>>>
    ) {
        when (val existingValve = queriesToValves[query]) {
            null -> {
                val valve = QueryFlowValve(query.toOutputFlow(fetcher))
                queriesToValves[query] = valve
                // Emit the Flow from the valve, so it can be subscribed to
                collector.emit(valve.outputFlow)
                // Turn on the valve before processing other inputs
                valve.invoke(valve)
            }

            else -> existingValve.invoke(existingValve)
        }
    }
}

/**
 * Allows for turning on, off and terminating the [Flow] specified by a given fetcher.
 * It is a
 * Function: To emit items down stream
 * Cold Flow: To reduce object allocations and to implement the distinctUntilChanged operator
 *
 */
private class QueryFlowValve<Query, Item>(
    downstreamFlow: Flow<Tile.Data<Query, Item>>
) : suspend (Flow<Tile.Data<Query, Item>>?) -> Unit,
    Flow<Tile.Data<Query, Item>> by downstreamFlow {

    private val mutableSharedFlow = MutableSharedFlow<Flow<Tile.Data<Query, Item>>?>()

    val outputFlow: Flow<Tile.Output<Query, Item>> = mutableSharedFlow
        .distinctUntilChanged()
        .takeWhile { it != null }
        .flatMapLatest { it ?: emptyFlow() }

    override suspend fun invoke(flow: Flow<Tile.Data<Query, Item>>?) {
        // Suspend till the downstream is connected
        if (mutableSharedFlow.subscriptionCount.value < 1) {
            mutableSharedFlow.subscriptionCount.first { it > 0 }
        }
        mutableSharedFlow.emit(flow)
    }
}

private fun <Query, Item> Query.toOutputFlow(
    fetcher: QueryFetcher<Query, Item>
) = fetcher(this).map {
    Tile.Data(
        query = this,
        items = it
    )
}

private val terminate = null
private val off = emptyFlow<Nothing>()