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

For example consider a DB table consisting of chats:

| id   | Author | Message                                 |
|------|--------|-----------------------------------------|
| ...  |        |                                         |
| 0998 | Jess   | Hi Mel!                                 |
| 0999 | Mel    | Hello Jess!                             |
| 1000 | Jess   | Were you able to get the birthday cake? |
| ...  |        |                                         |

Assuming 20 items per query, messages 0950 - 1000 will be contained in a query for page 50.

After Mel responds, the messages table becomes:

| id   | Author | Message                                 |
|------|--------|-----------------------------------------|
| ...  |        |                                         |
| 0998 | Jess   | Hi Mel                                  |
| 0999 | Mel    | Hello Jess                              |
| 1000 | Jess   | Were you able to get the birthday cake? |
| 1001 | Mel    | I did! Bringing it over!                |
| ...  |        |                                         |

There are now 51 pages of messages. When page 51 emits:

* It will have the exact same items as page 50 except message 0951.
* Page 50 will still have messages 0950 - 1000.
* At some point in the future, page 50 will update to contain messages 0901 - 0951.

Until page 50 updates, there will be 49 duplicate items in the list. To address this, the
produced `TiledList` will need to be filtered for duplicates since keys must be unique in Compose
lazy layouts and indices cannot be used for keys without losing animations.

This is easily done using `TiledList.distinct()` or `TiledList.distinctBy()`. The cost of this fixed
since a `TiledList` is a sublist of the entire collection. Using a pivoted tiling
pipeline where 5 queries are kept on, but 3 queries are presented to the UI at any one
time (using `Tile.Limiter`), the fixed cost for de-duplicating items for every message
received is O(60).

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
