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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import com.tunjid.demo.common.ui.AppTheme
import com.tunjid.demo.common.ui.Root
import org.jetbrains.skiko.wasm.onWasmReady


fun main() {
    onWasmReady {
        Window("Tiler") {

            Column(modifier = Modifier.fillMaxSize()) {

                Button(
                    onClick = { println("Clicked") },
                    content = {
                        Text("HELLO")
                    }
                )

                AppTheme {
                    Root()
                }
            }
        }
    }
}