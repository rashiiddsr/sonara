import java.security.MessageDigest
import java.util.Base64
import java.util.Properties
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.serialization") version "2.4.20"
    kotlin("plugin.compose") version "2.4.20"
    id("org.jetbrains.compose") version "1.12.2"
}

repositories {
    google()
    mavenCentral()
    maven("https://jitpack.io")
}

kotlin {
    jvmToolchain(21)
    // The YouTube Music client is plain Kotlin: shared with the Android app, source for source.
    sourceSets["main"].kotlin.srcDir("../innertube/src/main/kotlin")
}

/**
 * The translator key sealed with AES-GCM, so the app carries no readable copy; SealedKey.kt opens
 * it and must use the same pepper (as the Android app does). Salt and IV come from the key itself,
 * so the same key gives the same code and builds stay cacheable.
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

val secretsDir = layout.buildDirectory.dir("generated/secrets")
val generateSecrets by tasks.registering {
    val source = rootProject.file("../local.properties")
    // The built-in key, sealed (see app/build.gradle.kts); a key in local.properties takes its place.
    val sealedFile = rootProject.file("../secrets/translator.sealed")
    if (sealedFile.exists()) inputs.file(sealedFile)
    val target = secretsDir.map { it.file("com/ermusic/desktop/BuildSecrets.kt") }
    if (source.exists()) inputs.file(source)
    outputs.file(target)
    doLast {
        val values = Properties().apply { if (source.exists()) source.inputStream().use(::load) }
        fun setting(name: String, default: String) = (values.getProperty(name) ?: System.getenv(name))?.trim()?.takeIf { it.isNotBlank() } ?: default
        val file = target.get().asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            package com.ermusic.desktop

            internal object BuildSecrets {
                private const val AI_KEY_SEALED = "${setting("SONARA_AI_KEY", "").let { if (it.isNotEmpty()) sealKey(it) else sealedFile.takeIf { f -> f.isFile }?.readText()?.trim().orEmpty() }}"
                val AI_KEY: String by lazy { SealedKey.open(AI_KEY_SEALED) }
                const val AI_BASE_URL = "${setting("SONARA_AI_BASE_URL", "https://generativelanguage.googleapis.com/v1beta/openai/")}"
                const val AI_MODEL = "${setting("SONARA_AI_MODEL", "gemini-flash-lite-latest")}"
                const val AI_FALLBACK_MODELS = "${setting("SONARA_AI_FALLBACK_MODELS", "gemini-3.5-flash-lite,gemini-3.8-flash,gemini-flash-latest")}"
            }
            """.trimIndent(),
        )
    }
}
kotlin.sourceSets["main"].kotlin.srcDir(generateSecrets.map { secretsDir.get() })

// The Android app's lyric sources and parser, compiled as they are (see android/ and music/utils here).
val fromAndroid by tasks.registering(Sync::class) {
    from("../app/src/main/kotlin/com/metrolist") {
        include("kugou/**", "lrclib/**", "paxsenix/**", "music/betterlyrics/**")
        include("music/utils/YouTubeWebSession.kt", "music/utils/YouTubePlaylistEditor.kt")
        listOf(
            "LyricsProvider", "LyricsUtils", "LyricsEntry", "BetterLyricsProvider", "LrcLibLyricsProvider", "KuGouLyricsProvider",
            "PaxsenixLyricsProvider", "LyricsPlusProvider", "ZemerLyricsProvider", "YouTubeLyricsProvider", "YouTubeSubtitleLyricsProvider",
        ).forEach { include("music/lyrics/$it.kt") }
    }
    into(layout.buildDirectory.dir("generated/fromAndroid"))
}
kotlin.sourceSets["main"].kotlin.srcDir(fromAndroid)

val appVersion = "2.3.5"
val ktor = "3.6.0"
val ffmpegPresets = "1.5.14"

// FFmpeg's natives for the system being built on: jpackage only packages for the system it runs on.
val buildsOnMac = System.getProperty("os.name").startsWith("Mac", ignoreCase = true)
val nativePlatform =
    providers.gradleProperty("sonara.natives").orNull
        ?: when {
            buildsOnMac && System.getProperty("os.arch") == "aarch64" -> "macosx-arm64"
            buildsOnMac -> "macosx-x86_64"
            else -> "windows-x86_64"
        }

dependencies {
    implementation(
        when (nativePlatform) {
            "macosx-arm64" -> compose.desktop.macos_arm64
            "macosx-x86_64" -> compose.desktop.macos_x64
            else -> compose.desktop.currentOs
        },
    )
    implementation(compose.material3)
    implementation("com.github.MetrolistGroup.innertubex:innertubex:v0.7.4")
    implementation("io.ktor:ktor-client-core:$ktor")
    implementation("io.ktor:ktor-client-okhttp:$ktor")
    implementation("io.ktor:ktor-client-content-negotiation:$ktor")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor")
    implementation("io.ktor:ktor-client-encoding:$ktor")
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
    implementation("net.java.dev.jna:jna-platform:5.17.0")
    implementation("org.json:json:20250517")

    // Audio decoding: FFmpeg, natives for this system only, from Maven Central.
    implementation("org.bytedeco:javacv:$ffmpegPresets") {
        listOf(
            "opencv", "openblas", "flycapture", "libdc1394", "libfreenect", "libfreenect2",
            "librealsense", "librealsense2", "videoinput", "artoolkitplus", "flandmark",
            "leptonica", "tesseract",
        ).forEach { exclude(group = "org.bytedeco", module = it) }
    }
    implementation("org.bytedeco:javacpp:$ffmpegPresets:$nativePlatform")
    implementation("org.bytedeco:ffmpeg:8.1.2-$ffmpegPresets:$nativePlatform")

    // UI tests run headless: ./gradlew -p desktop test
    testImplementation(compose.desktop.uiTestJUnit4)
}

compose.desktop {
    application {
        mainClass = "com.ermusic.desktop.MainKt"
        // The Mac's title bar and menus follow its light or dark appearance; Windows ignores these.
        jvmArgs += listOf("-Xmx512m", "-Dfile.encoding=UTF-8", "-Dapple.awt.application.appearance=system", "-Dapple.awt.application.name=Sonara")
        nativeDistributions {
            // Each system builds its own: Windows the .msi and .exe, a Mac the .dmg.
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Dmg)
            packageName = "Sonara"
            // Bumped for every installer: Windows only upgrades to a higher version.
            packageVersion = appVersion
            vendor = "Sonara"
            description = "Sonara"
            licenseFile.set(project.file("../LICENSE"))
            modules(
                "java.instrument", "java.management", "java.naming", "java.net.http", "java.sql",
                "jdk.crypto.ec", "jdk.unsupported", "java.desktop",
            )
            windows {
                iconFile.set(project.file("icon.ico"))
                menuGroup = "Sonara"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                // Fixed, so a new version replaces the old one instead of installing beside it.
                upgradeUuid = "6f3b1c52-8a4e-4c1b-9d57-2e7c5a90b1d3"
            }
            macOS {
                iconFile.set(project.file("icon.icns"))
                bundleID = "app.sonara.desktop"
                dockName = "Sonara"
                appCategory = "public.app-category.music"
                packageVersion = appVersion
                dmgPackageVersion = appVersion
                minimumSystemVersion = "12.0"
            }
        }
    }
}

// The app's jars with another system's natives (-Psonara.natives=macosx-arm64), for putting a Mac
// app together away from a Mac (mac/make_app.py). On a Mac itself, packageDmg is the way.
tasks.register<Sync>("stageAppJars") {
    from(tasks.named("jar"))
    from(configurations.named("runtimeClasspath"))
    into(layout.buildDirectory.dir("stage/$nativePlatform"))
}

// The installer: jpackage's own, from wix/main.wxs, with Sonara's pictures and "Open Sonara" at the end.
tasks.register<Exec>("packageInstaller") {
    dependsOn("createDistributable", "unzipWix")
    val output = layout.buildDirectory.dir("compose/binaries/main/msi").get().asFile
    val wix = layout.buildDirectory.dir("wix311").get().asFile
    val wixResources = project.file("wix")
    val tool = org.gradle.internal.jvm.Jvm.current().javaHome.resolve("bin/jpackage.exe")
    doFirst {
        output.mkdirs()
        output.listFiles().orEmpty().forEach { it.delete() }
        environment("PATH", wix.path + ";" + System.getenv("PATH"))
        environment("SONARA_WIX", wixResources.path)
    }
    commandLine(
        tool.path,
        "--type", "msi",
        "--dest", output.path,
        "--name", "Sonara",
        "--app-image", layout.buildDirectory.dir("compose/binaries/main/app/Sonara").get().asFile.path,
        "--app-version", appVersion,
        "--vendor", "Sonara",
        "--description", "Sonara",
        "--license-file", project.file("../LICENSE").path,
        "--resource-dir", wixResources.path,
        "--win-per-user-install",
        "--win-menu",
        "--win-menu-group", "Sonara",
        "--win-shortcut",
        "--win-dir-chooser",
        "--win-upgrade-uuid", "6f3b1c52-8a4e-4c1b-9d57-2e7c5a90b1d3",
    )
}
