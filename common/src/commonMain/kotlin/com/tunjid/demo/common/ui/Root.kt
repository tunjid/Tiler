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

package com.tunjid.demo.common.ui

import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import com.tunjid.demo.common.ui.numbers.advanced.AdvancedRoute
import com.tunjid.mutator.Mutation
import com.tunjid.mutator.coroutines.asNoOpStateFlowMutator
import com.tunjid.mutator.coroutines.stateFlowMutator
import com.tunjid.treenav.Route
import com.tunjid.treenav.StackNav
import com.tunjid.treenav.current
import kotlinx.coroutines.flow.SharingStarted

interface AppRoute : Route {
    @Composable
    fun Render()
}

private val initialNav = StackNav(
    name = "Default",
    routes = listOf(AdvancedRoute)
)

@Composable
fun Root() {
    val scope = rememberCoroutineScope()
    val navMutator = stateFlowMutator<Mutation<StackNav>, StackNav>(
        scope = scope,
        initialState = initialNav,
        started = SharingStarted.WhileSubscribed(),
        actionTransform = { it }
    )
    CompositionLocalProvider(LocalNavigation provides navMutator) {
        val stackNav by LocalNavigation.current.state.collectAsState()
        val appRoute = stackNav.current as? AppRoute
        Surface {
            appRoute?.Render()
        }
    }
}

val LocalNavigation = staticCompositionLocalOf {
    initialNav.asNoOpStateFlowMutator<Mutation<StackNav>, StackNav>()
}