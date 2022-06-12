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

import org.jetbrains.kotlin.konan.properties.hasProperty


plugins {
    `android-library-convention`
    `kotlin-library-convention`
    `publishing-library-convention`
    id("org.jetbrains.compose")
    `maven-publish`
    signing
    id("org.jetbrains.dokka")
}

kotlin {
    sourceSets {
        named("commonMain") {
            dependencies {
                implementation(libs.jetbrains.compose.foundation)
                implementation(libs.jetbrains.compose.foundation.layout)
            }
        }
        named("androidMain") {
            dependencies {
                implementation(libs.androidx.compose.foundation)
                implementation(libs.androidx.compose.foundation.layout)
            }
        }

        all {
            languageSettings.apply {
                optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
                optIn("kotlinx.coroutines.FlowPreview")
            }
        }
    }
}

