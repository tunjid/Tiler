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
    `publishing-library-convention`
    `maven-publish`
    signing
    id("org.jetbrains.dokka")
}

kotlin {
    js(IR) {
        nodejs()
        browser()
    }
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "1.8"
        }
        withJava()
        testRuns["test"].executionTask.configure {
            useJUnit()
        }
    }
    ios()
    iosSimulatorArm64()
    linuxX64()
    macosX64()
    macosArm64()
    mingwX64()
    tvos()
    tvosSimulatorArm64()
    watchos()
    watchosSimulatorArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.cashapp.turbine)
            }
        }
        val jvmMain by getting
        val jvmTest by getting

        val jsMain by getting
        val jsTest by getting

        // Native targets
        val nativeMain by creating {
            dependsOn(commonMain)
        }
        val iosMain by getting {
            dependsOn(nativeMain)
        }
        val iosSimulatorArm64Main by getting {
            dependsOn(nativeMain)
        }
        val tvosMain by getting {
            dependsOn(nativeMain)
        }
        val tvosSimulatorArm64Main by getting {
            dependsOn(nativeMain)
        }
        val watchosMain by getting {
            dependsOn(nativeMain)
        }
        val watchosSimulatorArm64Main by getting {
            dependsOn(nativeMain)
        }
        val linuxX64Main by getting {
            dependsOn(nativeMain)
        }
        val mingwX64Main by getting {
            dependsOn(nativeMain)
        }
        val macosX64Main by getting {
            dependsOn(nativeMain)
        }
        val macosArm64Main by getting {
            dependsOn(nativeMain)
        }

        all {
            languageSettings.apply {
                optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
                optIn("kotlinx.coroutines.FlowPreview")
            }
        }
    }
}
