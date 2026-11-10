/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import com.metrolist.music.playback.PlayerConnection
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Share of the width that changes the song on release. */
private const val DISTANCE_FRACTION = 0.25f

/** A quick flick changes the song even when it travelled less than [DISTANCE_FRACTION]. */
private val FlickVelocity = 600.dp
private const val SLIDE_MS = 160

/**
 * Swipe left for the next song, right for the previous one, like YouTube Music's mini player and
 * artwork. The content follows the finger 1:1; a quarter of the width or a quick flick is enough.
 * The current song slides out and the new one slides in from the other side; a short, slow swipe
 * springs back. Vertical drags are left to the sheet.
 */
fun Modifier.swipeToSkip(playerConnection: PlayerConnection): Modifier =
    composed {
        val offset = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()
        this
            .pointerInput(playerConnection) {
                val tracker = VelocityTracker()
                val flick = FlickVelocity.toPx()
                detectHorizontalDragGestures(
                    onDragStart = { tracker.resetTracking() },
                    onDragEnd = {
                        val dx = offset.value
                        val width = size.width.toFloat()
                        val velocity = tracker.calculateVelocity().x
                        val towardNext = dx < 0 && (dx <= -width * DISTANCE_FRACTION || velocity <= -flick)
                        val towardPrevious = dx > 0 && (dx >= width * DISTANCE_FRACTION || velocity >= flick)
                        val direction = when {
                            towardNext && playerConnection.player.hasNextMediaItem() -> -1f
                            towardPrevious -> 1f
                            else -> 0f
                        }
                        scope.launch {
                            if (direction == 0f) {
                                offset.animateTo(0f, spring())
                                return@launch
                            }
                            offset.animateTo(direction * width, tween(SLIDE_MS))
                            if (direction < 0) playerConnection.seekToNext() else playerConnection.seekToPrevious()
                            offset.snapTo(-direction * width)
                            offset.animateTo(0f, tween(SLIDE_MS))
                        }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f, spring()) } },
                ) { change, dragAmount ->
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    scope.launch { offset.snapTo(offset.value + dragAmount) }
                }
            }.graphicsLayer {
                translationX = offset.value
                alpha = 1f - (abs(offset.value) / (size.width.coerceAtLeast(1f))).coerceIn(0f, 0.5f)
            }
    }
