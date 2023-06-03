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

class IntArrayList(
    initialSize: Int = 10
) {

    private var data = IntArray(initialSize)
    var size = 0
        private set

    val lastIndex get() = size - 1

    fun add(element: Int) {
        if (size == data.size) {
            val newData = IntArray(data.size * 2)
            data.copyInto(destination = newData)
            data = newData
        }
        data[size++] = element
    }

    fun add(index: Int, element: Int) {
        when (size) {
            // Grow the backing array
            data.size -> {
                val newData = IntArray(data.size * 2)
                data.copyInto(
                    destination = newData,
                    startIndex = 0,
                    endIndex = index,
                )
                data.copyInto(
                    destination = newData,
                    destinationOffset = index + 1,
                    startIndex = index,
                )
                newData[index] = element
                data = newData
            }
            else -> {
                data.copyInto(
                    destination = data,
                    destinationOffset = index + 1,
                    startIndex = index,
                    endIndex = size,
                )
                data[index] = element
            }
        }
        ++size
    }

    operator fun get(index: Int): Int {
        if (index > lastIndex) throw IndexOutOfBoundsException(
            "Attempted to read $index in IntArrayList of size $size"
        )
        return data[index]
    }

    fun isEmpty() = size == 0

    fun clear() {
        size = 0
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is IntArrayList) return false

        return orderedEquals(this, other)
    }

    /**
     * Returns the hash code value for this list.
     */
    override fun hashCode(): Int = orderedHashCode(this)

    private fun orderedEquals(c: IntArrayList, other: IntArrayList): Boolean {
        if (c.size != other.size) return false

        for (i in 0..lastIndex) {
            val elem = this[i]
            val elemOther = other[i]
            if (elem != elemOther) {
                return false
            }
        }
        return true
    }

    private fun orderedHashCode(c: IntArrayList): Int {
        var hashCode = 1
        for (i in 0..lastIndex) {
            val elem = c[i]
            hashCode = 31 * hashCode + elem.hashCode()
        }
        return hashCode
    }
}

fun IntArrayList.toList(): List<Int> = buildList build@{
    for (i in 0..this@toList.lastIndex) {
        this@build.add(this@toList[i])
    }
}