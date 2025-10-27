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

plugins {
    kotlin("multiplatform")
    id("publishing-library-convention")
    id("android-library-convention")
    id("kotlin-jvm-convention")
    id("kotlin-library-convention")
    id("maven-publish")
    signing
    id("org.jetbrains.dokka")
    id("org.jetbrains.compose")
    alias(libs.plugins.compose.compiler)
}

android {
    buildFeatures {
        compose = true
    }
}

kotlin {
    js(IR) {
        nodejs()
        browser()
    }
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":library:tiler"))
                implementation(libs.kotlinx.coroutines.core)

                implementation(libs.compose.multiplatform.runtime)
                implementation(libs.compose.multiplatform.foundation)
                implementation(libs.compose.multiplatform.foundation.layout)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.cashapp.turbine)
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
