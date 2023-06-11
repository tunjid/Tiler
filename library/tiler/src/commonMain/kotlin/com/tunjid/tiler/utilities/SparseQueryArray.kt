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

internal fun QueryRange(
    start: Int,
    end: Int,
) = QueryRange(
    start.toLong().shl(32) or (end.toLong() and 0xFFFFFFFF)
)

@JvmInline
internal value class QueryRange(
    val packedValue: Long
) {

    val start: Int get() = packedValue.shr(32).toInt()

    val end: Int get() = packedValue.and(0xFFFFFFFF).toInt()

    override fun toString(): String = "QueryRange(start=$start, end=$end)"
}

internal class SparseQueryArray<Query> @JvmOverloads constructor(
    initialCapacity: Int = 10
) {
    private var garbage = false
    private var keys: LongArray
    private var values: Array<Any?>
    private var size: Int
    /**
     * Creates a new SparseArray containing no mappings that will not
     * require any additional memory allocation to store the specified
     * number of mappings.  If you supply an initial capacity of 0, the
     * sparse array will be initialized with a light-weight representation
     * not requiring any additional array allocations.
     */
    /**
     * Creates a new SparseArray containing no mappings.
     */
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

    fun appendQuery(query: Query, count: Int) {
        val lastIndex = if (size == 0) -1 else QueryRange(keys[size - 1]).end - 1

        when (size) {
            0 -> set(
                key = QueryRange(
                    start = 0,
                    end = count
                ),
                value = query
            )
            else -> updateExistingQueryOr(
                index = lastIndex,
                query = query,
                count = count
            ) {
                // Append at the end
                this[
                    QueryRange(
                        start = lastIndex + 1,
                        end = lastIndex + count + 1
                    )
                ] = query
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
        if (i < 0) throw IllegalArgumentException("Index $index is not present")

        val newRange = QueryRange(
            start = index,
            end = index + count
        )
        // Shift items to accommodate new entry
        val gap = newRange.end - newRange.start

        for (j in i until size) {
            val existing = QueryRange(keys[j])
            keys[j] = QueryRange(
                start = existing.start + gap,
                end = existing.end + gap,
            ).packedValue
        }
        set(
            key = newRange,
            value = query
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
        if (i < 0) throw IllegalArgumentException("Index $index is not present")

        val existing = values[i]
        if (query != existing) return block(i)

        val existingRange = QueryRange(keys[i])
        val newRange = QueryRange(
            start = existingRange.start,
            end = existingRange.end + count
        )
        keys[i] = newRange.packedValue

        if (existingRange.end != newRange.end) {
            val gap = newRange.end - existingRange.end

            for (j in i + 1 until size) {
                val range = QueryRange(keys[j])
                keys[j] = QueryRange(
                    start = range.start + gap,
                    end = range.end + gap,
                ).packedValue
            }
        }
    }

    fun deleteAt(
        index: Int,
    ) {
        val i = keys.indexBinarySearch(
            index = index,
            size = size
        )
        if (i < 0) throw IllegalArgumentException("Index $index is not present")

        val existingRange = QueryRange(keys[i])
        val newRange = QueryRange(
            start = existingRange.start,
            end = existingRange.end - 1
        )
        if (newRange.start == newRange.end) {
            values[i] = DELETED
            gc()
        }

        val gap = -1

        for (j in i until size) {
            val existing = QueryRange(keys[j])
            keys[j] = QueryRange(
                start = existing.start + gap,
                end = existing.end + gap,
            ).packedValue
        }
    }

    /**
     * Returns true if the key exists in the array. This is equivalent to
     * [.indexOfKey] >= 0.
     *
     * @param key Potential key in the mapping
     * @return true if the key is defined in the mapping
     */
    operator fun contains(key: Int): Boolean {
        return indexOfKey(key) >= 0
    }

    /**
     * Gets the Object mapped from the specified key, or the specified Object
     * if no such mapping has been made.
     */
    /**
     * Gets the Object mapped from the specified key, or `null`
     * if no such mapping has been made.
     */
    fun find(key: Int): Query {
        val i: Int = keys.indexBinarySearch(
            index = key,
            size = size
        )
        if (i < 0 || values[i] === DELETED) throw IllegalArgumentException(
            "Unknown key"
        )
        @Suppress("UNCHECKED_CAST")
        return values[i] as Query
    }

    /**
     * Adds a mapping from the specified key to the specified value,
     * replacing the previous mapping from the specified key if there
     * was one.
     */
    operator fun set(key: QueryRange, value: Query) {
        var i: Int = keys.indexBinarySearch(
            index = key.start,
            size = size
        )
        if (i >= 0) return values.set(
            index = i,
            value = value
        )

        i = i.inv()
        if (i < size && values[i] === DELETED) {
            keys[i] = key.packedValue
            values[i] = value
            return
        }
        if (garbage && size >= keys.size) {
            gc()

            // Search again because indices may have changed.
            i = keys.indexBinarySearch(
                index = key.start,
                size = size
            ).inv()
        }
        keys = keys.insert(
            currentSize = size,
            index = i,
            element = key.packedValue
        )
        values = values.insert(
            currentSize = size,
            index = i,
            element = value
        )
        size++
    }

    private fun gc() {
        // Log.e("SparseArray", "gc start with " + mSize);
        val n = size
        var o = 0
        val keys = keys
        val values = values
        for (i in 0 until n) {
            val value = values[i]
            if (value !== DELETED) {
                if (i != o) {
                    keys[o] = keys[i]
                    values[o] = value
                    values[i] = null
                }
                o++
            }
        }
        garbage = false
        size = o

        // Log.e("SparseArray", "gc end with " + mSize);
    }

    /**
     * Returns the number of key-value mappings that this SparseArray
     * currently stores.
     */
    fun size(): Int {
        if (garbage) {
            gc()
        }
        return size
    }

    /**
     * Returns the index for which [.keyAt] would return the
     * specified key, or a negative number if the specified
     * key is not mapped.
     */
    private fun indexOfKey(key: Int): Int {
        if (garbage) gc()
        return keys.indexBinarySearch(index = key, size = size)
    }


    /**
     * Puts a key/value pair into the array, optimizing for the case where
     * the key is greater than all existing keys in the array.
     */
//    fun append(key: Int, value: Query) {
//        if (size != 0 && key <= keys[size - 1]) {
//            put(key, value)
//            return
//        }
//        if (garbage && size >= keys.size) {
//            gc()
//        }
//        keys = keys.append(size, key)
//        values = values.append(size, value)
//        size++
//    }

    private inline fun <T> checkSize(index: Int, block: () -> T): T {
        if (index >= size) {
            // The array might be slightly bigger than mSize, in which case, indexing won't fail.
            // Check if exception should be thrown outside of the critical path.
            throw IndexOutOfBoundsException(
                "Unable to look up index $index bc it is larger than the size ($size) of this SparseArray"
            )
        }
        return block()
    }

    override fun toString(): String =
        keys
            .take(size)
            .map(::QueryRange)
            .zip(values)
            .joinToString(
                separator = "\n",
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
    element: Any
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
