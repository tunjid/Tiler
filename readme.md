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

### Paging as state

Paging as state is the representation of paginated data in a form that is idiomatic and easy to consume with declarative and/or functional programming paradigms. This typically means the data paged through offers APIs that are identical to, or similar enough to a `List`; offering constant time or near constanr time access to items at indices, and the ability to introspect the items paged through.

### Tiling as a paging as state implementation

Tiling is a kotlin multiplatform experiment for loading chunks of structured data from reactive sources.

Tiling is achieved with a Tiler; a pure function that has the ability to adapt any generic method of the form:

```kotlin
fun <T> items(query: Query): Flow<List<T>>
```

into a paginated API.

It does this by exposing a functional reactive API:

* The inputs modify the queries for data
* The output is the data returned over time in a `List`.

This output of tiling is a `TiledList`. Its a `List` implementation that allows for looking up the query that fetched each item.
This is done by associating a range of indices in the `List` with a `Tile`. Effectively a `TiledList` "chunks" its items by query. 
For example, the `TiledList` below is a `List` with 10 items, and two tiles. Each `Tile` covers 5 indices:

```
|     1      |        2      |
[0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
```

A `Tile` is a `value` class with the following public properties:

```kotlin
value class Tile(..) {
    // start index for a chunk
    val start: Int
    // end exclusive index for a chunk
    val end: Int
}
``` 

A `TiledList` is defined as:

```kotlin
interface TiledList<Query, Item> : List<Item> {
    /**
     * The number of [Tile] instances or query ranges there are in this [TiledList]
     */
    val tileCount: Int

    /**
     * Returns the [Tile] at the specified tile index.
     */
    fun tileAt(index: Int): Tile

    /**
     * Returns the query at the specified tile index.
     */
    fun queryAtTile(index: Int): Query

    /**
     * Returns the query that fetched an [Item] at a specified index.
     */
    fun queryAt(index: Int): Query
}
```

Mutable `TiledList` instances also exist so items may be added to the `TiledList` from outher sources after fetching. This is useful for modifying the `TiledList` returned. Actions like:

* Inserting separators or other interstitial content
* Mapping items with in memory data after fetching from a database
* General list modification

can be easily performed.

```kotlin
interface MutableTiledList<Query, Item> : TiledList<Query, Item> {
    fun add(index: Int, query: Query, item: Item)

    fun add(query: Query, item: Item): Boolean

    fun addAll(query: Query, items: Collection<Item>): Boolean

    fun addAll(index: Int, query: Query, items: Collection<Item>): Boolean

    fun remove(index: Int): Item
}
```

The performance of each operation for the default `MutableTiledList` implementation is comparable to an `ArrayList` + O(log(T))
where T is the number of `Tile` instances (pages) in the `TiledList`. This makes them perfect for use in recycling and scrolling containers.

## Demo

The demo app is cheekily implemented as a dynamic grid of tiles with dynamic colors that update many times per second:

