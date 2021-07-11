package com.tunjid.tyler

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.transformWhile

/**
 * Effectively a function of [Flow] [Tile.Input] to [Flow] [Tile.Output].
 * It keeps track of concurrent [Query]s allowing to pause, resume or discard of them at will.
 *
 * Each [Tile.Input] is mapped to an instance of an [InputValve] which manages the lifecycle
 * of the resultant [Flow].
 */
@ExperimentalCoroutinesApi
internal fun <Query, Item> Flow<Tile.Input<Query, Item>>.groupByQuery(
    fetcher: suspend (Query) -> Flow<Item>
) = channelFlow<Flow<Tile.Output<Query, Item>>> channelFlow@{
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
        }
    }
}

/**
 * Allows for turning on and off a Flow
 */
internal class InputValve<Query, Item>(
    query: Query,
    fetcher: suspend (Query) -> Flow<Item>
) {

    private val mutableSharedFlow = MutableSharedFlow<Tile.Request<Query, Item>>()

    val push: suspend (Tile.Request<Query, Item>) -> Unit = { request ->
        // Suspend till the downstream is connected
        mutableSharedFlow.subscriptionCount
            .filter { it > 0 }
            .take(1)
            .collect()
        mutableSharedFlow.emit(request)
    }

    @ExperimentalCoroutinesApi
    val flow: Flow<Tile.Output<Query, Item>> = mutableSharedFlow
        .onSubscription { emit(Tile.Request.On(query = query)) }
        .distinctUntilChanged()
        .flatMapLatest { input ->
            val toggledAt = System.currentTimeMillis()
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