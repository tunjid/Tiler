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

package com.tunjid.demo.common.ui.numbers

data class NumberTile(
    val number: Int,
    val color: Int,
    val page: Int
)

sealed class Item(open val page: Int) {

    data class Tile(
        val numberTile: NumberTile
    ) : Item(numberTile.page)

    data class Header(
        override val page: Int,
        val color: Int,
    ) : Item(page)

    val key: Any
        get() = when (this) {
            is Tile -> "tile-${numberTile.page}-${numberTile.number}"
            is Header -> "header-$page"
        }
}

val Any.isStickyHeaderKey get() = this is String && this.startsWith("header")

val Any?.pageFromKey get() = when (this) {
    is String -> split("-").getOrNull(1)?.toIntOrNull() ?: 0
    else -> 0
}

