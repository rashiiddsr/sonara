/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ermusic.desktop.window

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Only a window that has already closed may be let go in silence; real bugs must still show. */
class ErrorLogTest {
    /** The real thing, thrown by Compose itself: asking a closed scene to measure. */
    @OptIn(ExperimentalComposeUiApi::class)
    private fun realDisposedError(): Throwable {
        val scene = ImageComposeScene(100, 100) {}
        scene.close()
        return requireNotNull(runCatching { scene.calculateContentSize() }.exceptionOrNull()) { "a closed scene measured without complaint" }
    }

    @Test
    fun composesOwnErrorIsRecognised() {
        val error = realDisposedError()
        assertEquals("RootNodeOwner is already disposed", error.message)
        assertTrue("not recognised: ${error::class.simpleName}", ErrorLog.isDisposedWindow(error))
    }

    @Test
    fun itIsRecognisedWhenWrapped() {
        assertTrue(ErrorLog.isDisposedWindow(RuntimeException("wrapped", realDisposedError())))
    }

    @Test
    fun realBugsAreNot() {
        assertFalse(ErrorLog.isDisposedWindow(IllegalArgumentException("Invalid color value @android:color/white")))
        assertFalse(ErrorLog.isDisposedWindow(IllegalStateException("Something else is already disposed")))
    }
}
