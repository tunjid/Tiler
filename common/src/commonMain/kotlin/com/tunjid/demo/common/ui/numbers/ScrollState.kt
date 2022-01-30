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

data class ScrollState(
    val offset: Int = 0,
    val firstVisibleIndex: Int = 0,
    val dy: Int = 0,
    val firstPage: Int = 0,
    val lastPage: Int = 0,
    val isDownward: Boolean = true,
    val isAscending: Boolean = true,
)

fun ScrollState.updateDirection(new: ScrollState) = new.copy(
    firstPage = new.firstPage,
    lastPage = new.lastPage,
    dy = new.offset - offset,
    isDownward = when(firstVisibleIndex) {
        new.firstVisibleIndex -> isDownward
        else -> new.firstVisibleIndex > firstVisibleIndex
    }
)

val ScrollState.page get() = when(isDownward) {
    true -> lastPage
    false -> firstPage
}