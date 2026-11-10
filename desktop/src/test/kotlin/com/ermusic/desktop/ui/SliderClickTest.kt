/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ermusic.desktop.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Full screen pauses on a click on its background; a click on a slider over it must not. */
class SliderClickTest {
    @get:Rule
    val rule = createComposeRule()

    private var backgroundTaps = 0
    private var volume = -1f

    private fun showSliderOverPausingBackground() {
        rule.setContent {
            MaterialTheme {
                Box(
                    Modifier
                        .size(400.dp)
                        .testTag("background")
                        .pointerInput(Unit) { detectTapGestures(onTap = { backgroundTaps++ }) },
                ) {
                    ThinSlider(value = 0.5f, onChange = { volume = it }, modifier = Modifier.width(200.dp).align(Alignment.BottomEnd).testTag("volume"))
                }
            }
        }
    }

    @Test
    fun clickOnVolumeSetsVolumeWithoutPausing() {
        showSliderOverPausingBackground()
        rule.onNodeWithTag("volume").performClick()
        rule.waitForIdle()
        assertTrue("the slider took the click", volume >= 0f)
        assertEquals("the background must not see the click", 0, backgroundTaps)
    }

    @Test
    fun clickOnBackgroundStillPauses() {
        showSliderOverPausingBackground()
        rule.onNodeWithTag("background").performTouchInput { click(Offset(20f, 20f)) }
        rule.waitForIdle()
        assertEquals(1, backgroundTaps)
    }
}
