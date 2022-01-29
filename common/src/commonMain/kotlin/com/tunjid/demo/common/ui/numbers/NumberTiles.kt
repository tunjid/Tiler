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
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tunjid.mutator.Mutator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.abs

data class ScrollState(
    val offset: Int = 0,
    val dy: Int = 0,
    val page: Int = 0,
    val isDownward: Boolean = true,
)

sealed class ListStyle<T : ScrollableState>(val name: String) {

    abstract fun firstVisibleIndex(state: T): Int?
    abstract fun scrollState(
        state: T,
        items: List<Item>,
        isAscending: Boolean
    ): ScrollState

    abstract fun stickyHeaderOffsetCalculator(
        state: T,
        headerMatcher: (Any) -> Boolean
    ): Int

    @Composable
    abstract fun rememberState(): T

    @Composable
    abstract fun Content(
        state: T,
        items: List<Item>
    )
}

@Composable
fun <T : ScrollableState> NumberTiles(mutator: Mutator<Action, StateFlow<State<T>>>) {
    val state by mutator.state.collectAsState()
    val currentPage = state.currentPage
    val isAscending = state.isAscending
    val items = state.items
    val stickyHeader = state.stickyHeader
    val listStyle = state.style
    val loadSummary: Flow<String> = remember {
        mutator.state.map { it.loadSummary }.distinctUntilChanged()
    }

    val scaffoldState = rememberScaffoldState()
    val lazyState = listStyle.rememberState()

    Scaffold(
        scaffoldState = scaffoldState,
        floatingActionButton = {
            Fab(
                onClick = mutator.accept,
                currentPage = currentPage,
                isAscending = isAscending
            )
        }
    ) {
        StickyHeaderContainer(
            lazyState = lazyState,
            offsetCalculator = { lazyState ->
                listStyle.stickyHeaderOffsetCalculator(
                    state = lazyState,
                    headerMatcher = Any::isStickyHeaderKey
                )
            },
            stickyHeader = {
                if (stickyHeader != null) HeaderTile(item = stickyHeader)
            },
            content = {
                listStyle.Content(state = lazyState, items = items)
            }
        )
    }

    // Load when this Composable enters the composition
    LaunchedEffect(true) {
        mutator.accept(Action.Load(page = 0, isAscending = isAscending))
    }

    // Keep the sticky headers in sync
    LaunchedEffect(lazyState) {
        snapshotFlow { listStyle.firstVisibleIndex(lazyState) }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { mutator.accept(Action.FirstVisibleIndexChanged(index = it)) }
    }

    // Endless scrolling
    LaunchedEffect(lazyState, items) {
        snapshotFlow {
            listStyle.scrollState(
                lazyState,
                items,
                isAscending
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
    onClick: (Action) -> Unit,
    currentPage: Int,
    isAscending: Boolean
) {
    FloatingActionButton(
        onClick = {
            onClick(
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

private fun ScrollState.updateDirection(new: ScrollState) = new.copy(
    page = new.page,
    dy = new.offset - offset,
    isDownward = when {
        abs(new.offset - offset) > 10 -> isDownward
        else -> new.offset > offset
    }
)
