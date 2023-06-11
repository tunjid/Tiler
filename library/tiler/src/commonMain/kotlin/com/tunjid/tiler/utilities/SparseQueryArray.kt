/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tunjid.tiler.utilities

import kotlin.jvm.JvmInline
import kotlin.jvm.JvmOverloads


/**
 * Describes a range of indices for which a given query may be found
 */
@JvmInline
internal value class QueryRange(
    val packedValue: Long
) {

    val start: Int get() = packedValue.shr(32).toInt()

    val end: Int get() = packedValue.and(0xFFFFFFFF).toInt()

    override fun toString(): String = "QueryRange(start=$start,end=$end)"
}

internal fun QueryRange(
    start: Int,
    end: Int,
) = QueryRange(
    start.toLong().shl(32) or (end.toLong() and 0xFFFFFFFF)
)

/**
 * Uses the basic tenets of a
 * [SparseArray]("https://developer.android.com/reference/android/util/SparseArray")
 * To map a range of indices to a [Query]. This allows for the association of queries
 * to indices without allocating an object as seen in [MutablePairedTiledList].
 */
internal class SparseQueryArray<Query> @JvmOverloads constructor(
    initialCapacity: Int = 10
) {
    private var garbage = false
    private var keys: LongArray
    private var values: Array<Any?>
    private var size: Int

    init {
        if (initialCapacity == 0) {
            keys = longArrayOf()
            values = emptyArray()
        } else {
            values = arrayOf(initialCapacity)
            keys = LongArray(values.size)
        }
        size = 0
    }

    /**
     * Gets the Object mapped from the specified key, or the specified Object
     * if no such mapping has been made.
     */
    /**
     * Gets the Object mapped from the specified key, or `null`
     * if no such mapping has been made.
     */
    fun find(index: Int): Query {
        val i: Int = keys.indexBinarySearch(
            index = index,
            size = size
        )
        if (i < 0 || values[i] === DELETED) throw IllegalArgumentException(
            "Index $index is not present in SparseQueryArray of size $size and contents $this"
        )
        @Suppress("UNCHECKED_CAST")
        return values[i] as Query
    }

    fun appendQuery(
        query: Query,
        count: Int
    ) = when (size) {
        0 -> set(
            range = QueryRange(start = 0, end = count),
            value = query
        )

        else -> {
            // Append at the end
            val lastIndex = QueryRange(keys[size - 1]).end - 1
            updateExistingQueryOr(
                index = lastIndex,
                query = query,
                count = count
            ) {
                // Append at the end
                append(
                    range = QueryRange(
                        start = lastIndex + 1,
                        end = lastIndex + count + 1
                    ),
                    value = query
                )
            }
        }
    }

    fun insertQuery(
        index: Int,
        query: Query,
        count: Int
    ) = updateExistingQueryOr(
        index = index,
        query = query,
        count = count
    ) { i ->
        if (i < 0) throw IllegalArgumentException(
            "Index $index is not present in SparseQueryArray of size $size and contents $this"
        )

        val newRange = QueryRange(
            start = index,
            end = index + count
        )
        // Shift items to accommodate new entry
        shiftRangesBy(
            startIndex = i,
            gap = newRange.end - newRange.start
        )
        set(
            range = newRange,
            value = query
        )
    }

    fun deleteAt(
        index: Int,
    ) {
        val i = keys.indexBinarySearch(
            index = index,
            size = size
        )
        if (i < 0) throw IllegalArgumentException(
            "Index $index is not present in SparseQueryArray of size $size and contents $this"
        )
        val existingRange = QueryRange(keys[i])
        val newRange = QueryRange(
            start = existingRange.start,
            end = existingRange.end - 1
        )
        // This range is now invalid, remove it.
        if (newRange.start == newRange.end) {
            values[i] = DELETED
            gc()
        }
        shiftRangesBy(
            startIndex = i,
            gap = -1
        )
    }

    private inline fun updateExistingQueryOr(
        index: Int,
        query: Query,
        count: Int,
        block: (Int) -> Unit
    ) {
        val i = keys.indexBinarySearch(
            index = index,
            size = size
        )
        if (i < 0) throw IllegalArgumentException(
            "Index $index is not present in SparseQueryArray of size $size and contents $this"
        )
        val existing = values[i]
        if (query != existing) return block(i)

        val existingRange = QueryRange(keys[i])
        val newRange = QueryRange(
            start = existingRange.start,
            end = existingRange.end + count
        )
        keys[i] = newRange.packedValue

        if (existingRange.end != newRange.end) {
            shiftRangesBy(
                startIndex = i + 1,
                gap = newRange.end - existingRange.end
            )
        }
    }

    private fun shiftRangesBy(startIndex: Int, gap: Int) {
        for (i in startIndex until size) {
            val existing = QueryRange(keys[i])
            keys[i] = QueryRange(
                start = existing.start + gap,
                end = existing.end + gap,
            ).packedValue
        }
    }

    operator fun contains(key: Int): Boolean {
        return indexOfKey(key) >= 0
    }

    private fun set(range: QueryRange, value: Query) {
        var i: Int = keys.indexBinarySearch(
            index = range.start,
            size = size
        )
        if (i >= 0) return values.set(
            index = i,
            value = value
        )

        i = i.inv()
        if (i < size && values[i] === DELETED) {
            keys[i] = range.packedValue
            values[i] = value
            return
        }
        if (garbage && size >= keys.size) {
            gc()

            // Search again because indices may have changed.
            i = keys.indexBinarySearch(
                index = range.start,
                size = size
            ).inv()
        }
        keys = keys.insert(
            currentSize = size,
            index = i,
            element = range.packedValue
        )
        values = values.insert(
            currentSize = size,
            index = i,
            element = value
        )
        size++
    }

    private fun append(range: QueryRange, value: Query) {
        if (size != 0 && range.end <= QueryRange(keys[size - 1]).start) {
            set(range, value)
            return
        }
        if (garbage && size >= keys.size) {
            gc()
        }
        keys = keys.append(
            currentSize = size,
            element = range.packedValue,
        )
        values = values.append(
            currentSize = size,
            element = value,
        )
        size++
    }

    private fun gc() {
        val count = size
        var lag = 0
        val keys = keys
        val values = values
        for (lead in 0 until count) {
            val value = values[lead]
            if (value == DELETED) continue
            if (lead != lag) {
                keys[lag] = keys[lead]
                values[lag] = value
                values[lead] = null
            }
            lag++
        }
        garbage = false
        size = lag
    }

    private fun indexOfKey(key: Int): Int {
        if (garbage) gc()
        return keys.indexBinarySearch(index = key, size = size)
    }

    override fun toString(): String =
        keys
            .take(size)
            .map(::QueryRange)
            .zip(values)
            .joinToString(
                prefix = "\n",
                transform = { (key, value) -> "$key - $value" }
            )

    companion object {
        private val DELETED = Any()
    }
}

