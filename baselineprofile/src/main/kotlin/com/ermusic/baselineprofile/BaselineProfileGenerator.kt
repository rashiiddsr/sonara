package com.ermusic.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the Baseline Profile bundled into :app.
 * ./gradlew :app:generateFossReleaseBaselineProfile
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() =
        rule.collect(
            packageName = TARGET_PACKAGE,
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()
            waitForHome()
            scrollHome()
            runCatching {
                ensurePlaying()
                openAndClosePlayer()
            }
            runCatching {
                openLibrarySongs()
                scrollLibrarySongs()
            }
            runCatching {
                openAlbum(SAMPLE_ALBUM_BROWSE_ID)
                device.pressBack()
            }
        }
}
