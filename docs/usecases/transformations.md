There are extensions on `TiledList` that offer many standard library like transformations
to allow for easy `TiledList` modification. These include:

* `TiledList.map` and `TiledList.mapIndexed`
* `TiledList.filter`, `TiledList.filterIndexed` and `TiledList.filterisInstance`
* `TiledList.distinct` and `TiledList.distinctBy`

## Generic transformations
For transformations outside of this, a `buildTiledList` method that offers semantics identical to
the Kotlin standard library [`buildList`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/build-list.html)
is also available.

This method is most applicable to additive modifications like adding separators,
or other miscellaneous items at arbitrary indices.
