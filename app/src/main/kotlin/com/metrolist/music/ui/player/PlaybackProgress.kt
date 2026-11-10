/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.playback.PlayerConnection
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Playback position holder shared by the mini player and the full player.
 *
 * Only small leaf composables (slider draw phase, time labels, lyrics) read [position], so the
 * ticking position never recomposes the player root.
 */
@Stable
class PlaybackProgress(
    initialPosition: Long,
    initialDuration: Long,
) {
    var position by mutableLongStateOf(initialPosition)
    var duration by mutableLongStateOf(initialDuration)

    val fraction: Float
        get() = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
}

@Composable
fun rememberPlaybackProgress(playerConnection: PlayerConnection): PlaybackProgress {
    val player = playerConnection.player
    val progress =
        remember(playerConnection) {
            PlaybackProgress(
                initialPosition = runCatching { player.currentPosition }.getOrDefault(0L),
                initialDuration = runCatching { player.duration }.getOrDefault(0L).coerceAtLeast(0L),
            )
        }
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaId by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    LaunchedEffect(playerConnection, isPlaying, mediaId?.id) {
        while (isActive) {
            progress.position = runCatching { player.currentPosition }.getOrDefault(0L)
            val d = runCatching { player.duration }.getOrDefault(0L)
            progress.duration =
                if (d > 0) d else (mediaId?.duration?.takeIf { it > 0 }?.times(1000L) ?: 0L)
            delay(if (isPlaying) 200L else 1000L)
        }
    }
    return progress
}

fun formatTime(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000L)
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
