# Adaptive paging


## A guide on achieving...

The following guide should help create the UI/UX seen below:

<p align="center">
    <img src="../../images/adaptive.gif" alt="Adaptive" width="200"/>
</p>

## Guide

Situations can arise that can require more dynamic pagination.
Consider paging for an adaptive layout. The items fetched can be a function of:

* A UI that can change in size, requiring more items to fit the view port.
* A user wanting to change the sort order.

Assume a list of numbers shown in a grid. The view port may be dynamically resized, and the sort order may be toggled.

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