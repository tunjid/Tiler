# Placeholders

When loading data from asynchronous sources, it is sometimes required to show static data first.
Since tiling exposes a `List`, inserting placeholders typically involves emitting the placeholder
items first.

## Example

Consider the following repository that fetches a list of tracks from the network with paginated
offsets:

```kotlin

private const val LIMIT = 20

/**
 * A query for tracks at a certain offset
 */
data class TracksQuery(
    val offset: Int,
    val limit: Int = LIMIT,
)

interface TracksRepository {
    suspend fun tracksFor(query: TracksQuery): List<Track>
}
```

The above can be represented in the UI with a sealed class hierarchy for presentation:

```kotlin
sealed class TrackItem {
    data class Placeholder(
        val key: String,
    ): TrackItem()

    data class Loaded(
        val key: String,
        val track: Track,
    ): TrackItem()
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
): Flow<TiledList<TracksQuery, TrackItem>> = queries
    .toPivotedTileInputs(pivotRequest)
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
                flow {
                    val keys = (query.offset until (query.offset + query.limit))
                    // emit all placeholders first
                    emit(keys.map(TrackItem::Placeholder))
                    // Fetch tracks asynchronously
                    val tracks = repository.tracksFor(query)
                    emit(
                        tracks.mapIndexed { index, track ->
                            TrackItem.Loaded(
                                // Make sure the loaded items and placeholders share the same keys
                                key = keys[index],
                                track = track,
                            )
                        }
                    )
                }
                    // A basic retry strategy if the network fetch fails
                    .retry(retries = 10) { e ->
                        e.printStackTrace()
                        // retry on any IOException but also introduce delay if retrying
                        val shouldRetry = e is IOException
                        if (shouldRetry) delay(1000)
                        shouldRetry
                    }
                    // If the network is unavailable, nothing may be emitted
                    .catch { emit(emptyTiledList<TracksQuery, TrackItem>()) }
            }
        )
    )
```

## Practical example

An example of placeholder use can be seen in the Musify Spotify clone, on the Podcast
episode detail [screen](https://github.com/tunjid/Musify/blob/main/app/src/main/java/com/example/musify/ui/screens/searchscreen/StateProduction.kt).

