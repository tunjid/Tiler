# Basic example

Imagine a social media feed app backed by a `FeedRepository`.
Each page in the repository returns 100 items. A pivoted tiling pipeline for it can be assembled as follows:

```kotlin
class FeedState(
    repository: FeedRepository
) {
    private val requests = MutableStateFlow(0)

    private val comparator = Comparator(Int::compareTo)
  
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
        .toTiledList(
          listTiler(
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
        )
        .stateIn(/*...*/)

    fun setVisiblePage(page: Int) {
        requests.value = page
    }
}
```