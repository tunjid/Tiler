Some pagination pipelines need the result of an adjacent page to fetch consecutive pages. This
is common with network APIs that return a cursor.

Tiling however is concurrent. It attempts to fetch items for all it's active queries simultaneously.
Therefore, to use tiling with cursor based APIs, use the `neighboredQueryFetcher` method.
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
                // Set the cursor for the next page. This will cause the fetcher for the
                // next page to be invoked since a cursor is now available
                mapOf(ProductQuery(page = 1) to productsResponse.nextPage),
                items = productsResponse.products
            )
        )
    }
)
```

!!! note

    Tiling with cursors requires that the first query be seeded in the `seedQueryTokenMap` argument.
    Without this, all queries will suspend indefinitely as there is not starting query to initialize
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

    The caveats of cursor based paging still apply while tiling; page jumping is not supported.
    Furthermore, requesting a page that has no cursor will suspend indefinitely unless a cursor
    is provided. This means for dynamic paging pipelines like search, at least one query must emit
    a cursor for new incoming queries. Alternatively, a new tiling pipeline may be assembled.