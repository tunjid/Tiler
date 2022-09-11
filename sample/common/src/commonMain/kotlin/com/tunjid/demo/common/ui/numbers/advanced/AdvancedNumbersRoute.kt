///*
// * Copyright 2021 Google LLC
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     https://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package com.tunjid.demo.common.ui.numbers.advanced
//
//import Action
//import PageQuery
//import State
//import androidx.compose.animation.animateContentSize
//import androidx.compose.foundation.BorderStroke
//import androidx.compose.foundation.layout.Row
//import androidx.compose.foundation.layout.padding
//import androidx.compose.foundation.lazy.LazyColumn
//import androidx.compose.foundation.lazy.items
//import androidx.compose.foundation.lazy.rememberLazyListState
//import androidx.compose.material.Button
//import androidx.compose.material.ButtonDefaults
//import androidx.compose.material.FloatingActionButton
//import androidx.compose.material.Icon
//import androidx.compose.material.MaterialTheme
//import androidx.compose.material.Scaffold
//import androidx.compose.material.Text
//import androidx.compose.material.icons.Icons
//import androidx.compose.material.icons.filled.KeyboardArrowDown
//import androidx.compose.material.icons.filled.KeyboardArrowUp
//import androidx.compose.material.rememberScaffoldState
//import androidx.compose.runtime.Composable
//import androidx.compose.runtime.LaunchedEffect
//import androidx.compose.runtime.collectAsState
//import androidx.compose.runtime.getValue
//import androidx.compose.runtime.mutableStateOf
//import androidx.compose.runtime.remember
//import androidx.compose.runtime.rememberCoroutineScope
//import androidx.compose.runtime.setValue
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.unit.dp
//import com.tunjid.demo.common.ui.numbers.Item
//import com.tunjid.mutator.Mutator
//import kotlinx.coroutines.Job
//import kotlinx.coroutines.flow.Flow
//import kotlinx.coroutines.flow.StateFlow
//import kotlinx.coroutines.flow.distinctUntilChanged
//import kotlinx.coroutines.flow.map
//import kotlinx.coroutines.launch
//
//
//@Composable
//fun NumberTiles(
//    mutator: Mutator<Action, StateFlow<State>>
//) {
//    val state by mutator.state.collectAsState()
//    val isAscending = state.isAscending
//    val items = state.items
//    val loadSummary: Flow<String> = remember {
//        mutator.state.map { it.loadSummary }.distinctUntilChanged()
//    }
//
//    val scaffoldState = rememberScaffoldState()
//    val listState = rememberLazyListState()
//
//    Scaffold(
//        scaffoldState = scaffoldState,
//        floatingActionButton = {
//            Fab(
//                onClick = mutator.accept,
//                isAscending = isAscending
//            )
//        }
//    ) {
//        LazyColumn(
//            state = listState,
//            content = {
//                items(
//                    items = items,
//                    key = { it.key },
//                    itemContent = { item ->
//                        when (item) {
//                            is Item.Header ->     Button(
//                                modifier = Modifier.animateItemPlacement(),
//                                elevation = ButtonDefaults.elevation(defaultElevation = 0.dp),
//                                border = BorderStroke(width = 2.dp, color = Color(item.color)),
//                                colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.surface),
//                                onClick = { /*TODO*/ },
//                                content = {
//                                    Text(
//                                        modifier = Modifier
//                                            .padding(
//                                                vertical = 4.dp,
//                                                horizontal = 8.dp
//                                            ),
//                                        text = "Page ${item.page}"
//                                    )
//                                }
//                            )
//
//                            is Item.Tile -> Button(
//                                modifier = Modifier.animateItemPlacement(),
//                                elevation = ButtonDefaults.elevation(defaultElevation = 0.dp),
//                                border = BorderStroke(width = 2.dp, color = Color(item.numberTile.color)),
//                                colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.surface),
//                                onClick = { /*TODO*/ },
//                                content = { Text(text = item.numberTile.number.toString(), color = Color(item.numberTile.color)) }
//                            )
//                        }
//                    }
//                )
//            }
//        )
//    }
//
//    // Load when this Composable enters the composition
//    LaunchedEffect(true) {
//        mutator.accept(Action.Load.LoadAround(PageQuery(page = 0, isAscending = state.isAscending)))
//    }
//
////    LaunchedEffect(lazyState) {
////        snapshotFlow {
////            listStyle.firstVisibleKey(lazyState)?.pageFromKey
////        }
////            .filterNotNull()
////            .distinctUntilChanged()
////            .collect {
////                mutator.accept(Action.Load.LoadAround(PageQuery(page = it, isAscending = state.isAscending)))
////            }
////    }
//
//    // In the docs: https://developer.android.com/reference/kotlin/androidx/compose/material/SnackbarHostState
//    //
//    // `snackbarHostState.showSnackbar` is a suspending function that queues snack bars to be shown.
//    // If it is called multiple times, the showing snackbar is not dismissed,
//    // rather the new snackbar is added to the queue to be shown when the current one is dismissed.
//    //
//    // I however want to only have 1 snackbar in the queue at any one time, so I keep a ref
//    // to the prev job to manually cancel it.
//    val coroutineScope = rememberCoroutineScope()
//    var currentSnackbarJob by remember { mutableStateOf<Job?>(null) }
//    LaunchedEffect(loadSummary, scaffoldState.snackbarHostState) {
//        loadSummary
//            .collect {
//                currentSnackbarJob?.cancel()
//                currentSnackbarJob = coroutineScope.launch {
//                    scaffoldState.snackbarHostState.showSnackbar(
//                        message = it
//                    )
//                }
//            }
//    }
//}
//
//@Composable
//private fun Fab(
//    onClick: (Action) -> Unit,
//    isAscending: Boolean
//) {
//    FloatingActionButton(
//        onClick = { onClick(Action.Load.ToggleOrder(isAscending)) },
//        content = {
//            Row(
//                modifier = Modifier
//                    .padding(horizontal = 8.dp)
//                    .animateContentSize()
//            ) {
//                val text = if (isAscending) "Sort descending" else "Sort ascending"
//                Text(text)
//                when {
//                    isAscending -> Icon(
//                        imageVector = Icons.Default.KeyboardArrowDown,
//                        contentDescription = text
//                    )
//
//                    else -> Icon(
//                        imageVector = Icons.Default.KeyboardArrowUp,
//                        contentDescription = text
//                    )
//                }
//            }
//        }
//    )
//}
