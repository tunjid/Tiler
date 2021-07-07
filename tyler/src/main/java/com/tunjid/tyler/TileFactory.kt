package com.tunjid.tyler

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

/**
 * Book keeper for [Tile] fetching
 */
internal data class TileFactory<Query, Item>(
    val flow: Flow<Tile.Output<Query, Item>> = emptyFlow(),
    val queryFlowValveMap: Map<Query, FlowValve<Query, Item>> = mapOf(),
    val fetcher: suspend (Query) -> Flow<Item>
) {

    @ExperimentalCoroutinesApi
    fun add(request: Tile.Input<Query, Item>): TileFactory<Query, Item> = when (request) {
        is Tile.Request.Evict -> {
            val existingValve = queryFlowValveMap[request.query]
            copy(
                flow = when (existingValve) {
                    null -> emptyFlow()
                    else -> flow { existingValve.toggle(request) }
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
                    else -> flow { existingValve.toggle(request) }
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
                // Don't accidentally duplicate calling a flow for the same query
                flow = when (existingValve) {
                    null -> valve.flow
                    else -> flow { valve.toggle(request) }
                },
                // Only add a valve if it didn't exist prior
                queryFlowValveMap = when (existingValve) {
                    null -> queryFlowValveMap.plus(request.query to valve)
                    else -> queryFlowValveMap
                }
            )
        }
        is Tile.Order -> copy(flow = flowOf(Tile.Output.Order(order = request)))
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

    val toggle: suspend (Tile.Request<Query, Item>) -> Unit = backingFlow::emit

    @ExperimentalCoroutinesApi
    val flow: Flow<Tile.Output<Query, Item>> = backingFlow
        .onSubscription { emit(Tile.Request.On(query = query)) }
        .distinctUntilChanged()
        .flatMapLatest { toggle ->
            val toggledAt = System.currentTimeMillis()
            when (toggle) {
                is Tile.Request.Evict -> flowOf(Tile.Output.Evict<Query, Item>(query = query))
                is Tile.Request.Off<Query, Item> -> emptyFlow()
                is Tile.Request.On<Query, Item> -> fetcher.invoke(query).map { item ->
                    Tile.Output.Data(
                        query = query,
                        tile = Tile(
                            query = query,
                            item = item,
                            flowOnAt = toggledAt
                        )
                    )
                }
            }
        }
        .transformWhile { toggle: Tile.Output<Query, Item> ->
            emit(toggle)
            toggle !is Tile.Output.Evict<Query, Item>
        }
}
