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

@JvmInline
internal value class SizedIndex(
    private val packedValue: Long
) {
    /**
     * The size of the items at a particular [SizedIndex]
     */
    val size: Int
        get() = packedValue.shr(32).toInt()

    /**
     * An index in a [TiledList] for a [SizedIndex]
     */
    val index: Int
        get() = packedValue.and(0xFFFFFFFF).toInt()

    override fun toString(): String = "SizedIndex(size=$size, index=$index)"
}

internal fun SizedIndex(
    size: Int,
    index: Int,
) = SizedIndex(size.toLong().shl(32) or (index.toLong() and 0xFFFFFFFF))