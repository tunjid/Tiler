## Pivoting with Jetpack Compose

Pivoted tiling in Jetpack Compose is done using the `PivotedTilingEffect`:

```kotlin
@Composable
fun Feed(
    state: FeedState
) {
    val feed by state.feed.collectAsState()
    val lazyState = rememberLazyListState()

    LazyColumn(
        state = lazyState,
        content = {
            items(
                items = feed,
                key = FeedItem::key,
                itemContent = { /*...*/ }
            )
        }
    )

    lazyState.PivotedTilingEffect(
        items = feed,
        // Update the user's current visible query
        onQueryChanged = { page -> if (it != null) state.setVisiblePage(page) }
    )
}
```

As the user scrolls, `setVisiblePage` is called to keep pivoting about the current position.

## Unique keys

Tiling collects from each `Flow` for all queries that are on concurrently. For pagination from
a database where items can be inserted, items may be duplicated in the produced `TIledList`.

For example consider a DB table consisting of tasks sorted by ascending date:

| id   | date     | task                  |
|------|----------|-----------------------|
| ...  |          |                       |
| 0998 | 01/01/23 | Go for a jog          |
| 0999 | 01/04/23 | Print shipping labels |
| ...  |          |                       |

Assuming 20 items per query, tasks 0980 - 0999 will be contained in a query for page 50.

Assume a new task for "Check invoices" with id 1000 is entered for date 01/03/23:

| id   | date     | task                  |
|------|----------|-----------------------|
| ...  |          |                       |
| 0998 | 01/01/23 | Go for a jog          |
| 1000 | 01/03/23 | Check invoices        |
| 0999 | 01/04/23 | Print shipping labels |
| ...  |          |                       |

There are now 51 pages of tasks. When page 51 emits:

* It will contain the last task alone; task 099 "Print shipping labels".
* Page 50 will still have its last emitted tasks 0980 - 0999, including "Print shipping labels".
* At some point in the future, page 50 will update to contain the new task 1000 "Check invoices" and
  exclude task 0999 - "Print shipping labels".

Until page 50 updates, task 0999 "Print shipping labels" will be duplicated in the list. To address
this, the
produced `TiledList` will need to be filtered for duplicates since keys must be unique in Compose
lazy layouts and indices cannot be used for keys without losing animations.

This is easily done using `TiledList.distinct()` or `TiledList.distinctBy()`. The cost of this fixed
since a `TiledList` is a sublist of the entire collection. Using a pivoted tiling
pipeline where 5 queries are kept on, but 3 queries are presented to the UI at any one
time (using `Tile.Limiter`), the fixed cost for de-duplicating items for every change in the
data set is O(60).

Note: Page 51 is not guaranteed to have emitted first. Any query can emit at anytime
when tiling. Tiling presents snapshots of the paging pipeline at a single point in time. It is not
opinionated about the data contained. It only guarantees ordering of the queries according to the
`Tile.Order` specified in the tiling configuration. This makes it flexible enough for post
processing of data like filtering, debouncing, mapping and so on.

## Sticky headers

For a `LazyList` in Compose, sticky headers can be implemented using the following:

```kotlin
// This ideally would be done in the ViewModel
val grouped = contacts.groupBy { it.firstName[0] }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactsList(grouped: Map<Char, List<Contact>>) {
    LazyColumn {
        grouped.forEach { (initial, contactsForInitial) ->
            stickyHeader {
                CharacterHeader(initial)
            }

            items(contactsForInitial) { contact ->
                ContactListItem(contact)
            }
        }
    }
}
```

When paging with a `TiledList`, grouping can still be performed. If you do not need `TiledList`
metadata on the grouped data use `List.groupBy()`, otherwise use `TiledList.groupBy()` which will
return a `Map<Key, TiledList<Query, Item>`.
