package com.example.benchmarks

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.benchmarks.data.PAGE_TO_SCROLL_TO
import com.example.benchmarks.data.PagingBenchmark
import com.example.benchmarks.data.TilingBenchmark
import com.example.benchmarks.data.emptyPages
import com.example.benchmarks.data.offScreenPages
import com.example.benchmarks.data.onScreenPages
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class BenchmarkRunTest {
    @Test
    fun benchmark() = runTest {
        repeat(1) {
            listOf(
                TilingBenchmark(
                    pageToScrollTo = PAGE_TO_SCROLL_TO,
                    pagesToInvalidate = emptyPages,
                ),
                TilingBenchmark(
                    pageToScrollTo = PAGE_TO_SCROLL_TO,
                    pagesToInvalidate = offScreenPages,
                ),
                TilingBenchmark(
                    pageToScrollTo = PAGE_TO_SCROLL_TO,
                    pagesToInvalidate = onScreenPages,
                ),
                PagingBenchmark(
                    pageToScrollTo = PAGE_TO_SCROLL_TO,
                    pagesToInvalidate = emptyPages,
                ),
                PagingBenchmark(
                    pageToScrollTo = PAGE_TO_SCROLL_TO,
                    pagesToInvalidate = offScreenPages,
                ),
                PagingBenchmark(
                    pageToScrollTo = PAGE_TO_SCROLL_TO,
                    pagesToInvalidate = onScreenPages,
                ),
            ).forEach { it.benchmark() }
        }

    }
}
