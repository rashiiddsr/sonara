/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import com.metrolist.music.ui.theme.ErMusicTheme
import com.metrolist.music.ui.theme.Size
import com.metrolist.music.ui.theme.Spacing

/**
 * Plain seek slider for the player.
 *
 * [progress] is a lambda that is only read in the draw phase, so playback position updates
 * redraw the track without recomposing anything. While the user drags, the slider shows the
 * drag position and reports it through [onSeeking]; [onSeekFinished] fires on release.
 */
@Composable
fun ErSlider(
    progress: () -> Float,
    onSeekFinished: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onSeeking: (Float?) -> Unit = {},
    activeColor: Color = MaterialTheme.colorScheme.onSurface,
    inactiveColor: Color = activeColor.copy(alpha = 0.2f),
    enabled: Boolean = true,
) {
    var dragFraction by remember { mutableFloatStateOf(-1f) }
    var pressed by remember { mutableStateOf(false) }
    val thumb by animateDpAsState(if (pressed) Size.SliderThumbActive else Size.SliderThumb, label = "thumb")
    val track by animateDpAsState(if (pressed) Size.SliderTrack * 1.5f else Size.SliderTrack, label = "track")
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val currentOnSeeking by rememberUpdatedState(onSeeking)
    val currentOnSeekFinished by rememberUpdatedState(onSeekFinished)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Size.IconButton - Spacing.S)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(progress().coerceIn(0f, 1f), 0f..1f)
                if (enabled) {
                    setProgress { value ->
                        currentOnSeekFinished(value.coerceIn(0f, 1f))
                        true
                    }
                }
            }.pointerInput(enabled, rtl) {
                if (!enabled) return@pointerInput
                fun fractionAt(x: Float): Float {
                    val f = (x / size.width).coerceIn(0f, 1f)
                    return if (rtl) 1f - f else f
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    pressed = true
                    dragFraction = fractionAt(down.position.x)
                    currentOnSeeking(dragFraction)
                    horizontalDrag(down.id) { change ->
                        dragFraction = fractionAt(change.position.x)
                        currentOnSeeking(dragFraction)
                        if (change.positionChange() != Offset.Zero) change.consume()
                    }
                    currentOnSeekFinished(dragFraction)
                    currentOnSeeking(null)
                    pressed = false
                    dragFraction = -1f
                }
            }.drawBehind {
                val fraction = (if (dragFraction >= 0f) dragFraction else progress()).coerceIn(0f, 1f)
                val y = size.height / 2f
                val trackPx = track.toPx()
                val start = if (rtl) size.width else 0f
                val end = if (rtl) 0f else size.width
                val x = start + (end - start) * fraction
                drawLine(inactiveColor, Offset(start, y), Offset(end, y), trackPx, StrokeCap.Round)
                drawLine(activeColor, Offset(start, y), Offset(x, y), trackPx, StrokeCap.Round)
                drawCircle(activeColor, radius = thumb.toPx() / 2f, center = Offset(x, y))
            },
    )
}

/** 2dp progress line, e.g. along the bottom edge of the mini player. */
@Composable
fun ProgressLine(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(Spacing.XXS)
            .drawBehind {
                drawRect(color, size = size.copy(width = size.width * progress().coerceIn(0f, 1f)))
            },
    )
}

@Preview(name = "Slider light")
@Composable
private fun SliderPreviewLight() {
    ErMusicTheme(darkTheme = false) {
        Column(Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.L)) {
            ErSlider(progress = { 0.35f }, onSeekFinished = {})
            ProgressLine(progress = { 0.6f })
        }
    }
}

@Preview(name = "Slider dark")
@Composable
private fun SliderPreviewDark() {
    ErMusicTheme(darkTheme = true) {
        Column(Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.L)) {
            ErSlider(progress = { 0.35f }, onSeekFinished = {})
            ProgressLine(progress = { 0.6f })
        }
    }
}
