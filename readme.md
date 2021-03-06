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

Tiling is achieved with a Tiler; a pure function that has the ability to adapt any generic method of the form:

```kotlin
fun <T> items(query: Query): Flow<T>
```

into a paginated API.

It does this by exposing a functional reactive API most similar to a `Map` where:

* The keys are the queries (`Query`) for data
* The values are dynamic sets of data returned over time as the result of the `Query` key.


  | `typealias`              | type                                                            | Output             |
  |--------------------------|-----------------------------------------------------------------|--------------------|
  | `ListTiler<Query, Item>` | `(Flow<Tile.Input.List<Query, Item>>) -> Flow<List<Item>>`      | A flattened `List` |
  | `MapTiler<Query, Item>`  | `(Flow<Tile.Input.Map<Query, Item>>) -> Flow<Map<Query, Item>>` | `Map<Key, Value>`  |

## Get it

`Tiler` is available on mavenCentral with the latest version indicated by the badge at the top of this readme file.

`implementation com.tunjid.tiler:tiler:version`

## Demo

The demo app is cheekily implemented as a grid of tiles with dynamic colors:

![Demo image](https://github.com/tunjid/tiler/blob/develop/misc/demo.gif)


## Use cases

As tiling is a pure function that operates on a reactive stream, its configuration can be changed on the fly.
This lends it well to the following situations:

* Adaptive pagination: The amount of items paginated through can be adjusted dynamically to account for app window
resizing by turning [on](https://github.com/tunjid/Tiler#inputrequest) more pages and increasing the 
[limit](https://github.com/tunjid/Tiler#inputlimiter) of data sent to the UI from the paginated data available.
An example is in the [Me](https://github.com/tunjid/me/blob/main/common/feature-archive-list/src/commonMain/kotlin/com/tunjid/me/feature/archivelist/ArchiveLoading.kt) app.

* Dynamic sort order: The sort order of paginated items can be changed cheaply on a whim by changing the
[order](https://github.com/tunjid/Tiler#inputorder) as this only operates on the data output from the tiler, and not
the entire paginated data set. An example is in the sample in this
[repository](https://github.com/tunjid/Tiler/blob/develop/common/src/commonMain/kotlin/com/tunjid/demo/common/ui/numbers/advanced/NumberFetching.kt).


## API surface

### Getting your data

Tiling prioritizes access to the data you've paged through, allowing you to read all paginated data at once, or a subset of it
(using `Input.Limiter`). This allows you to trivially transform the data you've fetched after the fact.

Tilers are implemented as plain functions. Given a `Flow` of `Input`, you can either choose to get your data as:

* A `Flow<List<Item>>` with `tiledList`
* A `Flow<Map<Query, Item>>` with `tiledMap`

The choice between the two depends largely on the operations you want to perform on the output before consuming it.
A `MapTiler` could be used when you want a clear separation of the chunks of data,
for example to add headers or to group information in a single component. Alternatively, you can use a `ListTiler`
and call `List<Item>.groupBy {...}` if you find that more ergonomic. The `Map<Query, Item>` in the `Flow` produced from the `MapTiler` is guaranteed to have a stable iteration order defined by
the `Input.Order` passed to it. More on this below.


## Managing requested data

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

### `Input.Limiter`

Can be used to select a subset of items tiled instead of the entire paginated set. For example, assuming 1000 items have been
fetched, there's no need to send a 1000 items to the UI for diffing/display when the UI can only show about 30 at once.
The `Limiter` allows for selecting an arbitrary amount of items as the situation demands.

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

## Examples

### Simple, non scaling example

In this example, the code will return a full list of every item requested sorted in ascending order of the pages.

The list will grow indefinitely as more pages are requested unless queries are evicted. This may
be fine for small lists, but as the list size grows, some items may need to be evicted as only a small subset of items
need to be presented to the UI. This sort of behavior can be achieved using the `Evict` `Request`, and
the `PivotSorted` `Order` covered next.

```kotlin
import Tile
import tiledList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class NumberFetcher {
  private val requests = MutableStateFlow(0)

  private val tiledList: (Flow<Tile.Input.List<Int, List<Int>>>) -> Flow<List<List<Int>>> = tiledList(
    // Sort items in ascending order
    order = Tile.Order.Sorted(comparator = Int::compareTo),
    fetcher = { page ->
      // Fetch 50 numbers for each page
      val start = page * 50
      val numbers = start.until(start + 50)
      flowOf(numbers.toList())
    }
  )

  // All paginated items in a single list
  val listItems: Flow<List<Int>> = tiledList.invoke(
    requests.map { Tile.Request.On(it) }
  )
    .map(List<List<Int>>::flatten)


  fun fetchPage(page: Int) {
    requests.value = page
  }
}
```


### Advanced, scalable solution

To deal with the issue of the tiled data set becoming arbitrarily large, one can create a pipeline where the
pages loaded are defined as a function of the page the user is currently at:

```
[out of bounds]                                  -> Evict from memory
                                          _
[currentPage - n - 1 - n]                  |
...                                        | -> Keep pages in memory, but don't observe
[currentPage - n - 1]          _          _|                        
[currentPage - n]               |
...                             |
[currentPage - 1]               |
[currentPage]                   |  -> Observe pages     
[currentPage + 1]               |
...                             |
[currentPage + n]              _|         _
[currentPage + n + 1]                      |
...                                        | -> Keep pages in memory, but don't observe
[currentPage + n + 1 + n]                 _|

[out of bounds]                                  -> Evict from memory
```

Where `n` is an arbitrary number that may be defined by how many items are visible on the screen at once.

For an example where `n` is a function of grid size in a grid list, check out [ArchiveLoading.kt](https://github.com/tunjid/me/blob/main/common/feature-archive-list/src/commonMain/kotlin/com/tunjid/me/feature/archivelist/ArchiveLoading.kt) in the [me](https://github.com/tunjid/me) project.

An example where `n` is fixed at 2 follows:

```kotlin
import Tile
import tiledList
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
    .scan(LoadMetadata()) { previousMetadata, currentPage ->
      // Load 5 pages pivoted around the current page at once
      val on: List<Int> = ((currentPage - 2)..(currentPage + 2))
        .filter { it >= 0 }
        .toList()
      // Keep 2 pages on either end of the active pages in memory
      val off: List<Int> = (((currentPage - 5)..(currentPage - 3)) + ((currentPage + 3)..(currentPage + 5)))
        .filter { it >= 0 }
      LoadMetadata(
        on = on,
        off = off,
        pivotPage = currentPage,
        // Evict everything not in the curren active and inactive range
        evict = (previousMetadata.on + previousMetadata.off) - (on + off).toSet()
      )
    }
    .distinctUntilChanged()
    .flatMapLatest(LoadMetadata::toRequests)

  private val tiledList: (Flow<Tile.Input.List<Int, List<Int>>>) -> Flow<List<List<Int>>> = tiledList(
    // Sort items in ascending order, pivoted around the current page
    order = Tile.Order.PivotSorted(comparator = Int::compareTo),
    // Output at most 200 items at once to allow for cheap data transformations regardless of paged dataset size
    limiter = Tile.Limiter.List { it.size > 200 },
    fetcher = { page ->
      // Fetch 50 numbers for each page
      val start = page * 50
      val numbers = start.until(start + 50)
      flowOf(numbers.toList())
    }
  )

  val listItems: Flow<List<Int>> = tiledList.invoke(managedRequests)
    .map(List<List<Int>>::flatten)

  fun setCurrentPage(page: Int) {
    requests.value = page
  }
}
```

In the above, only flows for 5 pages are collected at any one time. 4 more pages are kept in memory for quick
resumption, and the rest are evicted from memory. As the user scrolls, `setCurrentPage` is called, and data is
fetched for that page, and the surrounding pages.
Pages that are far away from the current page (more than 4 pages away) are removed from memory.

## Efficiency & performance

As tiling loads from multiple flows simultaneously, performance is a function of 2 things:

* How often the backing `Flow` for each `Input.Request` emits
* The time and space complexity of the transformations applied to the output `List<Item>` or `Map<Query, Item>`.

In the case of a former, the `Flow` should only emit if the backing dataset has actually changed. This prevents unneccessary emissions downstream.

In the case of the latter, by using `Input.Limiter` on the output of the tiler, you can guarantee transformations on the output are a function `O(N)`,
where `N` is the amount defined by the `Input.Limiter`.

For example if tiling is done for the UI, with a viewport that can display 20 items at once, 20 items can be fetched per page, and 100 (20 * 5) pages can be observed at concurrently. Using `Input.Limiter.List { it.size > 100 }`, only 100 items will be sent to the UI at once. The items can be transformed with algorithms of `O(N)` to `O(N^2)` time and space complexity trivially as regardless of the size of the actual paginated set, only 100 items will be transformed at any one time.

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
