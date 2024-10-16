plugins {
    id("com.android.library")
    id("kotlin-android")
    id("androidx.benchmark")
    id("android-library-convention")
}

kotlin {
    jvmToolchain(17)
}
android {
    namespace = "com.example.microbenchmark"

    defaultConfig {
         testInstrumentationRunnerArguments["androidx.benchmark.profiling.mode"] = "StackSampling"
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR,LOW-BATTERY"
    }

    testBuildType = "release"

    buildFeatures {
        compose = false
    }
    buildTypes {
        getByName("release") {
            // The androidx.benchmark plugin configures release buildType with proper settings, such as:
            // - disables code coverage
            // - adds CPU clock locking task
            // - signs release buildType with debug signing config
            // - copies benchmark results into build/outputs/connected_android_test_additional_output folder
        }
    }
}

dependencies {
    androidTestImplementation(project(":benchmarks:benchmarkable"))
    androidTestImplementation(libs.androidx.benchmark.junit)
    androidTestImplementation(libs.androidx.benchmark.macro)
    androidTestImplementation(libs.androidx.test.junit)


    androidTestImplementation(libs.kotlinx.coroutines.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
