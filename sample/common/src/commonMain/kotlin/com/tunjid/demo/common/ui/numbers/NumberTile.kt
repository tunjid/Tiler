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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

data class NumberTile(
    val number: Int,
    val color: Int,
)
val NumberTile.key get() = "tile-$number"

@Composable
fun NumberTile(
    modifier: Modifier = Modifier,
    numberTile: NumberTile
) {
    Button(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        elevation = ButtonDefaults.elevation(defaultElevation = 0.dp),
        border = BorderStroke(width = 2.dp, color = Color(numberTile.color)),
        colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.surface),
        onClick = { /*TODO*/ },
        content = {
            Text(
                text = numberTile.number.toString(),
                color = Color(numberTile.color)
            )
        }
    )
}
