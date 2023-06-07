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

                is Tile.Request.Evict -> queriesToValves.remove(input.query)?.let { valve ->
                    valve.invoke(terminate)
                    collector.emit(flowOf(input))
                }

                is Tile.Request.Off -> queriesToValves[input.query]?.invoke(off)

                is Tile.Request.On -> when (val existingValve = queriesToValves[input.query]) {
                    null -> QueryFlowValve(input.query.toOutputFlow(fetcher)).let { valve ->
                        queriesToValves[input.query] = valve
                        // Emit the Flow from the valve, so it can be subscribed to
                        collector.emit(valve.outputFlow)
                        // Turn on the valve before processing other inputs
                        valve.invoke(valve)
                    }

                    else -> existingValve.invoke(existingValve)
                }

                is Tile.Limiter -> collector.emit(
                    flowOf(input)
                )
            }
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
        mutableSharedFlow.subscriptionCount.first { it > 0 }
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