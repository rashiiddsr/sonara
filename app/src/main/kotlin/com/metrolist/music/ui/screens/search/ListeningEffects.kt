/**
 * Sonara (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.metrolist.music.LocalPlayerConnection

/**
 * While the microphone listens (voice search, song recognition), Sonara's own music would drown
 * out the voice, or be "recognized" instead of the song nearby. So playback pauses for as long as
 * the sheet is open and resumes when it closes, but only if it was playing before, it is still the
 * same song, nothing resumed it meanwhile and [shouldResume] agrees (it says no once the sheet
 * starts another song, which loads a moment later). Music from other apps is left alone: the
 * song to recognize may be playing there.
 *
 * Leaving the app (screen off, home) closes the sheet through [onLeave], so the microphone never
 * keeps recording in the background.
 */
@Composable
internal fun ListeningEffects(
    onLeave: () -> Unit,
    shouldResume: () -> Boolean = { true },
) {
    val playerConnection = LocalPlayerConnection.current
    val resume by rememberUpdatedState(shouldResume)
    DisposableEffect(playerConnection) {
        val player = runCatching { playerConnection?.player }.getOrNull()
        val wasPlaying = player?.playWhenReady == true
        val songId = player?.currentMediaItem?.mediaId
        if (wasPlaying) player?.pause()
        onDispose {
            if (player != null && wasPlaying && resume() && !player.playWhenReady && player.currentMediaItem?.mediaId == songId) {
                player.play()
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val leave by rememberUpdatedState(onLeave)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) leave() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
