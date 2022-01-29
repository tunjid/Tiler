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
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope

@Composable
fun NumberTileTabs(
    scope: CoroutineScope,
    listStyles: List<ListStyle<ScrollableState>>
) {
    if (listStyles.isEmpty()) return

    var selectedStyle by remember { mutableStateOf(listStyles.first()) }
    val mutatorCreator = remember {
        listStyles.associateWith { listStyle ->
            numberTilesMutator(scope = scope, listStyle = listStyle)
        }
    }

    Column {
        Tabs(
            listStyles = listStyles,
            onClick = { selectedStyle = it }
        )
        NumberTiles(
            mutator = mutatorCreator.getValue(selectedStyle)
        )
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
        listStyles.forEach { listStyle ->
            Button(
                shape = RoundedCornerShape(
                    topStart = CornerSize(0),
                    topEnd = CornerSize(0),
                    bottomEnd = CornerSize(0),
                    bottomStart = CornerSize(0)
                ),
                onClick = { onClick(listStyle) }
            ) {
                Text(text = listStyle.name)
            }
        }
    }
}