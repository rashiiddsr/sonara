import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.security.MessageDigest
import java.util.Base64
import java.util.Properties
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// AI lyric translation (owner-supplied, never asked of users): read from local.properties, which
// git ignores, or the environment. With no key, lyrics show without a translation.
val localProperties =
    Properties().apply {
        rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
    }

fun aiSetting(
    name: String,
    fallback: String,
): String = (localProperties.getProperty(name) ?: System.getenv(name))?.trim()?.takeIf { it.isNotEmpty() } ?: fallback

/**
 * The key sealed with AES-GCM, so the app carries no readable copy (opened by lyrics/SealedKey.kt,
 * which must use the same pepper). Salt and IV come from the key itself: the same key gives the
 * same output, so builds stay cacheable.
 */
fun sealKey(key: String): String {
    if (key.isEmpty()) return ""
    val seed = MessageDigest.getInstance("SHA-256").digest("sonara-seal:$key".toByteArray(Charsets.UTF_8))
    val salt = seed.copyOfRange(0, 16)
    val iv = seed.copyOfRange(16, 28)
    val secret = MessageDigest.getInstance("SHA-256").digest(salt + "Sonara/lyrics/translator/2026".toByteArray(Charsets.UTF_8))
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(secret, "AES"), GCMParameterSpec(128, iv))
    return Base64.getEncoder().encodeToString(salt + iv + cipher.doFinal(key.toByteArray(Charsets.UTF_8)))
}

// The translator key is built in: its sealed form is kept in the repository, so every build has it
// with nothing to set up. A key in local.properties (or the environment) takes its place; run
// `./gradlew :app:sealTranslatorKey --no-configuration-cache` to seal that one into the file.
val sealedKeyFile = rootProject.file("secrets/translator.sealed")
val sealedTranslatorKey: String =
    aiSetting("SONARA_AI_KEY", "").let { key ->
        if (key.isNotEmpty()) sealKey(key) else sealedKeyFile.takeIf { it.isFile }?.readText()?.trim().orEmpty()
    }

tasks.register("sealTranslatorKey") {
    description = "Seals the SONARA_AI_KEY of local.properties into secrets/translator.sealed."
    doLast {
        check(aiSetting("SONARA_AI_KEY", "").isNotEmpty()) { "Put SONARA_AI_KEY in local.properties first." }
        sealedKeyFile.parentFile.mkdirs()
        sealedKeyFile.writeText(sealedTranslatorKey + "\n")
        println("Sealed into ${sealedKeyFile.path}")
    }
}

val baseApplicationId = "com.ermusic.android"
val applicationIdOverride = System.getenv("ERMUSIC_APPLICATION_ID")?.takeIf { it.isNotBlank() }
val appNameOverride = System.getenv("ERMUSIC_APP_NAME")?.takeIf { it.isNotBlank() }
val buildCommit =
    System.getenv("ERMUSIC_BUILD_COMMIT")
        ?.trim()
        ?.takeIf { it.matches(Regex("[0-9a-fA-F]{7,40}")) }
        ?.take(7)
        ?.lowercase()
