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

                implementation(libs.compose.ui.tooling)
                implementation(libs.compose.ui.util)

                implementation(libs.compose.runtime)
                implementation(libs.compose.animation)
                implementation(libs.compose.material)
                implementation(libs.compose.foundation.layout)

                implementation(libs.kotlinx.coroutines.core)
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
    }
}

android {
    compileSdk = 31

    defaultConfig {
        minSdk = 21
        targetSdk = 31
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    sourceSets {
        named("main") {
            manifest.srcFile("src/androidMain/AndroidManifest.xml")
            res.srcDirs("src/androidMain/res")
        }
    }
}

