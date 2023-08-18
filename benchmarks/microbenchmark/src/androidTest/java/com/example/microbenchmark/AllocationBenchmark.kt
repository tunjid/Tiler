package com.example.microbenchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.benchmarks.data.PAGE_TO_SCROLL_TO
import com.example.benchmarks.data.PagingBenchmark
import com.example.benchmarks.data.TilingBenchmark
import com.example.benchmarks.data.emptyPages
import com.example.benchmarks.data.offScreenPages
import com.example.benchmarks.data.onScreenPages
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * This is an example startup benchmark.
 *
 */
@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AllocationBenchmark {
    @get:Rule
    val benchmarkRule = BenchmarkRule()

    @Test
    fun a_pagingScroll() = benchmarkRule.measureRepeated {
        runBlocking {
            PagingBenchmark(
                pageToScrollTo = PAGE_TO_SCROLL_TO,
                pagesToInvalidate = emptyPages
            ).benchmark()
        }
    }

    @Test
    fun b_tilingScroll() = benchmarkRule.measureRepeated {
        runBlocking {
            TilingBenchmark(
                pageToScrollTo = PAGE_TO_SCROLL_TO,
                pagesToInvalidate = emptyPages
            ).benchmark()
        }
    }

    @Test
    fun c_pagingInvalidationOffScreen() = benchmarkRule.measureRepeated {
        runBlocking {
            PagingBenchmark(
                pageToScrollTo = PAGE_TO_SCROLL_TO,
                pagesToInvalidate = offScreenPages
            ).benchmark()
        }
    }

    @Test
    fun d_tilingInvalidationOffScreen() = benchmarkRule.measureRepeated {
        runBlocking {
            TilingBenchmark(
                pageToScrollTo = PAGE_TO_SCROLL_TO,
                pagesToInvalidate = offScreenPages
            ).benchmark()
        }
    }

    @Test
    fun e_pagingInvalidationOnScreen() = benchmarkRule.measureRepeated {
        runBlocking {
            PagingBenchmark(
                pageToScrollTo = PAGE_TO_SCROLL_TO,
                pagesToInvalidate = onScreenPages
            ).benchmark()
        }
    }

    @Test
    fun f_tilingInvalidationOnScreen() = benchmarkRule.measureRepeated {
        runBlocking {
            TilingBenchmark(
                pageToScrollTo = PAGE_TO_SCROLL_TO,
                pagesToInvalidate = onScreenPages
            ).benchmark()
        }
    }
}
