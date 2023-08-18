# Search

The following guide helps create the UI/UX seen below:

<p align="center">
    <img src="../../images/search.gif" alt="Search" width="200"/>
</p>

The code for the above can be seen in the Musify Spotify clone, on the search
[screen](https://github.com/tunjid/Musify/blob/main/app/src/main/java/com/example/musify/ui/screens/podcastshowdetailscreen/StateProduction.kt).

## Guide

Tiling provides data as a continuous stream, so search can be easily implemented without losing
items that were previously fetched by debouncing as the queries change.

Consider a paginated API that allows that allows for filtering results that matches a query:

```kotlin

private const val LIMIT = 20

/**
 * A query for tracks at a certain offset matching a query
 */

data class TracksQuery(
    val matching: String,
    val offset: Int,
    val limit: Int = LIMIT,
)

interface TracksRepository {
    suspend fun tracksFor(query: TracksQuery): List<Track>
}
```


```kotlin
private val pivotRequest = PivotRequest<TracksQuery, TrackItem>(
    onCount = 5,
    offCount = 4,
    comparator = compareBy(TrackQuery::offset),
    nextQuery = {
        copy(offset = offset + limit)
    },
    previousQuery = {
        if (offset == 0) null
        else copy(offset = offset - limit)
    },
)


fun toTiledList(
    startQuery: TracksQuery,
    queries: Flow<TracksQuery>,
    repository: TracksRepository,
): Flow<TiledList<TracksQuery, Track>> =
    queries.debounce {
        // Don't debounce the if its the first character or more is being loaded
        if (it.matching.length < 2 || it.offset != startQuery.offset) 0
        // Debounce for key input
        else 300
    }
        .toPivotedTiledInputs(pivotRequest)
        .toTiledList(
            listTiler(
                order = Tile.Order.PivotSorted(
                    query = startQuery,
                    comparator = compareBy(TrackQuery::offset)
                ),
                limiter = Tile.Limiter(
                    maxQueries = 3
                ),
                fetcher = { query ->
                    repository.tracksFor(query)
                }
            )
        )
        .debounce { tiledItems ->
            // If empty, the search query might have just changed.
            // Allow items to be fetched for item position animations
            if (tiledItems.isEmpty()) 350L
            else 0L
        }
```
