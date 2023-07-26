package com.example.benchmarks.data

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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import kotlin.math.max

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

    private var lastInvalidatedPage: Int = Int.MIN_VALUE

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
                differ.loadStateFlow
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

                emit(latestItems)

                val isFinished = pagesToInvalidate.isEmpty() ||
                        lastItem.lastInvalidatedPage >= pagesToInvalidate.last

                !isFinished
            }
            .collect {
                // Account for start
                lastInvalidatedPage = max(
                    a = lastInvalidatedPage,
                    b = pagesToInvalidate.first - 1
                )
                if (lastInvalidatedPage < pagesToInvalidate.last) {
                    ++lastInvalidatedPage
                    differ.refresh()
                }
            }

        collectJob.cancel()
    }
}

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

abstract class M<T> {
    abstract val initial: T

    private var seed: T = initial

    val flow = flowOf(seed)
        .onEach { seed = it }
}
