package com.tunjid.demo

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.tunjid.demo.common.ui.numbers.NumberedTileList
import com.tunjid.demo.common.ui.numbers.numberTilesMutator

fun main() {
    application {
        val windowState = rememberWindowState(
            size = DpSize(400.dp, 800.dp)
        )
        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "Tiling Demo"
        ) {
            val scope = rememberCoroutineScope()
            val mutator = remember {
                numberTilesMutator(scope = scope)
            }
            NumberedTileList(mutator = mutator)
        }
    }
}


