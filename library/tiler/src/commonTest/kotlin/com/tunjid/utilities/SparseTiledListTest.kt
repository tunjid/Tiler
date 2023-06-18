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

import com.tunjid.tiler.utilities.SparseTiledList
import kotlin.test.Test
import kotlin.test.assertEquals

class SparseTiledListTest {

    @Test
    fun can_append_for_same_query() {
        val sparseTiledList = SparseTiledList<Int, Int>()
        sparseTiledList.add(
            query = 1,
            item = 1
        )
        sparseTiledList.add(
            query = 1,
            item = 2
        )
        sparseTiledList.add(
            query = 1,
            item = 5
        )
        sparseTiledList.add(
            query = 1,
            item = 10
        )
        assertEquals(
            expected = listOf(
                1 to 1,
                1 to 2,
                1 to 5,
                1 to 10,
            ),
            actual = sparseTiledList.toPairedList()
        )
    }

    @Test
    fun can_append_in_bulk_for_same_query() {
        val sparseTiledList = SparseTiledList<Int, Int>()
        sparseTiledList.addAll(
            query = 1,
            items = listOf(1, 2, 3)
        )
        sparseTiledList.addAll(
            query = 1,
            items = listOf(5, 8, 9)
        )
        sparseTiledList.add(
            query = 1,
            item = 5
        )
        sparseTiledList.add(
            query = 1,
            item = 10
        )
        assertEquals(
            expected = listOf(
                1 to 1,
                1 to 2,
                1 to 3,
                1 to 5,
                1 to 8,
                1 to 9,
                1 to 5,
                1 to 10,
            ),
            actual = sparseTiledList.toPairedList()
        )
    }

    @Test
    fun can_insert_at_index_for_same_query() {
        val sparseTiledList = SparseTiledList<Int, Int>()
        sparseTiledList.add(
            query = 1,
            item = 1
        )
        sparseTiledList.add(
            query = 1,
            item = 3
        )
        sparseTiledList.add(
            query = 1,
            item = 5
        )
        sparseTiledList.add(
            query = 1,
            item = 6
        )
        sparseTiledList.add(
            index = 2,
            query = 1,
            item = 4
        )
        assertEquals(
            expected = listOf(
                1 to 1,
                1 to 3,
                1 to 4,
                1 to 5,
                1 to 6,
            ),
            actual = sparseTiledList.toPairedList()
        )
    }

    @Test
    fun can_append_for_different_query() {
        val sparseTiledList = SparseTiledList<Int, Int>()
        sparseTiledList.add(
            query = 1,
            item = 1
        )
        sparseTiledList.add(
            query = 1,
            item = 2
        )
        sparseTiledList.add(
            query = 3,
            item = 5
        )
        sparseTiledList.add(
            query = 7,
            item = 10
        )
        sparseTiledList.add(
            query = 1,
            item = 18
        )

        assertEquals(
            expected = listOf(
                1 to 1,
                1 to 2,
                3 to 5,
                7 to 10,
                1 to 18,
            ),
            actual = sparseTiledList.toPairedList()
        )
    }

    @Test
    fun can_insert_at_index_for_different_query() {
        val sparseTiledList = SparseTiledList<Int, Int>()
        sparseTiledList.add(
            query = 1,
            item = 1
        )
        sparseTiledList.add(
            query = 1,
            item = 2
        )
        sparseTiledList.add(
            query = 3,
            item = 5
        )
        sparseTiledList.add(
            query = 7,
            item = 10
        )
        sparseTiledList.add(
            index = 2,
            query = 9,
            item = 4
        )
        sparseTiledList.add(
            index = 3,
            query = 17,
            item = 27
        )
        assertEquals(
            expected = listOf(
                1 to 1,
                1 to 2,
                9 to 4,
                17 to 27,
                3 to 5,
                7 to 10,
            ),
            actual = sparseTiledList.toPairedList()
        )
    }

    @Test
    fun can_delete_at_index() {
        val sparseTiledList = SparseTiledList<Int, Int>()
        sparseTiledList.add(
            query = 1,
            item = 1
        )
        sparseTiledList.add(
            query = 1,
            item = 2
        )
        sparseTiledList.add(
            query = 3,
            item = 5
        )
        sparseTiledList.add(
            query = 7,
            item = 10
        )
        sparseTiledList.add(
            query = 1,
            item = 18
        )
        assertEquals(
            expected = listOf(
                1 to 1,
                1 to 2,
                3 to 5,
                7 to 10,
                1 to 18,
            ),
            actual = sparseTiledList.toPairedList()
        )

        sparseTiledList.remove(2)
        println(sparseTiledList.queryRanges)
        assertEquals(
            expected = listOf(
                1 to 1,
                1 to 2,
                7 to 10,
                1 to 18,
            ),
            actual = sparseTiledList.toPairedList()
        )

        sparseTiledList.remove(1)
        assertEquals(
            expected = listOf(
                1 to 1,
                7 to 10,
                1 to 18,
            ),
            actual = sparseTiledList.toPairedList()
        )

        sparseTiledList.remove(2)
        assertEquals(
            expected = listOf(
                1 to 1,
                7 to 10,
            ),
            actual = sparseTiledList.toPairedList()
        )
    }
}

private fun <Query, Item> SparseTiledList<Query, Item>.toPairedList() =
    mapIndexed { index, item ->
        queryAt(index) to item
    }