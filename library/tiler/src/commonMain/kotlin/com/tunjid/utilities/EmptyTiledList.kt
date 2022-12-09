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

internal object EmptyTiledList : TiledList<Nothing, Nothing>, List<Nothing> by emptyList() {
    override fun queryAt(index: Int): Nothing =
        throw IndexOutOfBoundsException("Empty tiled list doesn't contain element at index $index.")
    override fun equals(other: Any?): Boolean = other is TiledList<*, *> && other.isEmpty()
    override fun hashCode(): Int = 1
    override fun toString(): String = "[]"
}

