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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

data class ScrollState(
    val offset: Int = 0,
    val dy: Int = 0,
    val minViewPortIndex : Int = 0,
    val maxViewPortIndex: Int = 0,
    val isDownward: Boolean = true,
)
@Composable
fun NumberTile(tile: NumberTile) {
    Button(
        modifier = Modifier
            .aspectRatio(1f),
        border = BorderStroke(width = 2.dp, color = Color(tile.color)),
        onClick = { /*TODO*/ },
        content = { Text(text = tile.number.toString()) }
    )
}
