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

import org.gradle.api.Action
import org.gradle.api.JavaVersion
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

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

/**
 * Configure base Kotlin options for JVM (non-Android)
 */
internal fun org.gradle.api.Project.configureKotlinJvm() {
    extensions.configure<JavaPluginExtension> {
        // Up to Java 11 APIs are available through desugaring
        // https://developer.android.com/studio/write/java11-minimal-support-table
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    configureKotlin()
}

/**
 * Configure base Kotlin options
 */
private fun org.gradle.api.Project.configureKotlin() {
    // Use withType to workaround https://youtrack.jetbrains.com/issue/KT-55947
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            // Set JVM target to 11
            jvmTarget.set(JvmTarget.JVM_11)
            freeCompilerArgs.set(
                freeCompilerArgs.get() + listOf(
                    "-Xuse-experimental=androidx.compose.animation.ExperimentalAnimationApi",
                    "-Xuse-experimental=androidx.compose.material.ExperimentalMaterialApi",
                    "-Xuse-experimental=kotlinx.serialization.ExperimentalSerializationApi",
                    "-Xuse-experimental=kotlinx.coroutines.ExperimentalCoroutinesApi",
                    "-Xuse-experimental=kotlinx.coroutines.FlowPreview"
                )
            )
        }
    }
//    val kotlinOptions = "kotlinOptions"
//    if (extensions.findByName(kotlinOptions) != null) {
//        extensions.configure(kotlinOptions, Action<KotlinJvmOptions> {
//            jvmTarget = JavaVersion.VERSION_11.toString()
//            freeCompilerArgs = freeCompilerArgs + listOf(
//                "-Xuse-experimental=androidx.compose.animation.ExperimentalAnimationApi",
//                "-Xuse-experimental=androidx.compose.material.ExperimentalMaterialApi",
//                "-Xuse-experimental=kotlinx.serialization.ExperimentalSerializationApi",
//                "-Xuse-experimental=kotlinx.coroutines.ExperimentalCoroutinesApi",
//                "-Xuse-experimental=kotlinx.coroutines.FlowPreview"
//            )
//        })
//    }
}