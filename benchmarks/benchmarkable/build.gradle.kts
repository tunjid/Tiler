plugins {
    id("org.jetbrains.kotlin.android")
    id("android-library-convention")
}


kotlin {
    jvmToolchain(17)
}
android {
    namespace = "com.example.benchmarks"
    buildFeatures {
        compose = false
    }
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["androidx.benchmark.profiling.mode"] = "StackSampling"
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR,LOW-BATTERY"
    }
}

dependencies {
    implementation(project(":library:tiler"))
    implementation(libs.androidx.paging)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.androidx.test.rules)
    testImplementation(libs.androidx.test.runner)

    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.runner)

}