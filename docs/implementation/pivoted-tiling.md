## How to page with Tiling

While the tiling API lets you assemble a paging pipeline from scratch using its primitives, the
easiest scalable
pagination approach with tiling is through the pivoting algorithm.

Consider a large, possibly infinite set of paginated data where a user is currently viewing page p,
and n
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

`n` is an arbitrary number that may be defined by how many items are visible on the screen at once.
It could be fixed,
or variable depending on conditions like the available screen real estate.

For an example where `n` is a function of grid size in a grid list, check
out [ArchiveLoading.kt](https://github.com/tunjid/me/blob/main/common/feature-archive-list/src/commonMain/kotlin/com/tunjid/me/feature/archivelist/ArchiveLoading.kt)
in the [me](https://github.com/tunjid/me) project.

The above algorithm is called "pivoting" as items displayed are pivoted around the user's current
scrolling position.

Since tiling is dynamic at it's core, a pipeline can be built to allow for this dynamic behavior by
pivoting around the user's current position with the grid size as a dynamic input parameter.