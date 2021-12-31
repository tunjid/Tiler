# Tiler

[![JVM Tests](https://github.com/tunjid/Tiler/actions/workflows/tests.yml/badge.svg)](https://github.com/tunjid/Tiler/actions/workflows/tests.yml)
![Tiler](https://img.shields.io/maven-central/v/com.tunjid.tiler/tiler?label=tiler)

Please note, this is not an official Google repository. It is a Kotlin multiplatform experiment
that makes no guarantees about API stability or long term support. None of the works presented here
are production tested, and should not be taken as anything more than its face value.

## Introduction

A Tiler is a pure function with an API most similar to a reactive `Map` where:

* The keys are queries for data
* The values are dynamic sets of data returned over time as the result of the query key.

The output of a Tiler is either a snapshot of the `Map<Key, Value>` or a flattened `List<Value>`
making a `Tiler` either a:

* `(Flow<Tile.Input<Query, Item>>) -> Flow<List<Item>>`
or
* `(Flow<Tile.Request<Query, Item>>) -> Flow<Map<Query, Item>>`

## API surface

### Getting your data

Tilers are implemented as plain functions. Given a `Flow` of `Input`, you can either choose to get your data as:

* A flattened `Flow<List<Item>>` with `tiledList`
* A `Flow<Map<Query, Item>>` with `tiledMap`

In the simplest case given a `MutableStateFlow<Tile.Request<Int, List<Int>>` one can write:

```kotlin
class NumberFetcher {
    private val requests =
        MutableStateFlow<Tile.Request<Int, List<Int>>>(Tile.Request.On(query = 0))

    private val tiledList: (Flow<Tile.Input<Int, List<Int>>>) -> Flow<List<List<Int>>> = tiledList(
        flattener = Tile.Flattener.Sorted(comparator = Int::compareTo),
        fetcher = { page ->
            val start = page * 50
            val numbers = start.until(start + 50)
            flowOf(numbers.toList())
        }
    )

    val listItems: Flow<List<Int>> = tiledList.invoke(requests).map { it.flatten() }

    fun fetchPage(page: Int) {
        requests.value = Tile.Request.On(page)
    }
}
```

The above will return a full list of every item requested sorted in ascending order of the pages.

Note that in the above, the list will grow indefinitely as more tiles are requested unless queries are evicted.
This may be fine for small lists, but as the list size grows, some items may need to be evicted and
only a small subset of items need to be presented to the UI. This sort of behavior can be achieved using
the `Evict` `Request`, and the `PivotSorted` `Flattener` covered below.

### Managing requested data

Much like a classic `Map` that supports update and remove methods, a Tiler offers analogous operations in the form of `Inputs`.

### `Input.Request`
* On: Analogous to `put` for a `Map`, this starts collecting from the backing `Flow` for the specified `query`.
It is idempotent; multiple requests have no side effects for loading, i.e the same `Flow` will not be collect twice.

* Off: Stops collecting from the backing `Flow` for the specified `query`.
The items previously fetched by this query are still kept in memory and will be in the `List` of items returned.
Requesting this is idempotent; multiple requests have no side effects.

* Evict: Analogous to `remove` for a `Map`, this stops collecting from the backing `Flow` for the specified `query` and also evicts
the items previously fetched by the `query` from memory.
Requesting this is idempotent; multiple requests have no side effects.

### `Input.Flattener`

Defines the heuristic for flattening tiled items into a list.

* Unspecified: Items will be returned in an undefined order. This is the default.

* Sorted: Sort items with a specified query `comparator`.
A `limiter` can be used to select a subset of items requested instead of the whole set.

* PivotSorted: Sort items with the specified `comparator` but pivoted around the last query a
`Tile.Request.On` was sent for. This allows for showing items that have more priority
over others in the current context for example in a list being scrolled. In other words assume tiles
have been fetched for queries 1 - 10 but a user can see pages 5 and 6. The UI need only to be aware
of pages 4, 5, 6, and 7. This allows for a rolling window of queries based on a user's scroll position.
A `limiter` can be used to select a subset of items instead of the whole set as defined by the
rolling window defined above.

* Custom: Flattens tiled items produced whichever way you desire.

### More complex uses

To deal with the issue of the tiled data set becoming arbitrarily large, one create a pipeline
where requests backing only the items visible on the screen active, and automatically ejecting
those further than a certain page count away.

```kotlin
class ManagedNumberFetcher {
    private val requests =
        MutableStateFlow<Tile.Request.On<Int, List<Int>>>(Tile.Request.On(query = 0))

    val managedRequests = requests
        .map { (page) -> listOf(page - 1, page, page + 1).filter { it >= 0 } }
        .scan(listOf<Int>() to listOf<Int>()) { oldRequestsToNewRequests, newRequests ->
            // Keep track of what was last requested
            oldRequestsToNewRequests.copy(
                first = oldRequestsToNewRequests.second,
                second = newRequests
            )
        }
        .flatMapLatest { (oldRequests, newRequests) ->
            // Evict all items 10 pages behind the smallest page in the new request.
            // Their backing flows will stop being collected, and their existing values will be
            // evicted from memory
            val toEvict: List<Tile.Request.Evict<Int, List<Int>>> = (newRequests.minOrNull()
                ?.minus(10)
                ?.downTo(0)
                ?.take(10)
                ?: listOf())
                .map { Tile.Request.Evict(it) }

            // Turn off the flows for all old requests that are not in the new request batch
            // The existing emitted values will be kept in memory, but their backing flows
            // will stop being collected
            val toTurnOff: List<Tile.Request.Off<Int, List<Int>>> = oldRequests
                .filterNot(newRequests::contains)
                .map { Tile.Request.Off(it) }

            val toTurnOn: List<Tile.Request.On<Int, List<Int>>> = newRequests
                .map { Tile.Request.On(it) }

            (toEvict + toTurnOff + toTurnOn).asFlow()
        }

    private val tiledList: (Flow<Tile.Input<Int, List<Int>>>) -> Flow<List<List<Int>>> = tiledList(
        flattener = Tile.Flattener.Sorted(comparator = Int::compareTo),
        fetcher = { page ->
            val start = page * 50
            val numbers = start.until(start + 50)
            flowOf(numbers.toList())
        }
    )

    val listItems: Flow<List<Int>> = tiledList.invoke(managedRequests).map { it.flatten() }

    fun fetchPage(page: Int) {
        requests.value = Tile.Request.On(page)
    }
}
```

In the above, a moving window of pages are kept to manage requests. If for example page 19 is
requested, `Flows` for pages 18, 19, and 20 are kept active and any emissions for pages 0 - 9 are
evicted from memory. If emissions exist for pages 10 - 17, they will be kept in memory but their
`Flows` will be inactive.

### Use cases

Tilers are useful in a scenario where the underlying data sets are dynamic.
This can range from common cases like endless scrolling of lists whose individual elements change often,
to niche cases like crosswords that alert users if their solving attempts are correct. Please
take a look at the sample app for concrete examples.

