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
        onQueryChanged = { page -> if (it != null) state.setVisiblePage(page) }
    )
}
```

As the user scrolls, `setVisiblePage` is called to keep pivoting about the current position.

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

When paging with a `TiledList`, grouping can still be performed however make sure to use the `groupBy`
extension on a `TiledList`, and not on the one exposed on a `List`. The returned `Map` can then be
used in Jetpack Compose like the snippet above.