val debugKeystorePathOverride = System.getenv("ERMUSIC_DEBUG_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }
val debugKeystorePassword = System.getenv("ERMUSIC_DEBUG_KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() } ?: "android"
val debugKeyAlias = System.getenv("ERMUSIC_DEBUG_KEY_ALIAS")?.takeIf { it.isNotBlank() } ?: "androiddebugkey"
val debugKeyPassword = System.getenv("ERMUSIC_DEBUG_KEY_PASSWORD")?.takeIf { it.isNotBlank() } ?: "android"
val persistentDebugKeystoreFile = file("persistent-debug.keystore")
val workflowDebugKeystoreFile = debugKeystorePathOverride?.let(::file)

plugins {
    id("com.android.application")
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.metrolist.music"
    compileSdk = 37

    defaultConfig {
        applicationId = applicationIdOverride ?: baseApplicationId
        minSdk = 26
        targetSdk = 36
        versionCode = 2025
        versionName = "2.4.2"
        val baseVersionName = requireNotNull(versionName)
        buildConfigField("String", "BASE_VERSION_NAME", "\"$baseVersionName\"")
        buildCommit?.let { versionName = "$baseVersionName+$it" }
        resValue("string", "app_name", appNameOverride ?: "Sonara")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }

        buildConfigField("String", "ARCHITECTURE", "\"universal\"")
        // OpenAI-compatible chat endpoint; the default is Gemini's.
        buildConfigField("String", "AI_TRANSLATE_KEY_SEALED", "\"$sealedTranslatorKey\"")
        buildConfigField("String", "AI_TRANSLATE_BASE_URL", "\"${aiSetting("SONARA_AI_BASE_URL", "https://generativelanguage.googleapis.com/v1beta/openai/")}\"")
        buildConfigField("String", "AI_TRANSLATE_MODEL", "\"${aiSetting("SONARA_AI_MODEL", "gemini-flash-lite-latest")}\"")
        // Tried in turn when the model above is busy or fails; comma separated.
        buildConfigField("String", "AI_TRANSLATE_FALLBACK_MODELS", "\"${aiSetting("SONARA_AI_FALLBACK_MODELS", "gemini-3.5-flash-lite,gemini-3.8-flash,gemini-flash-latest")}\"")
    }

    // ER-Music ships a single FOSS flavor: no Cast, no in-app updater.
    flavorDimensions += listOf("variant")
    productFlavors {
        create("foss") {
            dimension = "variant"
            isDefault = true
            buildConfigField("Boolean", "CAST_AVAILABLE", "false")
            buildConfigField("Boolean", "UPDATER_AVAILABLE", "false")
        }
    }

    signingConfigs {
        create("persistentDebug") {
            storeFile = persistentDebugKeystoreFile
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("workflowDebug") {
            storeFile = workflowDebugKeystoreFile ?: persistentDebugKeystoreFile
            storePassword = debugKeystorePassword
            keyAlias = debugKeyAlias
            keyPassword = debugKeyPassword
        }
        create("release") {
            storeFile = file("keystore/release.keystore")
            storePassword = System.getenv("STORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
            enableV1Signing = true
            enableV2Signing = true
        }
        getByName("debug") {
            keyAlias = "androiddebugkey"
            keyPassword = "android"
            storePassword = "android"
            storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isCrunchPngs = false
            isDebuggable = false
            if (System.getenv("STORE_PASSWORD") != null) signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        // Release-equivalent build (R8 + resource shrinking) signed with the debug key and
        // profileable, used by :macrobenchmark. Never judge performance on a debug build.
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
            isProfileable = true
        }
        debug {
            if (applicationIdOverride == null) {
                applicationIdSuffix = ".debug"
            }
            isDebuggable = true
            if (appNameOverride == null) {
                resValue("string", "app_name", "Sonara Debug")
            }
            signingConfig =
                if (workflowDebugKeystoreFile != null) {
                    signingConfigs.getByName("workflowDebug")
                } else if (persistentDebugKeystoreFile.exists()) {
                    signingConfigs.getByName("persistentDebug")
                } else {
                    signingConfigs.getByName("debug")
                }
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        jvmToolchain(21)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        lintConfig = file("lint.xml")
        warningsAsErrors = false
        abortOnError = false
        checkDependencies = false
        // Lint never gated anything here (abortOnError = false), so the
        // lintVital pass that assembleRelease implicitly triggers was pure
        // build time. Run lint on demand with ./gradlew :app:lintFossDebug.
        checkReleaseBuilds = false
    }

    androidResources {
        // The app follows the phone language; no per-app language picker.
        generateLocaleConfig = false
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
            keepDebugSymbols +=
                listOf(
                    "**/libandroidx.graphics.path.so",
                    "**/libdatastore_shared_counter.so",
                )
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/NOTICE.md"
            excludes += "META-INF/CONTRIBUTORS.md"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

composeCompiler {
    stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("app/compose_stability.conf"))
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.RequiresOptIn",
        )
        suppressWarnings.set(false)
    }
}

// Android provides org.json as a platform API (/apex/com.android.art/javalib/core-libart.jar).
// The standalone org.json:json artefact bundles an older Apache Harmony copy of JSONArray that
// contains an internal `myArrayList` field absent from the platform class.  Without obfuscation
// R8 inlines against this internal field; at runtime the platform class is resolved instead,
// producing a NoSuchFieldError.  Excluding the artefact globally ensures only the platform
// class is ever referenced.
configurations.configureEach {
    exclude(group = "org.json", module = "json")
}

dependencies {
    implementation(libs.guava)
    implementation(libs.coroutines.guava)

    implementation(libs.activity)
    implementation(libs.hilt.navigation)
    implementation(libs.datastore)

    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.util)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.animation)
    implementation(libs.compose.reorderable)

    implementation(libs.viewmodel.compose)
    implementation(libs.lifecycle.process)

    implementation(libs.material3)
    implementation(libs.palette)
    implementation(libs.materialKolor)

    implementation(libs.appcompat)

    implementation(libs.coil)
    implementation(libs.coil.network.okhttp)
    implementation(libs.browser)

    implementation(libs.ucrop)

    implementation(libs.shimmer)

    implementation(libs.media3)
    implementation(libs.media3.session)
    // Google Play services: Cast (experiment).
    implementation(libs.cast.framework)
    implementation(libs.mediarouter)
    implementation(libs.media3.okhttp)

    implementation(libs.room.runtime)
    ksp(libs.room.compiler)

    implementation(libs.hilt)
    ksp(libs.hilt.compiler)

    implementation(project(":innertube"))

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.encoding)
    implementation(libs.ktor.serialization.json)

    coreLibraryDesugaring(libs.desugaring)

    implementation(libs.timber)

    implementation(libs.profileinstaller)
    "baselineProfile"(project(":baselineprofile"))

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.ktor.client.mock)
}
