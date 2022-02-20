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
    id("com.android.library")
    kotlin("multiplatform")
    id("org.jetbrains.compose")
}

kotlin {
    android()
    jvm("desktop")
    sourceSets {
        named("commonMain") {
            dependencies {
                implementation(project(":tiler"))

                implementation(libs.jetbrains.compose.ui.tooling)
                implementation(libs.jetbrains.compose.ui.util)

                implementation(libs.jetbrains.compose.runtime)
                implementation(libs.jetbrains.compose.animation)
                implementation(libs.jetbrains.compose.material)
                implementation(libs.jetbrains.compose.foundation.layout)

                implementation(libs.kotlinx.coroutines.core)

                implementation(libs.tunjid.mutator.core.common)
                implementation(libs.tunjid.mutator.coroutines.common)
                implementation(libs.tunjid.treenav.common)
            }
        }
        named("androidMain") {
            dependencies {
                implementation(libs.androidx.compose.foundation.layout)
            }
        }
        all {
            languageSettings.useExperimentalAnnotation("androidx.compose.animation.ExperimentalAnimationApi")
            languageSettings.useExperimentalAnnotation("androidx.compose.foundation.ExperimentalFoundationApi")
            languageSettings.useExperimentalAnnotation("androidx.compose.material.ExperimentalMaterialApi")
            languageSettings.useExperimentalAnnotation("kotlinx.serialization.ExperimentalSerializationApi")
            languageSettings.useExperimentalAnnotation("kotlinx.coroutines.ExperimentalCoroutinesApi")
            languageSettings.useExperimentalAnnotation("kotlinx.coroutines.FlowPreview")
        }
        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
            kotlinOptions.jvmTarget = "11"
        }
        configurations.all {
            resolutionStrategy.eachDependency {
                if (requested.group.startsWith("androidx.compose")) {
                    useVersion("1.2.0-alpha03")
                    because("I need the changes in lazyGrid")
                }
            }
        }
    }
}

android {
    compileSdk = 31

    defaultConfig {
        minSdk = 21
        targetSdk = 31
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    sourceSets {
        named("main") {
            manifest.srcFile("src/androidMain/AndroidManifest.xml")
            res.srcDirs("src/androidMain/res")
        }
    }
}

