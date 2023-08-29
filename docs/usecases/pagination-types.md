The following guide details how tiling may be used for

* Offset pagination
* Key set/cursor pagination

## Offset pagination

To use offset pagination with Tiling, embed the offset and limit required in each query specified.
For example, a `PageQuery` with 20 items per page mey be specified as:

```kotlin
data class PageQuery(
    val offset: Int,
    val limit: Int = 20,
)
```

### Example

Each `Flow` used in the `ListTiler` function should emit if the range it covers changes. Consider
the example below:

```kotlin
listTiler(
    order = Tile.Order.PivotSorted(
        query = startQuery,
        comparator = compareBy(PageQuery::offset)
    ),
    limiter = Tile.Limiter(
        maxQueries = 3
    ),
    fetcher = { query ->
        repository.itemsForPage(query)
    }
)
```

In the above, `repository.itemsForPage` may delegate to a SQL backed data source with the following
query:

```roomsql
SELECT *
FROM items
LIMIT :limit
OFFSET :offset;
```

## Key set and cursor based pagination

Some pagination pipelines need the result of an adjacent page to fetch consecutive pages. This
is common with SQL databases with ordered or indexed `WHERE` clauses or network APIs that
return a cursor.

Tiling however is concurrent. It attempts to fetch items for all it's active queries simultaneously.
Therefore, to use tiling with key set or cursor based APIs, use the `neighboredQueryFetcher` method.
It maintains a LIFO map of queries to tokens/cursors which lets active queries without an
adjacent token/cursor `suspend` until one is available.

## Example

Consider an API which returns the cursor for the next page in the response body:

```kotlin
interface ProductRepository {
    fun productsFor(
        limit: Int,
        cursor: String?
    ): ProductsResponse
}

data class ProductsResponse(
    val products: List<Product>,
    val nextPage: String
)
```

A tiled `QueryFetcher` for it can be set up as follows:

```kotlin

data class ProductQuery(
    // Keep track of the current page within the query
    val page: Int,
    val limit: Int = 20,
)

fun productQueryFetcher(
    productRepository: ProductRepository
): QueryFetcher<ProductQuery, Product> = neighboredQueryFetcher(
    // 5 tokens are held in a LIFO queue
    maxTokens = 5,
    // Make sure the first page has an entry for its cursor/token
    seedQueryTokenMap = mapOf(ProductQuery(page = 0) to null),
    fetcher = { query, cursor ->
        val productsResponse = ProductRepository.productsFor(
            limit = query.limit,
            cursor = cursor
        )
        flowOf(
            NeighboredFetchResult(
                // Set the cursor for the next page and any other page with data available.
                // This will cause the fetcher for the pages to be invoked if they are in scope.
                mapOf(ProductQuery(page = 1) to productsResponse.nextPage),
                items = productsResponse.products
            )
        )
    }
)
```

!!! note

    Tiling with cursors requires that the first query be seeded in the `seedQueryTokenMap` argument.
    Without this, all queries will suspend indefinitely as there is no starting query to initialize
    tiling.

The above can then be used in any other tiled paging pipeline:

```kotlin
class ProductState(
    repository: ProductRepository
) {
    private val requests = MutableStateFlow(0)
    private val comparator = compareBy(ProductQuery::page)

    val products: StateFlow<TiledList<ProductQuery, Product>> = requests
        .map { ProductQuery(page = it) }
        .toPivotedTileInputs<Int, FeedItem>(
            PivotRequest(
                onCount = 5,
                offCount = 2,
                comparator = comparator,
                nextQuery = {
                    copy(page + 1)
                },
                previousQuery = {
                    if (page > 0) copy(page - 1)
                    else null
                }
            )
        )
        .toTiledList(
            listTiler(
                // Start by pivoting around 0
                order = Tile.Order.PivotSorted(
                    query = PageQuery(page = 0),
                    comparator = comparator
                ),
                // Limit to only 3 pages of data in UI at any one time, so 90 items
                limiter = Tile.Limiter(
                    maxQueries = 3,
                    itemSizeHint = null,
                ),
                fetcher = productQueryFetcher(repository)
            )
        )
        .stateIn(/*...*/)

    fun setVisiblePage(page: Int) {
        requests.value = page
    }
}
```

!!! note

    The caveats of key set or cursor based paging still apply while tiling; page jumping is not
    supported. Requesting a page that has no cursor will suspend indefinitely unless a cursor
    is provided. This means for dynamic paging pipelines like search, at least one query must emit
    a cursor for new incoming queries. Alternatively, a new tiling pipeline may be assembled.

In the cases where queries change fundamentally where key sets or cursors are invalidated, you will
have to create a new `ListTiler`. However since tiling ultimately produces a `List<Item>`, distinct
tiling pipeline can be seamlessly flatmapped into each other. To maintain a good user experience,
the user's current visible range should be found in the new pipeline so the new pipeline can be
started around the user's current position.