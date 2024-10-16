package com.example.benchmarks.data

import com.tunjid.tiler.PivotRequest
import com.tunjid.tiler.Tile
import com.tunjid.tiler.TiledList
import com.tunjid.tiler.listTiler
import com.tunjid.tiler.queries
import com.tunjid.tiler.toPivotedTileInputs
import com.tunjid.tiler.toTiledList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.transformWhile

private val pivotRequest = PivotRequest<Int, Item>(
    onCount = NUM_PAGES_IN_MEMORY,
    offCount = 0,
    comparator = Int::compareTo,
    nextQuery = Int::next,
    previousQuery = Int::prev,
)

private fun Int.next() = this + 1

private fun Int.prev() = if (this <= 0) null else this - 1

class TilingBenchmark(
    private val pageToScrollTo: Int,
    private val pagesToInvalidate: IntRange
) : Benchmarked {

    private var lastInvalidatedPage: Int = pagesToInvalidate.first + 1

    override suspend fun benchmark() {
        val offsetFlow = MutableSharedFlow<Int>()
        val invalidationSignal = MutableSharedFlow<Int>()

        offsetFlow
            .onStart { emit(0) }
            .toPivotedTileInputs(pivotRequest)
            .toTiledList(invalidationSignal.listTiler())
            .transformWhile { latestItems ->
                // Wait for items to finish loading
                if (latestItems.size != MAX_LOAD_SIZE) return@transformWhile true

                val firstPage = latestItems.queryAt(0)
                val lastPage = latestItems.queryAt(latestItems.lastIndex)
                val isOnLastPage = lastPage >= pageToScrollTo

                // Trigger loading more
                if (!isOnLastPage) {
                    offsetFlow.emit(lastPage)
                    return@transformWhile true
                }

                // Currently at the page scrolled to. If there's nothing to invalidate, complete
                if (pagesToInvalidate.isEmpty()) return@transformWhile false

                // ** Invalidation code ** //

                // Outside the visible range, nothing to invalidate so terminate
                if (pagesToInvalidate.first > lastPage) return@transformWhile false
                if (pagesToInvalidate.last < firstPage) return@transformWhile false

                println(latestItems.queries())
                // Find an item from the page that was invalidated
                val invalidatedItem = latestItems.lastInvalidatedItem()

                val isFinished = invalidatedItem != null
                        && invalidatedItem.lastInvalidatedPage >= pagesToInvalidate.last

                emit(latestItems)
                !isFinished
            }
            .collect {
                // Invalidate
                val invalidatedItem = it.lastInvalidatedItem()
                val canIncrementAndInvalidate = invalidatedItem == null
                        || invalidatedItem.lastInvalidatedPage == lastInvalidatedPage

                if (canIncrementAndInvalidate && ++lastInvalidatedPage <= pagesToInvalidate.last) {
                    invalidationSignal.emit(lastInvalidatedPage)
                }
            }
    }

    private fun TiledList<Int, Item>.lastInvalidatedItem(): Item? {
        for (item in this) {
            if (item.lastInvalidatedPage == lastInvalidatedPage) return item
        }
        return null
    }
}

private fun Flow<Int>.listTiler() = listTiler(
    limiter = Tile.Limiter(
        maxQueries = NUM_PAGES_IN_MEMORY,
    ),
    order = Tile.Order.PivotSorted(
        query = 0,
        comparator = Int::compareTo,
    ),
    fetcher = { page ->
        val range = rangeFor(page)
        transform { invalidatedPage ->
            if (invalidatedPage == page) emit(
                range.map {
                    Item(
                        index = it,
                        lastInvalidatedPage = invalidatedPage
                    )
                }
            )
        }
            .onStart {
                emit(range.map {
                    Item(
                        index = it,
                        lastInvalidatedPage = Int.MIN_VALUE
                    )
                })
            }
    },
)