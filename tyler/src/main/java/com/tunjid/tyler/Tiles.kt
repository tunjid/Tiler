package com.tunjid.tyler

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * class holding meta data about a [Query] for an [Item], the [Item], and when the [Query] was sent
 */
internal data class Tile<Query, Item : Any?>(
    val flowOnAt: Long,
    val query: Query,
    val item: Item,
)

internal sealed class Result<Query, Item> {
    data class Data<Query, Item>(
        val query: Query,
        val tile: Tile<Query, Item>
    ) : Result<Query, Item>()

    data class Order<Query, Item>(val get: TileRequest.Get<Query, Item>) : Result<Query, Item>()

    data class None<Query, Item>(val query: Query) : Result<Query, Item>()
}

/**
 * Book keeper for [Tile] fetching
 */
internal data class Tiles<Query, Item>(
    val flow: Flow<Result<Query, Item>> = emptyFlow(),
    val queryFlowValveMap: Map<Query, FlowValve<Query, Item>> = mapOf(),
    val fetcher: suspend (Query) -> Flow<Item>
) {

    @ExperimentalCoroutinesApi
    fun add(request: TileRequest<Query, Item>): Tiles<Query, Item> = when (request) {
        is TileRequest.Request.Eject -> {
            // Stop collecting from the Flow to free up resources
            queryFlowValveMap[request.query]?.toggle?.invoke(false)
            // Eject query
            copy(
                flow = flowOf(Result.None(query = request.query)),
                queryFlowValveMap = queryFlowValveMap.minus(request.query)
            )
        }
        is TileRequest.Request.Off -> {
            // Stop collecting from the Flow to free up resources
            queryFlowValveMap[request.query]?.toggle?.invoke(false)
            // No new valve was created, empty flows complete immediately,
            // so they don't count towards te concurrent count
            copy(flow = emptyFlow())
        }
        is TileRequest.Request.On -> {
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
        is TileRequest.Get.Pivoted -> copy(flow = flowOf(Result.Order(get = request)))
        is TileRequest.Get.StrictOrder -> copy(flow = flowOf(Result.Order(get = request)))
        is TileRequest.Get.InsertionOrder -> copy(flow = flowOf(Result.Order(get = request)))
    }
}

/**
 * Allows for turning on and off a Flow
 */
internal class FlowValve<Query, Item>(
    request: TileRequest.Request<Query,Item>,
    fetcher: suspend (Query) -> Flow<Item>
) {
    private val backingFlow = MutableStateFlow(value = true)

    val toggle: (Boolean) -> Unit = backingFlow::value::set

    @ExperimentalCoroutinesApi
    val flow: Flow<Result<Query, Item>> = backingFlow
        .flatMapLatest { isOn ->
            val toggledAt = System.currentTimeMillis()
            if (isOn) fetcher.invoke(request.query).map { item ->
                Result.Data(
                    query = request.query,
                    tile = Tile(
                        query = request.query,
                        item = item,
                        flowOnAt = toggledAt
                    )
                )
            }
            else emptyFlow()
        }
}
