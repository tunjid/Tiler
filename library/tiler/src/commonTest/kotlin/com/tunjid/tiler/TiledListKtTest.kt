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

package com.tunjid.tiler

import com.tunjid.tiler.utilities.toList
import com.tunjid.utilities.queries
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals


class TiledListKtTest {

    @Test
    fun tiled_list_builder_works() {
        val tiledList = buildTiledList {
            addAll(1, 1.testRange().toList())
            addAll(3, 3.testRange().toList())
        }
        assertEquals(
            expected = 1.tiledTestRange() + 3.tiledTestRange(),
            actual = tiledList
        )
        assertEquals(
            expected = listOf(1,3),
            actual = tiledList.queries()
        )
    }

    @Test
    fun empty_tiled_list_works() {
        assertEquals(
            expected = tiledListOf<Int, Int>(),
            actual = emptyTiledList()
        )
    }

    @Test
    fun equals_fails_with_different_items() {
        assertNotEquals(
            illegal = tiledListOf(
                0 to 0,
                0 to 1,
                0 to 2,
            ),
            actual = tiledListOf(
                0 to 0,
                0 to 3,
                0 to 2,
            )
        )
    }

    @Test
    fun equals_works_with_simple_list() {
        val tiledList = tiledListOf(
            0 to 0,
            0 to 1,
            0 to 2,
        )
        assertEquals(
            expected = listOf(
                0,
                1,
                2,
            ),
            actual = tiledList
        )
        assertEquals(
            expected = listOf(0),
            actual = tiledList.queries()
        )
    }
}