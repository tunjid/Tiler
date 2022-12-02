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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

data class ReactiveList<T>(
    private val backing: List<T>
) : List<T> by backing {

    private val indexSharedFlow = MutableSharedFlow<Int>()

    val indexFlow : Flow<Int> = indexSharedFlow
    override fun get(index: Int): T {
        indexSharedFlow.tryEmit(index)
        return backing[index]
    }
}

data class ReactiveMap<K, V>(
    private val backing: Map<K, V>
) : Map<K, V> by backing {

    private val keySharedFlow = MutableStateFlow<K?>(null)

    val keyFlow : Flow<K> = keySharedFlow.filter { it != null }.map { it!! }
    override fun get(key: K): V? {
        keySharedFlow.tryEmit(key)
        return backing[key]
    }
}