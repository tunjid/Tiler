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

import androidx.compose.runtime.LaunchedEffect

data class NumberTile(
    val number: Int,
    val color: Int,
    val page: Int
)

/**
 * Keys items for Lazy UI and also saves page information for infinite scrolling efficiency with
 * [LaunchedEffect].
 *
 * Note only the id is used for equality
 */
// TODO: should this be a string value class for efficiency?
private class ItemKey(
    val id: String,
    val page: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ItemKey
        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int = id.hashCode()
}

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
            is Tile -> ItemKey(id = "tile-${numberTile.number}", page = numberTile.page)
            is Header -> ItemKey(id = "header-$page", page = page)
        }
}

val Any.isStickyHeaderKey get() = this is ItemKey && id.startsWith("header")

val Any?.pageFromKey get() = if (this is ItemKey) page else 0

