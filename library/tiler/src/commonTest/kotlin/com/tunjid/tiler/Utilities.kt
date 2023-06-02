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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.assertEquals

private const val ITEMS_PER_PAGE = 10
internal val Int.testRange: IntRange
    get() {
        val offset = this * ITEMS_PER_PAGE
        val next = offset + ITEMS_PER_PAGE

        return offset until next
    }

internal fun Int.tiledTestRange(
    transform: List<Int>.() -> List<Int> = {this}
) = buildTiledList {
    addAll(query = this@tiledTestRange, items = transform(testRange.toList()))
}

suspend fun <T> Flow<T>.toListWithTimeout(timeoutMillis: Long): List<T> {
    val result = mutableListOf<T>()
    return withTimeoutOrNull(timeoutMillis) {
        collect(result::add)
        result
    } ?: result
}

fun <Query, Item> assertTiledListEquals(
    expected: TiledList<Query, Item>,
    actual: TiledList<Query, Item>,
) = assertEquals(
    expected = expected.asPairedList(),
    actual = actual.asPairedList()
)

fun <Query, Item> assertBatchTiledListEquals(
    expected: List<TiledList<Query, Item>>,
    actual: List<TiledList<Query, Item>>,
) = assertEquals(
    expected = expected.map(TiledList<Query, Item>::asPairedList),
    actual = actual.map(TiledList<Query, Item>::asPairedList)
)

private fun TiledList<*, *>.asPairedList(): List<Pair<*, *>> =
    mapIndexed { index, item -> queryAt(index) to item }
