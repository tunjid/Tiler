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

import com.tunjid.tiler.TiledList

/**
 * A [TiledList] implementation that allows for transforming a [TiledList] while delegating to
 * the original for query lookup. Lookup is O(n).
 * If pivoting while tiling, this is on average O(n/2) as the user is typically in the center of the pivot.
 */
internal class TransformedTiledList<Query, Item>(
    private val originalList: TiledList<Query, Item>,
    private val transformedList: List<Item>,
) : AbstractList<Item>(), TiledList<Query, Item> {

    override val size: Int get() = transformedList.size

    override fun get(index: Int): Item = transformedList[index]

    override fun queryFor(index: Int): Query {
        val item = get(index)
        val originalIndex = originalList.indexOf(item)
        return originalList.queryFor(originalIndex)
    }

}