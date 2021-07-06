package com.tunjid.tyler

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

internal sealed class Output<Query, Item> {
    data class Data<Query, Item>(
        val query: Query,
        val tile: TileData<Query, Item>
    ) : Output<Query, Item>()

    data class Order<Query, Item>(val itemOrder: Tile.ItemOrder<Query, Item>) : Output<Query, Item>()

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
            // Stop collecting from the Flow to free up resources
            queryFlowValveMap[request.query]?.toggle?.invoke(false)
            // Eject query
            copy(
                flow = flowOf(Output.Evict(query = request.query)),
                queryFlowValveMap = queryFlowValveMap.minus(request.query)
            )
        }
        is Tile.Request.Off -> {
            // Stop collecting from the Flow to free up resources
            queryFlowValveMap[request.query]?.toggle?.invoke(false)
            // No new valve was created, empty flows complete immediately,
            // so they don't count towards te concurrent count
            copy(flow = emptyFlow())
        }
        is Tile.Request.On -> {
            val existingValve = queryFlowValveMap[request.query]
            // Turn on the valve if it was previously shut off
            existingValve?.toggle?.invoke(true)

            // Only add a valve if one didn't exist prior
            val newValve = if (existingValve == null) FlowValve(
                request = request,
                fetcher = fetcher
            ) else null

            copy(
                // Don't accidentally duplicate calling a flow for the same query
                flow = newValve?.flow ?: emptyFlow(),
                // Book keeping for newly added flow
                queryFlowValveMap = when (newValve) {
                    null -> queryFlowValveMap
                    else -> queryFlowValveMap.plus(request.query to newValve)
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
    request: Tile.Request<Query,Item>,
    fetcher: suspend (Query) -> Flow<Item>
) {
    private val backingFlow = MutableStateFlow(value = true)

    val toggle: (Boolean) -> Unit = backingFlow::value::set

    @ExperimentalCoroutinesApi
    val flow: Flow<Output<Query, Item>> = backingFlow
        .flatMapLatest { isOn ->
            val toggledAt = System.currentTimeMillis()
            if (isOn) fetcher.invoke(request.query).map { item ->
                Output.Data(
                    query = request.query,
                    tile = TileData(
                        query = request.query,
                        item = item,
                        flowOnAt = toggledAt
                    )
                )
            }
            else emptyFlow()
        }
}
