/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tunjid.tyler

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tunjid.mutator.Mutator
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.scan
import kotlin.math.abs
import kotlin.math.max

data class ScrollState(
    val offset: Int = 0,
    val dy: Int = 0,
    val page: Int = 0,
    val isDownward: Boolean = true,
)

@Composable
fun NumberedTileList(mutator: Mutator<Action, StateFlow<State>>) {
    val state by mutator.state.collectAsState()
    val chunkedTiles = state.chunkedTiles

    val listState = rememberLazyListState()
    LazyColumn(state = listState) {
        items(
            items = chunkedTiles,
            itemContent = { ChunkedNumberTiles(tiles = it) }
        )
    }

    LaunchedEffect(listState, chunkedTiles) {
        snapshotFlow {
            ScrollState(
                offset = listState.firstVisibleItemScrollOffset,
                page = max(
                    chunkedTiles.getOrNull(listState.firstVisibleItemIndex)
                        ?.firstOrNull()
                        ?.page
                        ?: 0,
                    chunkedTiles.getOrNull(
                        listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    )
                        ?.firstOrNull()
                        ?.page
                        ?: 0
                )
            )

        }
            .scan(ScrollState(), ScrollState::updateDirection)
            .filter { abs(it.dy) > 4 }
            .distinctUntilChangedBy(ScrollState::page)
            .collect { mutator.accept(Action.Load(it.page)) }
    }
}

@Composable
private fun ChunkedNumberTiles(tiles: List<NumberTile>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0..2) when (val tile = tiles.getOrNull(i)) {
            null -> Spacer(modifier = Modifier.weight(1F))
            else -> NumberTile(
                modifier = Modifier.weight(1F),
                tile = tile
            )
        }
    }
}

@Composable
private fun NumberTile(
    modifier: Modifier,
    tile: NumberTile
) {
    Button(
        modifier = modifier
            .aspectRatio(1f),
        border = BorderStroke(width = 2.dp, color = Color(tile.color)),
        onClick = { /*TODO*/ },
        content = { Text(text = tile.number.toString()) }
    )
}

private fun ScrollState.updateDirection(new: ScrollState) = new.copy(
    page = page,
    dy = new.offset - offset,
    isDownward = when {
        abs(new.offset - offset) > 10 -> isDownward
        else -> new.offset > offset
    }
)
