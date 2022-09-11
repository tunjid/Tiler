/*
 * Copyright 2020-2021 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
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