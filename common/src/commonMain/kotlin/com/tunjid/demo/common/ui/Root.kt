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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.BottomAppBar
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.tunjid.demo.common.ui.numbers.advanced.AdvancedNumbersRoute
import com.tunjid.demo.common.ui.numbers.intermediate.IntermediateNumbersRoute
import com.tunjid.demo.common.ui.numbers.simple.SimpleNumbersRoute
import com.tunjid.mutator.Mutation
import com.tunjid.mutator.accept
import com.tunjid.mutator.coroutines.asNoOpStateFlowMutator
import com.tunjid.mutator.coroutines.stateFlowMutator
import com.tunjid.treenav.MultiStackNav
import com.tunjid.treenav.Route
import com.tunjid.treenav.StackNav
import com.tunjid.treenav.current
import com.tunjid.treenav.switch
import kotlinx.coroutines.flow.SharingStarted

interface AppRoute : Route {
    @Composable
    fun Render()
}

private val initialNav = MultiStackNav(
    name = "Default",
    stacks = listOf(
        StackNav("Simple", routes = listOf(SimpleNumbersRoute)),
        StackNav("Intermediate", routes = listOf(IntermediateNumbersRoute)),
        StackNav("Advanced", routes = listOf(AdvancedNumbersRoute))
    )
)

@Composable
fun Root() {
    val scope = rememberCoroutineScope()
    val saveableStateHolder = rememberSaveableStateHolder()
    val navMutator = stateFlowMutator<Mutation<MultiStackNav>, MultiStackNav>(
        scope = scope,
        initialState = initialNav,
        started = SharingStarted.WhileSubscribed(),
        actionTransform = { it }
    )
    CompositionLocalProvider(LocalNavigation provides navMutator) {
        val stackNav by LocalNavigation.current.state.collectAsState()
        val appRoute = stackNav.current as? AppRoute
        Surface {
            Column {
                saveableStateHolder.SaveableStateProvider(appRoute?.id ?: "") {
                    Box(modifier = Modifier.weight(1f)) {
                        appRoute?.Render()
                    }
                }
                BottomNav()
            }
        }
    }
}

@Composable
private fun BottomNav() {
    BottomAppBar(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        backgroundColor = MaterialTheme.colors.primary,
    ) {

        val navMutator = LocalNavigation.current
        val nav by navMutator.state.collectAsState()
        BottomNavigation(
            backgroundColor = MaterialTheme.colors.primary,
        ) {
            nav.stacks
                .forEachIndexed { index, stack ->
                    BottomNavigationItem(
                        icon = {
//                            Icon(
//                                imageVector = navItem.icon,
//                                contentDescription = navItem.name
//                            )
                        },
                        label = { Text(stack.id) },
                        selected = stack.current == nav.current,
                        onClick = {
                            navMutator.accept { switch(toIndex = index) }
                        }
                    )
                }
        }
    }
}

val LocalNavigation = staticCompositionLocalOf {
    initialNav.asNoOpStateFlowMutator<Mutation<MultiStackNav>, MultiStackNav>()
}