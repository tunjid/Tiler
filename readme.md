# Tiler

## Introduction

A Tiler is a pure function with an API most similar to a reactive [Map] where:

* The keys are queries for data
* The values are dynamic sets of data returned over time as the result of the query key.

The output of a Tiler is either a snapshot of the [Map<Key, Value>] or a flattened [List<Value>]
making a [Tiler] either a:

* [(Flow<Tile.Input<Query, Item>>) -> Flow<List<Item>>]
or
* [(Flow<Tile.Request<Query, Item>>) -> Flow<Map<Query, Item>>]

## API surface

### Getting your data

Tilers are implemented as plain functions. Given a [Flow] of [Input], you can either choose to get your data as:

* A [Flow<Map<Key, Value><] with [tiledList]
* A flattened [Flow<List<Value>>] with [tiledMap]

In the simplest case given a [MutableStateFlow<Tile.Request<Int, List<Int>>] one can write:

```kotlin
Class NumberFetcher {
    private val requests = MutableStateFlow<Tile.Request<Int, List<Int>>(Request.On(0))

    private val tiledList = tiledList(
        flattener = Tile.Flattener.Sorted(comparator = Int::compareTo)
        fetcher = { page -> flowOf(page.until(page + 50).toList) }
    )

    val listItems: Flow<List<Int>> = tiledList.invoke(requests).map { it.flatten() }

    fun fetchPage(page: Int) {
        request.value = Request.On(page)
    }
}
```

The above will return a full list of every item requested sorted in ascending order of the query.
Note that in the above, the list will grow indefinitely as more tiles are requested unless queries are evicted.

### Managing requested data

Much like a classic [Map] that supports update and remove methods, a Tiler offers analogous operations in the form of [Inputs].

### [Input.Request]
* On: Analogous to [put for a [Map], this starts collecting from the backing [Flow] for the specified [query].
It is idempotent; multiple requests have no side effects

* Off: Stops collecting from the backing [Flow] for the specified [query].
The items previously fetched by this query are still kept in memory and will be in the [List] of items returned.
Requesting this is idempotent; multiple requests have no side effects.

* Evict: Analogous to [remove] for a [Map], this stops collecting from the backing [Flow] for the specified [query] and also evicts
the items previously fetched by the [query] from memory.
Requesting this is idempotent; multiple requests have no side effects.


### [Input.Flattener]

Defines the heuristic for flattening tiled items into a list.private

* Unspecified: Items will be returned in an undefined order. This is the default.

* Sorted: Sort items with a specified query [comparator].
A [limiter] can be used to select a subset of items requested instead of the whole set.

* PivotSorted: Sort items with the specified [comparator] but pivoted around the last query a
[Tile.Request.On] was sent for. This allows for showing items that have more priority
over others in the current context for example in a list being scrolled.

A [limiter] can be used to select a subset of items instead of the whole set.

* Custom: Flattens tiled items produced whichever way you desire

### Use cases

Tilers are useful in a scenario where the underlying data sets are dynamic.
This can range from common cases like endless scrolling of lists whose individual elements change often,
to niche cases like crosswords that alert users if their solving attempts are correct. Please
take a look at the sample app for concrete examples.