private fun LongArray.indexBinarySearch(
    index: Int,
    size: Int,
): Int {
    var low = 0
    var high = size - 1
    while (low <= high) {
        val mid = (low + high).ushr(1)
        val queryRange = QueryRange(this[mid])

        val comparison = when (index) {
            in Int.MIN_VALUE until queryRange.start -> 1
            in queryRange.start until queryRange.end -> 0
            else -> -1
        }

        if (comparison < 0) low = mid + 1
        else if (comparison > 0) high = mid - 1
        else return mid // Found
    }

    return low.inv()
}


/**
 * Given the current size of an array, returns an ideal size to which the array should grow.
 * This is typically double the given size, but should not be relied upon to do so in the
 * future.
 */
private fun growSize(currentSize: Int): Int {
    return if (currentSize <= 4) 8 else currentSize * 2
}


private inline fun LongArray.append(
    currentSize: Int,
    element: Long
): LongArray = append(
    containerSize = LongArray::size,
    containerFactory = ::LongArray,
    containerSet = LongArray::set,
    containerCopy = LongArray::copyInto,
    currentSize = currentSize,
    element = element
)


private inline fun Array<Any?>.append(
    currentSize: Int,
    element: Any?
): Array<Any?> = append(
    containerSize = Array<Any?>::size,
    containerFactory = ::arrayOfNulls,
    containerSet = Array<Any?>::set,
    containerCopy = Array<Any?>::copyInto,
    currentSize = currentSize,
    element = element
)


private inline fun <Container, Element> Container.append(
    containerSize: Container.() -> Int,
    containerFactory: (Int) -> Container,
    containerSet: Container.(Int, Element) -> Unit,
    containerCopy: (Container, Container) -> Unit,
    currentSize: Int,
    element: Element
): Container {
    var container = this
    if (currentSize + 1 > containerSize(this)) {
        val newContainer = containerFactory(growSize(currentSize))
        containerCopy(container, newContainer)
        container = newContainer
    }
    containerSet(currentSize, element)
    return container
}

private inline fun LongArray.insert(
    currentSize: Int,
    index: Int,
    element: Long
): LongArray = insert(
    containerSize = LongArray::size,
    containerFactory = ::LongArray,
    containerSet = LongArray::set,
    containerCopy = LongArray::copyInto,
    containerShift = { array, destinationOffset, startIndex, endIndex ->
        array.copyInto(
            destination = array,
            destinationOffset = destinationOffset,
            startIndex = startIndex,
            endIndex = endIndex
        )
    },
    currentSize = currentSize,
    index = index,
    element = element
)


private inline fun Array<Any?>.insert(
    currentSize: Int,
    index: Int,
    element: Any?
): Array<Any?> = insert(
    containerSize = Array<Any?>::size,
    containerFactory = ::arrayOfNulls,
    containerSet = Array<Any?>::set,
    containerCopy = Array<Any?>::copyInto,
    containerShift = { array, destinationOffset, startIndex, endIndex ->
        array.copyInto(
            destination = array,
            destinationOffset = destinationOffset,
            startIndex = startIndex,
            endIndex = endIndex
        )
    },
    currentSize = currentSize,
    index = index,
    element = element
)

private inline fun <Container, Element> Container.insert(
    containerSize: Container.() -> Int,
    containerFactory: (Int) -> Container,
    containerSet: Container.(Int, Element) -> Unit,
    containerCopy: (Container, Container) -> Unit,
    containerShift: (Container, destinationOffset: Int, startIndex: Int, endIndex: Int) -> Unit,
    currentSize: Int,
    index: Int,
    element: Element
): Container {
    val container = this
    if (currentSize + 1 <= containerSize(this)) {
        containerShift(container, index + 1, index, currentSize)
        containerSet(index, element)
        return container
    }

    val newContainer = containerFactory(growSize(currentSize))
    containerCopy(container, newContainer)
    newContainer.containerSet(index, element)

    return newContainer
}
