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

package com.tunjid.demo.common.ui.numbers.advanced

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import com.tunjid.demo.common.ui.AppRoute
import com.tunjid.demo.common.ui.numbers.ListStyle
import com.tunjid.demo.common.ui.numbers.NumberTileGrid
import com.tunjid.demo.common.ui.numbers.NumberTileList
import com.tunjid.demo.common.ui.numbers.NumberTiles
import com.tunjid.demo.common.ui.numbers.Tabbed

object AdvancedRoute : AppRoute {
    override val id: String
        get() = "advanced"

    @Composable
    override fun Render() {
        Tabbed(
            listStyles = listOf(
                NumberTileList as ListStyle<ScrollableState>,
                NumberTileGrid as ListStyle<ScrollableState>,
            ),
            contentDependencies = { coroutineScope, listStyle, isDark ->
                numberTilesMutator(
                    scope = coroutineScope,
                    isDark = isDark,
                    listStyle = listStyle,
                    itemsPerPage = listStyle.itemsPerPage
                )
            },
            content = { _, dependency ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    NumberTiles(
                        mutator = dependency
                    )
                }
            }
        )
    }

}