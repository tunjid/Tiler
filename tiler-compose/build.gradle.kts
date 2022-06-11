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

/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
    kotlin("multiplatform")
    `maven-publish`
    signing
    id("org.jetbrains.dokka")
    `android-library-convention`
    `kotlin-library-convention`
    id("org.jetbrains.compose")
}

group = "com.tunjid.tiler"
version = "0.0.2"

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "1.8"
        }
        withJava()
        testRuns["test"].executionTask.configure {
            useJUnit()
        }
    }
    linuxX64()
    macosX64()
    macosArm64()
    mingwX64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":tiler"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.jetbrains.compose.foundation.layout)
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

        // Native targets
        val nativeMain by creating {
            dependsOn(commonMain)
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

val dokkaHtml by tasks.getting(org.jetbrains.dokka.gradle.DokkaTask::class)

val javadocJar: TaskProvider<Jar> by tasks.registering(Jar::class) {
    dependsOn(dokkaHtml)
    archiveClassifier.set("javadoc")
    from(dokkaHtml.outputDirectory)
}

publishing {
    publications {
        withType<MavenPublication> {
            artifact(javadocJar)
            pom {
                    name.set(project.name)
                    description.set("An abstraction for a data type akin to a reactive map")
                    url.set("https://github.com/tunjid/tiler")
                    licenses {
                        license {
                            name.set("Apache License 2.0")
                            url.set("https://github.com/tunjid/tiler/blob/main/LICENSE")
                        }
                    }
                    developers {
                        developer {
                            id.set("tunjid")
                            name.set("Adetunji Dahunsi")
                            email.set("tjdah100@gmail.com")
                        }
                    }
                    scm {
                        connection.set("scm:git:github.com/tunjid/tiler.git")
                        developerConnection.set("scm:git:ssh://github.com/tunjid/tiler.git")
                        url.set("https://github.com/tunjid/tiler/tree/main")
                    }
                }

        }
    }
    repositories {
        val localProperties = parent?.ext?.get("localProps") as? java.util.Properties
            ?: return@repositories

        val publishUrl = localProperties.getProperty("publishUrl")
        if (publishUrl != null) {
            maven {
                name = localProperties.getProperty("repoName")
                url = uri(localProperties.getProperty("publishUrl"))
                credentials {
                    username = localProperties.getProperty("username")
                    password = localProperties.getProperty("password")
                }
            }
        }
    }
}

signing {
    val localProperties = parent?.ext?.get("localProps") as? java.util.Properties
        ?: return@signing

    val signingKey = localProperties.getProperty("signingKey")
    val signingPassword = localProperties.getProperty("signingPassword")

    if (signingKey != null && signingPassword != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications)
    }
}

