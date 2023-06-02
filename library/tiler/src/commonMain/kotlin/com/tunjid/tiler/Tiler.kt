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

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal sealed interface Tiler<Query, Item> {

    /**
     * Whether there's [Tiler] any meaningful change in the [Tiler] state requiring it emit data
     */
    suspend fun shouldEmit(): Boolean

    /**
     * The [TiledList] produced my this [Tiler]
     */
    suspend fun tiledItems(): TiledList<Query, Item>

    /**
     * Processes the [Tile.Output] for some [Tile.Input] earlier in the tiling pipeline
     */
    suspend fun process(
        output: Tile.Output<Query, Item>
    ): Tiler<Query, Item>
}

/**
 * Class used as seed for the tiling pipeline
 */
internal data class ImmutableTiler<Query, Item>(
    val metadata: Metadata<Query, Item>,
) : Tiler<Query, Item> {
    override suspend fun shouldEmit(): Boolean =
        false

    override suspend fun tiledItems(): TiledList<Query, Item> =
        emptyTiledList()

    override suspend fun process(output: Tile.Output<Query, Item>): Tiler<Query, Item> =
        MutableTiler(metadata).process(output)
}

/**
 * Mutable [Tiler] implementation for efficient tiling
 */
internal class MutableTiler<Query, Item>(
    private val metadata: Metadata<Query, Item>,
) : Tiler<Query, Item> {
    private val mutex = Mutex()

    override suspend fun shouldEmit() = mutex.withLock { metadata.shouldEmit }

    override suspend fun tiledItems(): TiledList<Query, Item> = mutex.withLock { metadata.tiledItems() }

    /**
     * Mutates this [MutableTiler] with [Tile.Output] produced from the tiling process.
     * As tiling collects from multiple flows concurrently, it is imperative a mutex is used to
     * synchronize access to the variables modified.
     */
    override suspend fun process(
        output: Tile.Output<Query, Item>
    ): MutableTiler<Query, Item> = mutex.withLock {
        metadata.update(output)
        this
    }
}
