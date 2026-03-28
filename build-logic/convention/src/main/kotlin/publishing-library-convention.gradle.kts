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
    id("com.vanniktech.maven.publish")
    signing
    id("org.jetbrains.dokka")
    id("pl.allegro.tech.build.axion-release")
}

scmVersion {
    tag {
        // Use an empty string for prefix
        prefix.set("v")
    }
    nextVersion {
        suffix.set("alpha")
        separator.set("-")
    }
    repository {
        pushTagsOnly.set(true)
    }
    providers.gradleProperty("library.releaseBranch")
        .orNull
        ?.let { releaseBranch ->
            when {
                releaseBranch.contains("bugfix/") -> versionIncrementer("incrementPatch")
                releaseBranch.contains("feature/") -> versionIncrementer("incrementMinor")
                releaseBranch.contains("release/") -> versionIncrementer("incrementMajor")
                else -> throw IllegalArgumentException("Unknown release type")
            }
        }
}

allprojects {
    val libProps = rootProject.ext.get("libProps") as? java.util.Properties
        ?: return@allprojects
    group = libProps["groupId"] as String
    version = scmVersion.version

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

mavenPublishing {
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

    val username = project.providers.gradleProperty("mavenCentralUsername")
    val password = project.providers.gradleProperty("mavenCentralPassword")

    if (username.isPresent && password.isPresent) {
        publishToMavenCentral()
        signAllPublications()
    }
}

signing {
    val signingKey = project.providers
        .gradleProperty("signingInMemoryKey")
        .orNull

    val signingPassword = project.providers
        .gradleProperty("signingInMemoryKeyPassword")
        .orNull

    if (signingKey != null && signingPassword != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications)
    }
}

val signingTasks = tasks.withType<Sign>()
tasks.withType<AbstractPublishToMaven>().configureEach {
    dependsOn(signingTasks)
}
