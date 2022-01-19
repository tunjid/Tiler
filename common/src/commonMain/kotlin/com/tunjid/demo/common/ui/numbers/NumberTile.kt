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

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.*
import androidx.compose.material.ButtonDefaults.buttonColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tunjid.mutator.Mutator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.collections.List
import kotlin.collections.firstOrNull
import kotlin.collections.getOrNull
import kotlin.collections.lastOrNull
import kotlin.collections.minOf
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class ScrollState(
    val offset: Int = 0,
    val dy: Int = 0,
    val page: Int = 0,
    val isDownward: Boolean = true,
)

@Composable
fun NumberedTileList(mutator: Mutator<Action, StateFlow<State>>) {
    val state by mutator.state.collectAsState()
    val currentPage = state.currentPage
    val isAscending = state.isAscending
    val chunkedItems = state.chunkedItems
    val stickyHeader = state.stickyHeader
    val loadSummary: Flow<String> = remember {
        mutator.state.map { it.loadSummary }.distinctUntilChanged()
    }

    val scaffoldState = rememberScaffoldState()
    val listState = rememberLazyListState()

    Scaffold(
        scaffoldState = scaffoldState,
        floatingActionButton = {
            Fab(
                mutator = mutator,
                currentPage = currentPage,
                isAscending = isAscending
            )
        }
    ) {
        StickyHeaderContainer(
            listState = listState,
            headerMatcher = Any::isStickyHeaderKey,
            stickyHeader = {
                if (stickyHeader != null) HeaderItem(stickyHeader)
            },
            content = {
                LazyColumn(state = listState) {
                    items(
                        items = chunkedItems,
                        key = { it.minOf(Item::key) },
                        itemContent = {
                            ChunkedNumberTiles(
                                modifier = Modifier,
                                tiles = it
                            )
                        }
                    )
                }
            }
        )
    }

    // Load when this Composable enters the composition
    LaunchedEffect(true) {
        mutator.accept(Action.Load(page = 0, isAscending = isAscending))
    }

    // Keep the sticky headers in sync
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.firstOrNull()?.index }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { mutator.accept(Action.FirstVisibleIndexChanged(index = it)) }
    }

    // Endless scrolling
    LaunchedEffect(listState, chunkedItems) {
        snapshotFlow {
            ScrollState(
                offset = listState.firstVisibleItemScrollOffset,
                page = chunkedItems.maxAndMinPages(listState).let {
                    if (isAscending) max(it.first, it.second) else min(it.first, it.second)
                }
            )
        }
            .scan(ScrollState(), ScrollState::updateDirection)
            .filter { abs(it.dy) > 4 }
            .distinctUntilChangedBy(ScrollState::page)
            .collect { mutator.accept(Action.Load(page = it.page, isAscending = isAscending)) }
    }

    // In the docs: https://developer.android.com/reference/kotlin/androidx/compose/material/SnackbarHostState
    //
    // `snackbarHostState.showSnackbar` is a suspending function that queues snack bars to be shown.
    // If it is called multiple times, the showing snackbar is not dismissed,
    // rather the new snackbar is added to the queue to be shown when the current one is dismissed.
    //
    // I however want to only have 1 snackbar in the queue at any one time, so I keep a ref
    // to the prev job to manually cancel it.
    val coroutineScope = rememberCoroutineScope()
    var currentSnackbarJob by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(loadSummary, scaffoldState.snackbarHostState) {
        loadSummary
            .collect {
                currentSnackbarJob?.cancel()
                currentSnackbarJob = coroutineScope.launch {
                    scaffoldState.snackbarHostState.showSnackbar(
                        message = it
                    )
                }
            }
    }
}

@Composable
private fun Fab(
    mutator: Mutator<Action, StateFlow<State>>,
    currentPage: Int,
    isAscending: Boolean
) {
    FloatingActionButton(
        onClick = {
            mutator.accept(
                Action.Load(
                    page = currentPage,
                    isAscending = !isAscending
                )
            )
        },
        content = {
            Row(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .animateContentSize()
            ) {
                val text = if (isAscending) "Sort descending" else "Sort ascending"
                Text(text)
                when {
                    isAscending -> Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = text
                    )
                    else -> Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = text
                    )
                }
            }
        }
    )
}

@Composable
private fun ChunkedNumberTiles(
    modifier: Modifier = Modifier,
    tiles: List<Item>
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until GridSize) when (val item = tiles.getOrNull(i)) {
            null -> Spacer(modifier = Modifier.weight(1F))
            else -> when (item) {
                is Item.Header -> HeaderItem(item)
                is Item.Tile -> NumberTile(
                    modifier = Modifier.weight(1F),
                    tile = item.numberTile
                )
            }
        }
    }
}

@Composable
private fun HeaderItem(item: Item.Header) {
    Button(
        elevation = ButtonDefaults.elevation(defaultElevation = 0.dp),
        border = BorderStroke(width = 2.dp, color = Color(item.color)),
        colors = buttonColors(backgroundColor = MaterialTheme.colors.surface),
        onClick = { /*TODO*/ },
        content = {
            Text(
                modifier = Modifier
                    .padding(
                        vertical = 4.dp,
                        horizontal = 8.dp
                    ),
                text = "Page ${item.page}"
            )
        }
    )
}

@Composable
private fun NumberTile(
    modifier: Modifier,
    tile: NumberTile
) {
    Button(
        modifier = modifier
            .aspectRatio(1f)
            .scale(0.9f),
        elevation = ButtonDefaults.elevation(defaultElevation = 0.dp),
        border = BorderStroke(width = 2.dp, color = Color(tile.color)),
        colors = buttonColors(backgroundColor = MaterialTheme.colors.surface),
        onClick = { /*TODO*/ },
        content = { Text(text = tile.number.toString(), color = Color(tile.color)) }
    )
}

private fun List<List<Item>>.maxAndMinPages(listState: LazyListState) =
    Pair(
        first = (getOrNull(listState.firstVisibleItemIndex)
            ?.firstOrNull()
            ?.page
            ?: 0),
        second = (getOrNull(listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0)
            ?.firstOrNull()
            ?.page
            ?: 0)
    )

private fun ScrollState.updateDirection(new: ScrollState) = new.copy(
    page = new.page,
    dy = new.offset - offset,
    isDownward = when {
        abs(new.offset - offset) > 10 -> isDownward
        else -> new.offset > offset
    }
)
