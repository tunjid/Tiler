package com.tunjid.tyler

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

internal sealed class Output<Query, Item> {
    data class Data<Query, Item>(
        val query: Query,
        val tile: TileData<Query, Item>
    ) : Output<Query, Item>()

    data class Order<Query, Item>(val itemOrder: Tile.ItemOrder<Query, Item>) :
        Output<Query, Item>()

    data class Evict<Query, Item>(val query: Query) : Output<Query, Item>()
}

/**
 * Book keeper for [TileData] fetching
 */
internal data class TileFactory<Query, Item>(
    val flow: Flow<Output<Query, Item>> = emptyFlow(),
    val queryFlowValveMap: Map<Query, FlowValve<Query, Item>> = mapOf(),
    val fetcher: suspend (Query) -> Flow<Item>
) {

    @ExperimentalCoroutinesApi
    fun add(request: Tile<Query, Item>): TileFactory<Query, Item> = when (request) {
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
        is Tile.ItemOrder -> copy(flow = flowOf(Output.Order(itemOrder = request)))
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
    val flow: Flow<Output<Query, Item>> = backingFlow
        .onSubscription { emit(Tile.Request.On(query = query)) }
        .distinctUntilChanged()
        .flatMapLatest { toggle ->
            val toggledAt = System.currentTimeMillis()
            when (toggle) {
                is Tile.Request.Evict -> flowOf(Output.Evict<Query, Item>(query = query))
                is Tile.Request.Off<Query, Item> -> emptyFlow()
                is Tile.Request.On<Query, Item> -> fetcher.invoke(query).map { item ->
                    Output.Data(
                        query = query,
                        tile = TileData(
                            query = query,
                            item = item,
                            flowOnAt = toggledAt
                        )
                    )
                }
            }
        }
        .transformWhile { toggle: Output<Query, Item> ->
            emit(toggle)
            toggle !is Output.Evict<Query, Item>
        }
}
