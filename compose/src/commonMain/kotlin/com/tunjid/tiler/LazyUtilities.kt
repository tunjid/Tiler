import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

@Composable
inline fun <Query> LazyListState.middleItem(
    crossinline queryMapper: (LazyListItemInfo) -> Query,
    crossinline onQueryChanged: (Query) -> Unit
) {
    LaunchedEffect(this) {
        snapshotFlow {
            val visible = layoutInfo.visibleItemsInfo
            val middle = visible.getOrNull(visible.size / 2)
            middle?.let(queryMapper)
        }
            .filterNotNull()
            .distinctUntilChanged()
            .collect {
                onQueryChanged(it)
            }
    }
}

@Composable
inline fun <Query> LazyGridState.middleItem(
    crossinline queryMapper: (LazyGridItemInfo) -> Query,
    crossinline onQueryChanged: (Query) -> Unit
) {
    LaunchedEffect(this) {
        snapshotFlow {
            val visible = layoutInfo.visibleItemsInfo
            val middle = visible.getOrNull(visible.size / 2)
            middle?.let(queryMapper)
        }
            .filterNotNull()
            .distinctUntilChanged()
            .collect {
                onQueryChanged(it)
            }
    }
}