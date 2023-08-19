# Placeholders

The following guide helps create the UI/UX seen below:

<p align="center">
    <img src="../../images/placeholders.gif" alt="Placeholders" width="200"/>
</p>

The code for the above can be seen in the Musify Spotify clone, on the Podcast
episode detail [screen](https://github.com/tunjid/Musify/blob/main/app/src/main/java/com/example/musify/ui/screens/searchscreen/StateProduction.kt).

## Guide

When loading data from asynchronous sources, it is sometimes required to show static data first.
Since tiling exposes a `List`, inserting placeholders typically involves emitting the placeholder
items first.

Consider the following repository that fetches a list of podcast episodes from the network with
paginated offsets:

```kotlin

private const val LIMIT = 20

/**
 * A query for tracks at a certain offset
 */
data class PodcastEpisodeQuery(
    val offset: Int,
    val limit: Int = LIMIT,
)

interface PodcastEpisodeRepository {
    suspend fun episodesFor(query: PodcastEpisodeQuery): List<Track>
}
```

The above can be represented in the UI with a sealed class hierarchy for presentation:

```kotlin
sealed class PodcastEpisodeItem {
    data class Placeholder(
        val key: String,
    ): PodcastEpisodeItem()

    data class Loaded(
        val key: String,
        val track: Track,
    ): PodcastEpisodeItem()
}
```

The tiling pipeline can then be used to emit placeholders immediately, then the actual items can
then be fetched asynchronously.

```kotlin
fun tiledPodcastEpisodes(
    startQuery: PodcastEpisodeQuery,
    queries: Flow<PodcastEpisodeQuery>,
    repository: PodcastEpisodeRepository,
): Flow<TiledList<PodcastEpisodeQuery, PodcastEpisodeItem>> = queries
    .toPivotedTileInputs(
        PivotRequest<PodcastEpisodeQuery, PodcastEpisodeItem>(
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
    )
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
                    emit(keys.map(PodcastEpisodeItem::Placeholder))
                    // Fetch tracks asynchronously
                    val episodes = repository.episodesFor(query)
                    // if the repository returns a `Flow`, `emitAll` can be used instead
                    emit(
                        episodes.mapIndexed { index, track ->
                            PodcastEpisodeItem.Loaded(
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
                    .catch { emit(emptyTiledList<PodcastEpisodeQuery, PodcastEpisodeItem>()) }
            }
        )
    )
```
