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

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import kotlinx.coroutines.CoroutineScope

@Composable
fun NumberTileTabs(
    scope: CoroutineScope,
    listStyles: List<ListStyle<ScrollableState>>
) {
    if (listStyles.isEmpty()) return
    val saveableStateHolder = rememberSaveableStateHolder()

    var selectedStyle by remember { mutableStateOf(listStyles.first()) }
    val mutatorCreator = remember {
        listStyles.associateWith { listStyle ->
            numberTilesMutator(
                scope = scope,
                listStyle = listStyle,
                itemsPerPage = listStyle.itemsPerPage,
            )
        }
    }
    val mutator = mutatorCreator.getValue(selectedStyle)

    Surface {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Tabs(
                listStyles = listStyles,
                onClick = { selectedStyle = it }
            )
            saveableStateHolder.SaveableStateProvider(selectedStyle) {
                NumberTiles(
                    mutator = mutator
                )
            }
        }
    }
}

@Composable
private fun Tabs(
    listStyles: List<ListStyle<ScrollableState>>,
    onClick: (ListStyle<ScrollableState>) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.Center
    ) {
        listStyles.forEachIndexed { index, listStyle ->
            val isStart = index == 0
            val isEnd = index == listStyles.lastIndex
            OutlinedButton(
                shape = RoundedCornerShape(
                    topStart = CornerSize(percent = if (isStart) 20 else 0),
                    topEnd = CornerSize(percent = if (isEnd) 20 else 0),
                    bottomEnd = CornerSize(percent = if (isEnd) 20 else 0),
                    bottomStart = CornerSize(percent = if (isStart) 20 else 0)
                ),
                onClick = { onClick(listStyle) }
            ) {
                Text(text = listStyle.name)
            }
        }
    }
}