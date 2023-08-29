## API surface and Tiling primitives

Given a `Flow` of `Tile.Input`, tiling transforms them into a `Flow<TiledList<Query, Item>>` with
a `ListTiler`.

The resulting `TiledList` should be kept at a size that covers approximately 3 times the viewport.
This is typically at or under 100 items for non grid UIs. You can then transform this list however
way you want.

## Managing requested data

A tiled pagination pipeline is managed by the `Tile.Input` it receives. These inputs drive the
dynamism of the pipeline. The following is a breakdown of them all.

### `Input.Request`

* `Tile.Request.On`: Analogous to `put` for a `Map`, this starts collecting from the backing `Flow`
  for the specified `query`. It is
  idempotent; multiple requests have no side effects for loading, i.e the same `Flow` will not be
  collect twice.

* `Tile.Request.Off`: Stops collecting from the backing `Flow` for the specified `query`. The items
  previously fetched by this query
  are still kept in memory and will be in the `TiledList` of items returned. Requesting this is
  idempotent; multiple requests
  have no side effects.

* `Tile.Request.Evict`: Analogous to `remove` for a `Map`, this stops collecting from the
  backing `Flow` for the specified `query` and
  also evicts the items previously fetched by the `query` from memory. Requesting this is
  idempotent; multiple requests
  have no side effects.

### `Tile.Batch`

Used for dispatching multiple `Tile.Input` instances. The `ListTiler` may emit `TiledList` instances
during the application of a `Tile.Batch` input; it is not transactional. Rather, it is an
encapsulation of an aggregate of `Tile.Input` that represents a single logical operation. Users of
the library may also define arbitrary `Tile.Batch` instances and use them in their tiled pipelines.

### `Pivot`

An implementation of `Tile.Batch`, this allows for returning a `TiledList` from results
around a particular `Query`. It's use must be accompanied by a `Tile.Order.PivotSorted`.

### `Tile.Input.Limiter`

Can be used to select a subset of items tiled instead of the entire paginated set. For example,
assuming 1000 items have been
fetched, there's no need to send a 1000 items to the UI for diffing/display when the UI can only
show about 30 at once.
The `Limiter` allows for selecting an arbitrary amount of items as the situation demands.

### `Tile.Input.Order`

Defines the heuristic for selecting tiled items into the output `TiledList`.

* Sorted: Sort items with a specified query `comparator`.

* PivotSorted: Sort items with the specified `comparator` but pivoted around a specific `Query`.
  This allows for showing items that have more priority over others in the current context
  like example in a list being scrolled. In other words assume tiles have been fetched for queries
  1 - 10 but a
  user can see pages 5 and 6. The UI need only to be aware of pages 4, 5, 6, and 7. This allows for
  a rolling window of
  queries based on a user's scroll position.

