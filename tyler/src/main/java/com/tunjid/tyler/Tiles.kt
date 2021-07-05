package com.tunjid.tyler

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

data class Tile<Query, Item>(
    val flowOnAt: Long,
    val request: TileRequest<Query>,
    val items: List<Item>,
)

internal data class Tiles<Query, Item>(
    val newValve: FlowValve<Query, Item>? = null,
    val queryFlowValveMap: Map<Query, FlowValve<Query, Item>> = mapOf(),
    val fetcher: suspend (Query) -> Flow<List<Item>>
) {
    // Empty flows complete immediately, so they don't count towards te concurrent count
    fun flow(): Flow<Tile<Query, Item>> = newValve?.flow ?: emptyFlow()

    fun add(request: TileRequest<Query>): Tiles<Query, Item> = when (request) {
        is TileRequest.Eject -> {
            // Stop collecting from the Flow to free up resources
            queryFlowValveMap[request.query]?.toggle?.invoke(false)
            copy(
                newValve = FlowValve(
                    request = request,
                    // Emit an empty list to eject previous items
                    fetcher = { flowOf(listOf()) }
                )
            )
        }
        is TileRequest.Off -> {
            // Stop collecting from the lLow to free up resources
            queryFlowValveMap[request.query]?.toggle?.invoke(false)
            copy(newValve = null)
        }
        is TileRequest.On -> {
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
                newValve = newValve,
                // Book keeping for newly added flow
                queryFlowValveMap = when (newValve) {
                    null -> queryFlowValveMap
                    else -> queryFlowValveMap.plus(request.query to newValve)
                }
            )
        }
    }
}

/**
 * Allows for turning on and off a Flow
 */
internal class FlowValve<Query, Item>(
    request: TileRequest<Query>,
    fetcher: suspend (Query) -> Flow<List<Item>>
) {
    private val backingFlow = MutableStateFlow(value = true)

    val toggle: (Boolean) -> Unit = backingFlow::value::set

    val flow: Flow<Tile<Query, Item>> = backingFlow
        .flatMapLatest { isOn ->
            if (isOn) fetcher.invokeWithTimestamp(request)
            else emptyFlow()
        }
}

private suspend fun <Query, Item> (
suspend (Query) -> Flow<List<Item>>
).invokeWithTimestamp(
    request: TileRequest<Query>,
): Flow<Tile<Query, Item>> {
    val onAt = System.currentTimeMillis()
    return invoke(request.query).map { items ->
        Tile(
            request = request,
            items = items,
            flowOnAt = onAt
        )
    }
}