![Demo image](https://github.com/tunjid/tiler/blob/develop/misc/demo.gif)


## Use cases

As tiling is a pure function that operates on a reactive stream, its configuration can be changed on the fly.
This lends it well to the following situations:

* Offline-first apps: Tiling delivers targeted updates to only queries that have changed. This works well for
  apps which write to the database as the source of truth, and need the UI to update immediately. For example
  a viral tweet whose like count updates several times a second.

* Adaptive pagination: The amount of items paginated through can be adjusted dynamically to account for app window
resizing by turning [on](https://github.com/tunjid/Tiler#inputrequest) more pages and increasing the 
[limit](https://github.com/tunjid/Tiler#inputlimiter) of data sent to the UI from the paginated data available.
An example is in the [Me](https://github.com/tunjid/me/blob/main/common/feature-archive-list/src/commonMain/kotlin/com/tunjid/me/feature/archivelist/ArchiveLoading.kt) app.

* Dynamic sort order: The sort order of paginated items can be changed cheaply on a whim by changing the
[order](https://github.com/tunjid/Tiler#inputorder) as this only operates on the data output from the tiler, and not
the entire paginated data set. An example is in the sample in this
[repository](https://github.com/tunjid/Tiler/blob/develop/common/src/commonMain/kotlin/com/tunjid/demo/common/ui/numbers/advanced/NumberFetching.kt).

## Get it

`Tiler` is available on mavenCentral with the latest version indicated by the badge at the top of this readme file.

`implementation com.tunjid.tiler:tiler:version`

## How to page with Tiling

While the tiling API lets you assemble a paging pipeline from scratch using its primitives, the easiest scalable
tiling approach is through the pivoting algorithm.

Consider a large, possibly infinite set of paginated data where a user is currently viewing page p, and n
is the buffer zone - the number of pages lazy loaded in case the user wants to visit it.

```
[..., p - n, ..., p - 1, p, p + 1, ..., p + n, ...]
```

As the user moves from page to page, items can be refreshed around the user's current page
while allowing them to observe immediate changes to the data they're looking at.

This is expanded in the diagram below:

```
[out of bounds]                        -> Evict from memory
                                _
[p - n - 1 - n]                  |
...                              | -> Keep pages in memory, but don't observe
[p - n - 1]          _          _|                        
[p - n]               |
...                   |
[p - 1]               |
[p]                   |  -> Observe pages     
[p + 1]               |
...                   |
[p + n]              _|         _
[p + n + 1]                      |
...                              | -> Keep pages in memory, but don't observe
[p + n + 1 + n]                 _|

[out of bounds]                        -> Evict from memory
```

`n` is an arbitrary number that may be defined by how many items are visible on the screen at once. It could be fixed,
or variable depending on conditions like the available screen real estate.

For an example where `n` is a function of grid size in a grid list, check out [ArchiveLoading.kt](https://github.com/tunjid/me/blob/main/common/feature-archive-list/src/commonMain/kotlin/com/tunjid/me/feature/archivelist/ArchiveLoading.kt) in the [me](https://github.com/tunjid/me) project.

The above algorithm is called "pivoting" as items displayed are pivoted around the user's current scrolling position.

Since tiling is dynamic at it's core, a pipeline can be built to allow for this dynamic behavior by pivoting around the user's current position with the grid size as a dynamic input parameter as shown below:

### Basic example

Imagine a social media feed app backed by a `FeedRepository`.
Each page in the repository returns 100 items. A pivoted tiling pipeline for it can be assembled as follows:

```kotlin
class FeedState(
    repository: FeedRepository
) {
    private val requests = MutableStateFlow(0)

    private val comparator = Comparator(Int::compareTo)

    private val listTiler: ListTiler<Int, FeedItem> = listTiler(
        // Start by pivoting around 0
        order = Tile.Order.PivotSorted(
          query = 0,
          comparator = comparator
        ),
        // Limit to only 3 pages of data in UI at any one time
        limiter = Tile.Limiter(
          maxQueries = 3,
          itemSizeHint = null,
        ),
        fetcher = { page ->
          repository.getPage(page)
        }
    )
  
    // A TiledList is a regular List that has information about what
    // query fetched an item at each index
    val feed: StateFlow<TiledList<Int, FeedItem>> = requests
        .toPivotedTileInputs<Int, FeedItem>(
            PivotRequest(
                // 5 pages are fetched concurrently, so 500 items
                onCount = 5,
                // A buffer of 2 extra pages on either side are kept, so 700 items total
                offCount = 2,
                comparator = comparator,
                nextQuery = {
                    this + 1
                },
                previousQuery = {
                    (this - 1).takeIf { it >= 0 }
                }
            )
        )
        .toTiledList(listTiler)
        .stateIn(/*...*/)

    fun setVisiblePage(page: Int) {
        requests.value = page
    }
}
```

The UI is responsible for setting the visible page. In Jetpack Compose, this is easily done using the 
`PivotedTilingEffect`:

```kotlin
@Composable
fun Feed(
    state: FeedState
) {
    val feed by state.feed.collectAsState()
    val lazyState = rememberLazyListState()
  
    LazyColumn(
        state = lazyState,
        content = {
            items(
                items = feed,
                key = FeedItem::key,
                itemContent = { /*...*/ }
            )
        }
    )

    lazyState.PivotedTilingEffect(
        items = feed,
        onQueryChanged = { page -> if (it != null) state.setVisiblePage(page) }
    )
}
```

As the user scrolls, `setVisiblePage` is called to keep pivoting about the current position with the following
constraints:

* At most 3 pages of content (300 items) will be in the UI at any one time.
* 5 pages will be observed concurrently, including the current page of the user. Any changes to the data in any
of the pages will update the UI if it will be seen by the user (it is in th 300 being presented).
* 2 pages will be kept in memory for quick access to accommodate UI interactions like flinging or jumping to a page.

### Advanced example

Situations can arise that can require more dynamism. The items fetched can be a function of:

* A UI that can change in size, requiring more items to fit the view port.
* A user wanting to change the sort order.

Consider a list of numbers shown in a grid. The view port may be dynamically resized, and the sort order may be toggled.

A pivoting pipeline for the above looks like:

```kotlin

// Query for items describing the page and sort order
data class PageQuery(
  val page: Int,
  val isAscending: Boolean
)

class Loader(
  isDark: Boolean,
  scope: CoroutineScope
) {
  // Current query that is visible in the view port
  private val currentQuery = MutableStateFlow(
    PageQuery(
      page = 0,
      isAscending = true
    )
  )

  // Number of columns in the grid
  private val numberOfColumns = MutableStateFlow(1)

  // Flow specifying the pivot configuration
  private val pivotRequests = combine(
    currentQuery.map { it.isAscending },
    numberOfColumns,
    ::pivotRequest
  ).distinctUntilChanged()

  // Define inputs that match the current pivoted position
  private val pivotInputs = currentQuery.toPivotedTileInputs<PageQuery, NumberTile>(
    pivotRequests = pivotRequests
  )

  // Allows for changing the order on response to user input
  private val orderInputs = currentQuery
    .map { pageQuery ->
      Tile.Order.PivotSorted<PageQuery, NumberTile>(
        query = pageQuery,
        comparator = when {
          pageQuery.isAscending -> ascendingPageComparator
          else -> descendingPageComparator
        }
      )
    }
    .distinctUntilChanged()

  // Change limit to account for dynamic view port size
  private val limitInputs = numberOfColumns.map { gridSize ->
    Tile.Limiter<PageQuery, NumberTile> { items -> items.size > MIN_ITEMS_TO_SHOW * gridSize }
  }

  val tiledList: Flow<TiledList<PageQuery, NumberTile>> = merge(
    pivotInputs,
    orderInputs,
    limitInputs,
  )
    .toTiledList(
      numberTiler(
        itemsPerPage = ITEMS_PER_PAGE,
        isDark = isDark,
      )
    )

  fun setCurrentPage(page: Int) = currentQuery.update { query ->
    query.copy(page = page)
  }

  fun toggleOrder() = currentQuery.update { query ->
    query.copy(isAscending = !query.isAscending)
  }

  fun setNumberOfColumns(numberOfColumns: Int) = this.numberOfColumns.update {
    numberOfColumns
  }

  // Avoid breaking object equality in [PivotRequest] by using vals
  private val nextQuery: PageQuery.() -> PageQuery? = {
    copy(page = page + 1)
  }
  private val previousQuery: PageQuery.() -> PageQuery? = {
    copy(page = page - 1).takeIf { it.page >= 0 }
  }

  /**
   * Pivoted tiling with the grid size as a dynamic input parameter
   */
  private fun pivotRequest(
    isAscending: Boolean,
    numberOfColumns: Int,
  ) = PivotRequest(
    onCount = 4 * numberOfColumns,
    offCount = 4 * numberOfColumns,
    nextQuery = nextQuery,
    previousQuery = previousQuery,
    comparator = when {
      isAscending -> ascendingPageComparator
      else -> descendingPageComparator
    }
  )
}

private fun numberTiler(
  itemsPerPage: Int,
  isDark: Boolean,
): ListTiler<PageQuery, NumberTile> =
  listTiler(
    limiter = Tile.Limiter { items -> items.size > 40 },
    order = Tile.Order.PivotSorted(
      query = PageQuery(page = 0, isAscending = true),
      comparator = ascendingPageComparator
    ),
    fetcher = { pageQuery ->
      pageQuery.colorShiftingTiles(itemsPerPage, isDark)
    }
  )
```

In the above, only flows for 4 * numOfColumns queries are collected at any one time. 4 * numOfColumns more queries are kept in memory for quick
resumption, and the rest are evicted from memory. As the user scrolls, `setCurrentPage` is called, and data is
fetched for that page, and the surrounding pages.
Pages that are far away from the defined range are removed from memory.

## Efficiency & performance

As tiling loads from multiple flows simultaneously, performance is a function of 2 things:

* How often the backing `Flow` for each `Input.Request` emits
* The time and space complexity of the transformations applied to the output `TiledList<Query, Item>`.

In the case of a former, the `Flow` should only emit if the backing dataset has actually changed. This prevents unnecessary emissions downstream.

In the case of the latter, use `PivotRequest(on = x)` and `Input.Limiter` to match the
output `TiledList` to the view port of the user's device to create an efficient paging pipeline.

For example if tiling is done for the UI, with a viewport that can display 20 items at once:

* 20 items can be fetched per page
* 100 items (20 * 5 pages) can be observed at concurrently
* `Input.Limiter.List(maxQueries = 3)` can be set so only changes to the visible 60 items will be sent to the UI at once.

The items can be transformed with algorithms of `O(N)` to `O(N^2)` time and space complexity
trivially as regardless of the size of the actual paginated set, only 60 items will be transformed
at any one time.

## API surface and Tiling primitives

### Getting your data

Tiling prioritizes access to the data you've paged through, allowing you to read all paginated data at once, or a subset of it
(using `Input.Limiter`). This allows you to trivially transform the data you've fetched after the fact.

Tilers are implemented as plain functions. Given a `Flow` of `Input`, tiling transforms them into a `Flow<TiledList<Query, Item>>` with a `ListTiler`.

The resulting `TiledList` should be kept at under 100 items. You can then transform this list however way you want.

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

* PivotAround: Only valid when using the `PivotSorted` `Order`, this allows for returing a `TiledList` from results
  around a certain `Query`.

### `Input.Limiter`

Can be used to select a subset of items tiled instead of the entire paginated set. For example, assuming 1000 items have been
fetched, there's no need to send a 1000 items to the UI for diffing/display when the UI can only show about 30 at once.
The `Limiter` allows for selecting an arbitrary amount of items as the situation demands.

### `Input.Order`

Defines the heuristic for selecting tiled items into the output `TiledList`.

* Sorted: Sort items with a specified query `comparator`.

* PivotSorted: Sort items with the specified `comparator` but pivoted around a specific `Query`.
  This allows for showing items that have more priority over others in the current context
  like example in a list being scrolled. In other words assume tiles have been fetched for queries 1 - 10 but a
  user can see pages 5 and 6. The UI need only to be aware of pages 4, 5, 6, and 7. This allows for a rolling window of
  queries based on a user's scroll position.


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
