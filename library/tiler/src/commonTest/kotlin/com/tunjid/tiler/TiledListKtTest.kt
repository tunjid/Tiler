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

import kotlin.test.Test
import kotlin.test.assertEquals


class TiledListKtTest {

    @Test
    fun tiled_list_builder_works() {
        val tiledList = buildTiledList {
            addAll(1, 1.testRange.toList())
            addAll(3, 3.testRange.toList())
        }
        assertEquals(
            expected = (1.testRange + 3.testRange).toList(),
            actual = tiledList
        )
    }

    @Test
    fun tiled_list_filter_transform_works() {
        val tiledList = buildTiledList {
            addAll(1, 1.testRange.toList())
            addAll(3, 3.testRange.toList())
        }
        assertEquals(
            expected = (1.testRange + 3.testRange).toList().filter { it % 2 == 0 },
            actual = tiledList.filterTransform { filter { it % 2 == 0 } }
        )
    }
}