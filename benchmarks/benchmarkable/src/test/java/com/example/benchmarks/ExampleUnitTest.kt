package com.example.benchmarks

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.benchmarks.data.PAGE_TO_SCROLL_TO
import com.example.benchmarks.data.Item
import com.example.benchmarks.data.ItemDao
import com.example.benchmarks.data.PagingBenchmarks
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun useAppContext() = runTest {
//       TilingBenchmarks(Dao).scrollToIndex(ITEMS_TO_LOAD).run {
//           val at = indexOfFirst { it.id == ITEMS_TO_LOAD }
//           println("Tiling. id of $ITEMS_TO_LOAD at $at. Size: $size")
//           println(joinToString(separator = "\n"))
//       }

        PagingBenchmarks(Dao).scrollToIndex(PAGE_TO_SCROLL_TO).run {
            val at = indexOfFirst { it.id == PAGE_TO_SCROLL_TO }
            println("Tiling. id of $PAGE_TO_SCROLL_TO at $at. Size: $size")
            println(joinToString(separator = "\n"))
        }
    }
}

private object Dao : ItemDao {
    override fun itemPagingSource(): PagingSource<Int, Item> = object : PagingSource<Int, Item>() {
        override fun getRefreshKey(state: PagingState<Int, Item>): Int? =
            state.anchorPosition

        override suspend fun load(
            params: LoadParams<Int>
        ): LoadResult<Int, Item> = LoadResult.Page(
            data = params.key?.let { (it until (it + params.loadSize)).map(::Item) } ?: emptyList(),
            nextKey = params.key?.let { it + 1 },
            prevKey = params.key?.let {
                if (it <= 0) null
                else it - 1
            },
        )
    }

    override fun items(offset: Int, limit: Int): Flow<List<Item>> =
        flowOf((offset until (offset + limit)).map(::Item))


    override suspend fun upsertItems(items: List<Item>) {
        TODO("Not yet implemented")
    }

}