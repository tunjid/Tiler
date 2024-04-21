package com.example.benchmarks.data

const val PAGE_TO_SCROLL_TO = 20
const val ITEMS_PER_PAGE = 100

internal const val NUM_PAGES_IN_MEMORY = 3
internal const val MAX_LOAD_SIZE = NUM_PAGES_IN_MEMORY * ITEMS_PER_PAGE

@Suppress("EmptyRange")
val emptyPages = 0 until 0
val onScreenPages = (PAGE_TO_SCROLL_TO - NUM_PAGES_IN_MEMORY)..PAGE_TO_SCROLL_TO
val offScreenPages = (onScreenPages.first - NUM_PAGES_IN_MEMORY) until onScreenPages.first

sealed interface Benchmarked {
    suspend fun benchmark()
}

data class Item(
  val  index: Int,
  val  lastInvalidatedPage: Int = Int.MIN_VALUE
)

fun rangeFor(startPage: Int, numberOfPages: Int = 1): IntRange {
    val offset = startPage * ITEMS_PER_PAGE
    val next = offset + (ITEMS_PER_PAGE * numberOfPages)

    return offset until next
}

fun pageFor(item: Item): Int {
    val diff = item.index % ITEMS_PER_PAGE
    val firstItemIndex = item.index - diff
    return firstItemIndex / ITEMS_PER_PAGE
}
