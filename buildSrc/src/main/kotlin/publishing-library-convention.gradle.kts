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
    `maven-publish`
    signing
    id("org.jetbrains.dokka")
}

allprojects {
    val versionKey = project.name + "_version"
    val libProps = parent?.ext?.get("libProps") as? java.util.Properties
        ?: return@allprojects
    group = libProps["groupId"] as String
    version = libProps[versionKey] as String

    task("printProjectVersion") {
        doLast {
            println(">> " + project.name + " version is " + version)
        }
    }
}

val dokkaHtml by tasks.getting(org.jetbrains.dokka.gradle.DokkaTask::class) {
    dokkaSourceSets {
        try {
            named("iosTest") {
                suppress.set(true)
            }
        } catch (e: Exception) {
        }
    }
}

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
                description.set("An experimental kotlin multiplatform paging library for loading reactive data in chunks")
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