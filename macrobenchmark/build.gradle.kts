plugins {
    id("com.android.test")
}

android {
    namespace = "com.ermusic.macrobenchmark"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
    }

    flavorDimensions += listOf("variant")
    productFlavors {
        create("foss") { dimension = "variant" }
    }

    buildTypes {
        // Mirrors :app's "benchmark" build type (R8, debug-signed, profileable).
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.uiautomator)
    implementation(libs.benchmark.macro.junit4)
}

androidComponents {
    beforeVariants(selector().all()) {
        it.enable = it.buildType == "benchmark"
    }
}
