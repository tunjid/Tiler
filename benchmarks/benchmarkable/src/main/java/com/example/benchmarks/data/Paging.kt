package com.example.benchmarks.data

import android.annotation.SuppressLint
import androidx.paging.CombinedLoadStates
import androidx.paging.DifferCallback
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.NullPaddedList
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingDataDiffer
import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch

private val pagingConfig = PagingConfig(
    pageSize = ITEMS_PER_PAGE,
    prefetchDistance = ITEMS_PER_PAGE,
    initialLoadSize = MAX_LOAD_SIZE,
    maxSize = MAX_LOAD_SIZE,
)

class PagingBenchmark(
    private val pageToScrollTo: Int,
    private val pagesToInvalidate: IntRange
) : Benchmarked {

    private var lastInvalidatedPage: Int = pagesToInvalidate.first + 1

    @SuppressLint("RestrictedApi")
    override suspend fun benchmark() = coroutineScope {
        val differ = pagingDataDiffer()
        val collectJob = launch {
            // Will never complete as submitData suspends indefinitely
            Pager(
                config = pagingConfig,
                pagingSourceFactory = {
                    itemPagingSource(lastInvalidatedPage)
                }
            )
                .flow
                .collectLatest(differ::collectFrom)
        }

        differ
            // When items are delivered, read them
            .onPagesUpdatedFlow
            .transformWhile {
                if (!differ.loadStateFlow.value.isIdle()) return@transformWhile true
                val latestItems = differ.snapshot().items

                val lastItem = latestItems.last()
                val lastPage = pageFor(lastItem)
                val isOnLastPage = lastPage >= pageToScrollTo

                // Trigger loading more
                if (!isOnLastPage) {
                    differ[lastItem.index]
                    return@transformWhile true
                }

                // Currently at the page scrolled to. If there's nothing to invalidate, complete
                if (pagesToInvalidate.isEmpty()) return@transformWhile false

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
                    differ.refresh()
                }
            }

        collectJob.cancel()
    }

    private fun List<Item>.lastInvalidatedItem(): Item? {
        for (item in this) {
            if (item.lastInvalidatedPage == lastInvalidatedPage) return item
        }
        return null
    }
}

@SuppressLint("RestrictedApi")
private fun pagingDataDiffer() = object : PagingDataDiffer<Item>(
    differCallback = object : DifferCallback {
        override fun onChanged(position: Int, count: Int) = Unit

        override fun onInserted(position: Int, count: Int) = Unit

        override fun onRemoved(position: Int, count: Int) = Unit
    }
) {
    override suspend fun presentNewList(
        previousList: NullPaddedList<Item>,
        newList: NullPaddedList<Item>,
        lastAccessedIndex: Int,
        onListPresentable: () -> Unit
    ): Int {
        onListPresentable()
        return lastAccessedIndex
    }
}

private fun itemPagingSource(
    lastInvalidatedPage: Int
): PagingSource<Int, Item> = object : PagingSource<Int, Item>() {

    // Only 3 pages are loaded at once,
    // Start loading again from the first page in the 3 page range
    override fun getRefreshKey(
        state: PagingState<Int, Item>
    ): Int = state.pages
        .firstOrNull()
        ?.data
        ?.firstOrNull()
        ?.let(::pageFor)
        ?: 0

    override suspend fun load(
        params: LoadParams<Int>
    ): LoadResult<Int, Item> {
        val page = params.key ?: 0
        val pageCount = params.loadSize / ITEMS_PER_PAGE
        val items = rangeFor(page, numberOfPages = pageCount).map { index ->
            Item(
                index = index,
                lastInvalidatedPage = lastInvalidatedPage
            )
        }
        return LoadResult.Page(
            data = items,
            nextKey = pageFor(items.last()) + 1,
            prevKey = if (page <= 0) null else page - 1,
        )
    }
}

private fun CombinedLoadStates?.isIdle(): Boolean {
    if (this == null) return false
    return source.isIdle() && mediator?.isIdle() ?: true
}

private fun LoadStates.isIdle(): Boolean {
    return refresh is LoadState.NotLoading && append is LoadState.NotLoading &&
            prepend is LoadState.NotLoading
}

