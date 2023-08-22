A `TiledList` is a `List` that:

* Is a sublist of the items in the backing data source.
* Allows for looking up the query that fetched each item.

The latter is done by associating a range of indices in the `List` with a `Tile`.
Effectively, a `TiledList` "chunks" its items by query.
For example, the `TiledList` below is a `List` with 10 items, and two tiles. Each `Tile` covers 5
indices:

```
|     1      |        2      |
[0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
```

A `Tile` is a `value` class with the following public properties:

```kotlin
value class Tile(...) {
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
    fun tileAt(tileIndex: Int): Tile

    /**
     * Returns the query at the specified tile index.
     */
    fun queryAtTile(tileIndex: Int): Query

    /**
     * Returns the query that fetched an [Item] at a specified index.
     */
    fun queryAt(index: Int): Query
}
```

`MutableTiledList` instances also exist:

```kotlin
interface MutableTiledList<Query, Item> : TiledList<Query, Item> {
    fun add(index: Int, query: Query, item: Item)

    fun add(query: Query, item: Item): Boolean

    fun addAll(query: Query, items: Collection<Item>): Boolean

    fun addAll(index: Int, query: Query, items: Collection<Item>): Boolean

    fun remove(index: Int): Item
}
```

This is useful for modifying `TiledList` instances returned. Actions like:

* Inserting separators or other interstitial content
* Mapping items with in memory data after fetching from a database
* General list modification

can be easily performed.