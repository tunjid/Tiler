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

package com.tunjid.tiler.compose

import androidx.compose.runtime.Immutable
import com.tunjid.tiler.TiledList
import com.tunjid.tiler.buildTiledList
import kotlin.jvm.JvmInline

@Immutable
@JvmInline
value class ImmutableTiledList<Query, Item> internal constructor(
    private val backing: TiledList<Query, Item>
) : TiledList<Query, Item> by backing

/**
 * Creates an [ImmutableTiledList] by making a copy of the original [TiledList]
 */
fun <Query, Item> TiledList<Query, Item>.toImmutableTiledList(): ImmutableTiledList<Query, Item> =
    ImmutableTiledList(
        buildTiledList build@{
            for (index in this@toImmutableTiledList.indices) add(
                query = this@toImmutableTiledList.queryAt(index),
                item = this@toImmutableTiledList[index]
            )
        }
    )