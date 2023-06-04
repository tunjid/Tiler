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

package com.tunjid.utilities

import com.tunjid.tiler.utilities.IntArrayList
import com.tunjid.tiler.utilities.toList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IntArrayListTest {

    @Test
    fun can_add() {
        val intList = IntArrayList()
        intList.add(1)
        assertEquals(
            expected = 1,
            actual = intList.size
        )

        intList.add(2)
        assertEquals(
            expected = listOf(1, 2),
            actual = intList.toList()
        )

        assertEquals(
            expected = 2,
            actual = intList.size
        )
    }

    @Test
    fun can_add_at_index() {
        val intList = IntArrayList()

        intList.add(1)
        assertEquals(
            expected = 1,
            actual = intList.size
        )

        intList.add(2)
        assertEquals(
            expected = 2,
            actual = intList.size
        )

        intList.add(
            index = 1,
            element = 3
        )
        assertEquals(
            expected = 3,
            actual = intList.size
        )
        assertEquals(
            expected = listOf(1, 3, 2),
            actual = intList.toList()
        )

        intList.add(
            index = 0,
            element = 9
        )
        assertEquals(
            expected = 4,
            actual = intList.size
        )
        assertEquals(
            expected = listOf(9, 1, 3, 2),
            actual = intList.toList()
        )
    }

    @Test
    fun can_set_at_index() {
        val intList = IntArrayList()
        intList.add(1)
        intList.add(2)
        intList.add(3)
        intList.add(4)

        assertEquals(
            expected = listOf(1, 2, 3, 4),
            actual = intList.toList()
        )

        for(i in 0..intList.lastIndex) {
            intList[i] = intList[i] + 5
        }

        assertEquals(
            expected = listOf(6, 7, 8, 9),
            actual = intList.toList()
        )
    }

    @Test
    fun can_resize() {
        val intList = IntArrayList(1)

        intList.add(1)
        assertEquals(
            expected = 1,
            actual = intList.size
        )

        intList.add(2)
        assertEquals(
            expected = 2,
            actual = intList.size
        )

        assertEquals(
            expected = listOf(1, 2),
            actual = intList.toList()
        )
    }

    @Test
    fun can_removeAt() {
        val intList = IntArrayList(1)

        intList.add(1)
        intList.add(2)
        intList.add(3)
        intList.add(4)
        intList.add(5)

        assertEquals(
            expected = listOf(1, 2, 3, 4, 5),
            actual = intList.toList()
        )

        intList.removeAt(0)
        assertEquals(
            expected = listOf(2, 3, 4, 5),
            actual = intList.toList()
        )

        intList.removeAt(2)
        assertEquals(
            expected = listOf(2, 3, 5),
            actual = intList.toList()
        )

        intList.removeAt(2)
        assertEquals(
            expected = listOf(2, 3),
            actual = intList.toList()
        )
    }

    @Test
    fun cannot_removeAt_out_of_bounds() {
        val intList = IntArrayList(1)

        intList.add(1)
        intList.add(2)
        intList.add(3)

        assertFailsWith<IndexOutOfBoundsException> {
            intList.removeAt(7)
        }
    }
}