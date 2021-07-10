package com.tunjid.tyler

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.transformWhile

/**
 * Effectively a function of [Tile.Input] to [Flow] [Tile.Output].
 * It keeps track of concurrent [Query]s allowing to pause, resume or discard of them at will.
 *
 * Each [Tile.Input] creates a new instance of the [TileFactory] with a new [flow] to collect.
 * This [Flow] is either:
 *
 * * A new [Flow] for a [Query] produced by [fetcher]
 * * A [Flow] to control an existing [FlowValve] for the [Query] but does not emit
 * * An empty [Flow] as nothing needs to be done
 */
internal data class TileFactory<Query, Item>(
    val flow: Flow<Tile.Output<Query, Item>> = emptyFlow(),
    val queryFlowValveMap: Map<Query, FlowValve<Query, Item>> = mapOf(),
    val fetcher: suspend (Query) -> Flow<Item>
) {

    @ExperimentalCoroutinesApi
    fun process(request: Tile.Input<Query, Item>): TileFactory<Query, Item> = when (request) {
        is Tile.Request.Evict -> {
            val existingValve = queryFlowValveMap[request.query]
            copy(
                flow = when (existingValve) {
                    null -> emptyFlow()
                    else -> existingValve.process(request)
                },
                // Eject query
                queryFlowValveMap = queryFlowValveMap.minus(request.query)
            )
        }
        is Tile.Request.Off -> {
            val existingValve = queryFlowValveMap[request.query]
            copy(
                flow = when (existingValve) {
                    null -> emptyFlow()
                    else -> existingValve.process(request)
                }
            )
        }
        is Tile.Request.On -> {
            val existingValve = queryFlowValveMap[request.query]
            val valve = existingValve ?: FlowValve(
                query = request.query,
                fetcher = fetcher
            )
            copy(
                // Don't accidentally recreate a flow for an existing query
                flow = when (existingValve) {
                    null -> valve.flow
                    else -> existingValve.process(request)
                },
                // Only add a valve if it didn't exist prior
                queryFlowValveMap = when (existingValve) {
                    null -> queryFlowValveMap.plus(request.query to valve)
                    else -> queryFlowValveMap
                }
            )
        }
        is Tile.Order -> copy(flow = flowOf(Tile.Output.Flattener(order = request)))
    }
}

/**
 * Allows for turning on and off a Flow
 */
internal class FlowValve<Query, Item>(
    query: Query,
    fetcher: suspend (Query) -> Flow<Item>
) {

    private val backingFlow = MutableSharedFlow<Tile.Request<Query, Item>>()

    val process: (Tile.Request<Query, Item>) -> Flow<Tile.Output<Query, Item>> = { request ->
        flow {
            backingFlow.subscriptionCount
                .filter { it > 0 }.take(1)
                .collect()
            println("Emitting: $request")
            backingFlow.emit(request)
            println("Emitted: $request")
        }
    }

    @ExperimentalCoroutinesApi
    val flow: Flow<Tile.Output<Query, Item>> = backingFlow
        .onSubscription { emit(Tile.Request.On(query = query)) }
        .onEach { println("In: $it") }
        .distinctUntilChanged()
        .flatMapLatest { toggle ->
            val toggledAt = System.currentTimeMillis()
            when (toggle) {
                // Eject the query downstream
                is Tile.Request.Evict -> flowOf(Tile.Output.Eviction<Query, Item>(query = query))
                // Stop collecting from the fetcher
                is Tile.Request.Off<Query, Item> -> emptyFlow()
                // Start collecting from the fetcher, keeping track of when the flow was turned on
                is Tile.Request.On<Query, Item> -> fetcher.invoke(query).map { item ->
                    Tile.Output.Data(
                        query = query,
                        tile = Tile(item = item, flowOnAt = toggledAt)
                    )
                }
            }
        }
        .onStart { emit(Tile.Output.Started(query = query)) }
        .onEach { println("Out: $it") }
        .transformWhile { toggle: Tile.Output<Query, Item> ->
            emit(toggle)
            // Terminate this flow entirely when the eviction signal is sent
            toggle !is Tile.Output.Eviction<Query, Item>
        }
}
