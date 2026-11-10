plugins {
    id("com.android.test")
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.ermusic.baselineprofile"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // The only device available during development is an emulator.
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
    }

    flavorDimensions += listOf("variant")
    productFlavors {
        create("foss") { dimension = "variant" }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    targetProjectPath = ":app"
}

kotlin {
    jvmToolchain(21)
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.uiautomator)
    implementation(libs.benchmark.macro.junit4)
}
