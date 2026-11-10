package com.ermusic.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Journeys from docs/perf.md. Run against the `benchmark` build type:
 * ./gradlew :macrobenchmark:connectedFossBenchmarkAndroidTest
 *
 * Pass -Pandroid.testInstrumentationRunnerArguments.compilation=none|partial to compare
 * without / with the bundled Baseline Profile (default: partial = profile applied).
 */
@RunWith(AndroidJUnit4::class)
class ErMusicBenchmarks {
    @get:Rule
    val rule = MacrobenchmarkRule()

    private val compilation: CompilationMode
        get() =
            when (androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("compilation")) {
                "none" -> CompilationMode.None()
                "full" -> CompilationMode.Full()
                else -> CompilationMode.Partial()
            }

    private val iterations = 5

    @Test
    fun coldStartToHome() =
        rule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilation,
            startupMode = StartupMode.COLD,
            iterations = iterations,
        ) {
            pressHome()
            startActivityAndWait()
            waitForHome()
        }

    @Test
    fun homeScroll() =
        rule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = compilation,
            startupMode = StartupMode.WARM,
            iterations = iterations,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                waitForHome()
            },
        ) {
            scrollHome()
        }

    @Test
    fun playerOpenClose() =
        rule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = compilation,
            startupMode = StartupMode.WARM,
            iterations = iterations,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                ensurePlaying()
            },
        ) {
            openAndClosePlayer()
        }

    @Test
    fun librarySongsScroll() =
        rule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = compilation,
            startupMode = StartupMode.WARM,
            iterations = iterations,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                openLibrarySongs()
            },
        ) {
            scrollLibrarySongs()
        }

    @Test
    fun openAlbum() =
        rule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = compilation,
            startupMode = StartupMode.WARM,
            iterations = iterations,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                waitForHome()
            },
        ) {
            openAlbum(SAMPLE_ALBUM_BROWSE_ID)
            device.pressBack()
        }
}
