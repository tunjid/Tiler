In your application you may have scenarios that are a combination of all the use cases covered:

* Adapts to different screen sizes
* Uses placeholders
* Implements search
* Requires extra transformations

In situations like this, the preceding sections still apply. Each use case is
independent by and large. That said, the most difficult issue faced with combined use cases is
key preservation.

# Key preservation
In UIs, keys provide unique tokens to represent items in lists. This is necessary for scroll
state preservation and animations. In a tiled paging pipeline where items change due to:

* Placeholders being replaced
* Items being sorted or reordered differently
* Generic items being replaced
* Search queries changing

The best way to preserve keys across these changes is to maintain a snapshot of the old list,
and when the new list arrives, preserve the keys in the old list in the new list by defining
a way to identify them in the new list.

### On each query...
In a range of items in a query/page that has 20 items:

* First generate 20 unique ids for all items in that range.
* Create placeholders that use those ids and emit them.
* Asynchronously load and emit new items, and use the same ids for the placeholders in those items.

### On each `TiledList` emission...

* Keep a reference to the current list presented in the UI
* When the new `TiledList` is emitted, compare the old list to the new list
* If the old list has items that not placeholders that are present in the new list, replace the ids in the new list with the ids from the old list.
* Make sure ids are not duplicated.

The steps above will allow you to achieve smooth item animations in complex pagination pipelines.

# Practical example

See the `ArchiveList` state production pipeline in the [me](https://github.com/tunjid/me/blob/main/common/ui/archive-list/src/commonMain/kotlin/com/tunjid/me/feature/archivelist/ArchiveListStateHolder.kt)
github project for an example of a a complex tiled pagination pipeline with key preservation across
multiple queries.