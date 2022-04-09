# Tiler

[![JVM Tests](https://github.com/tunjid/Tiler/actions/workflows/tests.yml/badge.svg)](https://github.com/tunjid/Tiler/actions/workflows/tests.yml)
![Tiler](https://img.shields.io/maven-central/v/com.tunjid.tiler/tiler?label=tiler)

![badge][badge-ios]
![badge][badge-js]
![badge][badge-jvm]
![badge][badge-linux]
![badge][badge-windows]
![badge][badge-mac]
![badge][badge-tvos]
![badge][badge-watchos]

Please note, this is not an official Google repository. It is a Kotlin multiplatform experiment that makes no guarantees
about API stability or long term support. None of the works presented here are production tested, and should not be
taken as anything more than its face value.

## Introduction

Tiling is a kotlin multiplatform experiment for loading chunks of structured data from reactive sources.

Tiling is achieved with a Tiler; a pure function that has the ability to adapt a generic API of the form:

```kotlin
fun <T> items(query: Query): Flow<T>
```

into a paginated API.

It does this by exposing an API most similar to a reactive `Map` where:

* The keys are queries for data
* The values are dynamic sets of data returned over time as the result of the query key.

The output of a Tiler is either a snapshot of the `Map<Key, Value>` or a flattened `List<Value>`
making a `Tiler` either a:

* `(Flow<Tile.Input.List<Query, Item>>) -> Flow<List<Item>>`
  or
* `(Flow<Tile.Input.Map<Query, Item>>) -> Flow<Map<Query, Item>>`

## Demo

The demo app is cheekily implemented as a grid of tiles with dynamic colors:

![Demo image](https://github.com/tunjid/tiler/blob/develop/misc/demo.gif)


### Get it

`Tiler` is available on mavenCentral with the latest version indicated by the badge at the top of this readme file.

`implementation com.tunjid.tiler:tiler:version`

## API surface

### Getting your data

Tilers are implemented as plain functions. Given a `Flow` of `Input`, you can either choose to get your data as:

* A `Flow<List<Item>>` with `tiledList`
* A `Flow<Map<Query, Item>>` with `tiledMap`

In the simplest case given a `MutableStateFlow<Tile.Request<Int, List<Int>>` one can write:

```kotlin
class NumberFetcher {
    private val requests =
        MutableStateFlow<Tile.Input.List<Int, List<Int>>>(Tile.Request.On(query = 0))

    private val tiledList: (Flow<Tile.Input<Int, List<Int>>>) -> Flow<List<List<Int>>> = tiledList(
        order = Tile.Order.Sorted(comparator = Int::compareTo),
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

Note that in the above, the list will grow indefinitely as more tiles are requested unless queries are evicted. This may
be fine for small lists, but as the list size grows, some items may need to be evicted and only a small subset of items
need to be presented to the UI. This sort of behavior can be achieved using the `Evict` `Request`, and
the `PivotSorted` `Order` covered below.

### Managing requested data

Much like a classic `Map` that supports update and remove methods, a Tiler offers analogous operations in the form
of `Inputs`.

### `Input.Request`

* On: Analogous to `put` for a `Map`, this starts collecting from the backing `Flow` for the specified `query`. It is
  idempotent; multiple requests have no side effects for loading, i.e the same `Flow` will not be collect twice.

* Off: Stops collecting from the backing `Flow` for the specified `query`. The items previously fetched by this query
  are still kept in memory and will be in the `List` of items returned. Requesting this is idempotent; multiple requests
  have no side effects.

* Evict: Analogous to `remove` for a `Map`, this stops collecting from the backing `Flow` for the specified `query` and
  also evicts the items previously fetched by the `query` from memory. Requesting this is idempotent; multiple requests
  have no side effects.

### `Input.Order`

Defines the heuristic for selecting tiled items into the output container.

* Unspecified: Items will be returned in an undefined order. This is the default.

* Sorted: Sort items with a specified query `comparator`.

* PivotSorted: Sort items with the specified `comparator` but pivoted around the last query a
  `Tile.Request.On` was sent for. This allows for showing items that have more priority over others in the current
  context for example in a list being scrolled. In other words assume tiles have been fetched for queries 1 - 10 but a
  user can see pages 5 and 6. The UI need only to be aware of pages 4, 5, 6, and 7. This allows for a rolling window of
  queries based on a user's scroll position.

* Custom: Flattens tiled items produced whichever way you desire.

### `Input.Limiter`

Can be used to select a subset of items tiled instead of the whole set. For example, assuming 1000 items have been
fetched. There's no need to send a 1000 items to the UI for diffing/display when the UI can only show about 30 at once.
The `Limiter` allows for selecting an arbitrary amount of items as the situation demands.

### More complex uses

To deal with the issue of the tiled data set becoming arbitrarily large, one can create a pipeline where the
pages loaded are defined as a function of the page the user is currently at:

```kotlin
import com.tunjid.tiler.Tile
import com.tunjid.tiler.tiledList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan

data class LoadMetadata(
  val pivotPage: Int = 0,
  // Pages actively being collected and loaded from
  val on: List<Int> = listOf(),
  // Pages whose emissions are in memory, but are not being collected from
  val off: List<Int> = listOf(),
  // Pages to remove from memory
  val evict: List<Int> = listOf(),
)

private fun LoadMetadata.toRequests(): Flow<Tile.Input.List<Int, List<Int>>> =
  listOf<List<Tile.Input.List<Int, List<Int>>>>(
    evict.map { Tile.Request.Evict(it) },
    off.map { Tile.Request.Off(it) },
    on.map { Tile.Request.On(it) },
  )
    .flatten()
    .asFlow()

class ManagedNumberFetcher {
  private val requests = MutableStateFlow(0)

  val managedRequests: Flow<Tile.Input.List<Int, List<Int>>> = requests
    .scan(LoadMetadata()) { previousMetadata, page ->
      // Load 5 pages pivoted around the current page at once
      val on: List<Int> = ((page - 2)..(page + 2))
        .filter { it >= 0 }
        .toList()
      // Keep 2 pages on either end of the active pages in memory
      val off: List<Int> = (((page - 5)..(page - 3)) + ((page + 3)..(page + 5)))
        .filter { it >= 0 }
      LoadMetadata(
        on = on,
        off = off,
        pivotPage = page,
        // Evict everything not in the curren active and inactive range
        evict = (previousMetadata.on + previousMetadata.off) - (on + off).toSet()
      )
    }
    .distinctUntilChanged()
    .flatMapLatest(LoadMetadata::toRequests)

  private val tiledList: (Flow<Tile.Input.List<Int, List<Int>>>) -> Flow<List<List<Int>>> = tiledList(
    order = Tile.Order.PivotSorted(comparator = Int::compareTo),
    fetcher = { page ->
      val start = page * 50
      val numbers = start.until(start + 50)
      flowOf(numbers.toList())
    }
  )

  val listItems: Flow<List<Int>> = tiledList.invoke(managedRequests)
    .map(List<List<Int>>::flatten)

  fun fetchPage(page: Int) {
    requests.value = page
  }
}
```

In the above, only 5 page flows are collected at any one time. 4 more pages are kept in memory for quick resumption,
and the rest are evicted from memory.

### Use cases

Tilers are useful in a scenario where the underlying data sets are dynamic. This can range from common cases like
endless scrolling of lists whose individual elements change often, to niche cases like crosswords that alert users if
their solving attempts are correct. Please take a look at the sample app for concrete examples.

## License

    Copyright 2021 Google LLC

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

[badge-android]: http://img.shields.io/badge/-android-6EDB8D.svg?style=flat

[badge-jvm]: http://img.shields.io/badge/-jvm-DB413D.svg?style=flat

[badge-js]: http://img.shields.io/badge/-js-F8DB5D.svg?style=flat

[badge-js-ir]: https://img.shields.io/badge/support-[IR]-AAC4E0.svg?style=flat

[badge-nodejs]: https://img.shields.io/badge/-nodejs-68a063.svg?style=flat

[badge-linux]: http://img.shields.io/badge/-linux-2D3F6C.svg?style=flat

[badge-windows]: http://img.shields.io/badge/-windows-4D76CD.svg?style=flat

[badge-wasm]: https://img.shields.io/badge/-wasm-624FE8.svg?style=flat

[badge-apple-silicon]: http://img.shields.io/badge/support-[AppleSilicon]-43BBFF.svg?style=flat

[badge-ios]: http://img.shields.io/badge/-ios-CDCDCD.svg?style=flat

[badge-mac]: http://img.shields.io/badge/-macos-111111.svg?style=flat

[badge-watchos]: http://img.shields.io/badge/-watchos-C0C0C0.svg?style=flat

[badge-tvos]: http://img.shields.io/badge/-tvos-808080.svg?style=flat
