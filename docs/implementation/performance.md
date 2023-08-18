# Efficiency & performance

## General Tiling
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

## MutableTiledList
The performance of each operation for the default `MutableTiledList` implementation is comparable to an `ArrayList` + O(log(T))
where T is the number of `Tile` instances (pages) in the `TiledList`. This makes them perfect for use in recycling and scrolling containers.
