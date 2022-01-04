/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tunjid.tiler

import com.tunjid.utilities.epochMillis
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.transformWhile

@FlowPreview
@ExperimentalCoroutinesApi
internal fun <Query, Item, Output> tileFactory(
    limiter: Tile.Limiter<Query, Item, Output>,
    flattener: Tile.Flattener<Query, Item> = Tile.Flattener.Unspecified(),
    fetcher: suspend (Query) -> Flow<Item>
): (Flow<Tile.Input<Query, Item>>) -> Flow<Tiler<Query, Item, Output>> = { requests ->
    requests
        .groupByQuery(fetcher)
        .flatMapMerge(
            concurrency = Int.MAX_VALUE,
            transform = { it }
        )
        .scan(
            initial = Tiler(limiter =  limiter, flattener = flattener),
            operation = Tiler<Query, Item, Output>::add
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
@ExperimentalCoroutinesApi
private fun <Query, Item> Flow<Tile.Input<Query, Item>>.groupByQuery(
    fetcher: suspend (Query) -> Flow<Item>
): Flow<Flow<Tile.Output<Query, Item>>> = channelFlow channelFlow@{
    val queriesToValves = mutableMapOf<Query, InputValve<Query, Item>>()

    this@groupByQuery.collect { input ->
        when (input) {
            is Tile.Flattener -> flowOf(Tile.Output.FlattenChange(flattener = input))
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
                    this@channelFlow.channel.send(valve.flow)
                }
                else -> existingValve.push(input)
            }
            is Tile.Limiter.List -> flowOf(Tile.Output.LimiterChange(limiter = input))
            is Tile.Limiter.Map -> flowOf(Tile.Output.LimiterChange(limiter = input))
        }
    }
}

/**
 * Allows for turning on and off a Flow
 */
private class InputValve<Query, Item>(
    query: Query,
    fetcher: suspend (Query) -> Flow<Item>
) {

    private val mutableSharedFlow = MutableSharedFlow<Tile.Request<Query, Item>>()

    val push: suspend (Tile.Request<Query, Item>) -> Unit = { request ->
        // Suspend till the downstream is connected
        mutableSharedFlow.subscriptionCount.first { it > 0 }
        mutableSharedFlow.emit(request)
    }

    @ExperimentalCoroutinesApi
    val flow: Flow<Tile.Output<Query, Item>> = mutableSharedFlow
        .onSubscription { emit(Tile.Request.On(query = query)) }
        .distinctUntilChanged()
        .flatMapLatest { input ->
            val toggledAt = epochMillis()
            when (input) {
                // Eject the query downstream
                is Tile.Request.Evict -> flowOf(Tile.Output.Eviction<Query, Item>(query = input.query))
                // Stop collecting from the fetcher
                is Tile.Request.Off<Query, Item> -> emptyFlow()
                // Start collecting from the fetcher, keeping track of when the flow was turned on
                is Tile.Request.On<Query, Item> -> fetcher.invoke(input.query)
                    .map<Item, Tile.Output<Query, Item>> { item ->
                        Tile.Output.Data(
                            query = input.query,
                            tile = Tile(item = item, flowOnAt = toggledAt)
                        )
                    }
                    .onStart { emit(Tile.Output.TurnedOn(query = query)) }
            }
        }
        .transformWhile { toggle: Tile.Output<Query, Item> ->
            emit(toggle)
            // Terminate this flow entirely when the eviction signal is sent
            toggle !is Tile.Output.Eviction<Query, Item>
        }
}