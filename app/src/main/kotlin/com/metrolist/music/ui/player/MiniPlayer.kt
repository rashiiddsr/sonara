/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.widthIn
import com.metrolist.music.ui.theme.rememberWindowLayout
import com.metrolist.music.ui.theme.WindowLayout
import com.metrolist.music.ui.theme.Motion
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.ui.platform.LocalContext
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.ui.component.ProgressLine
import com.metrolist.music.ui.theme.ErShape
import com.metrolist.music.ui.theme.Size
import com.metrolist.music.ui.theme.Spacing

/**
 * Floating mini player: artwork, title/artist, play/pause and next, with a progress line on the
 * bottom edge. Tapping it is handled by the sheet (it expands the full player); swiping sideways
 * changes the song.
 */
@Composable
fun MiniPlayer(
    progress: PlaybackProgress,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val canSkipNext by playerConnection.canSkipNext.collectAsStateWithLifecycle()
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsStateWithLifecycle()
    val currentSong by playerConnection.currentSong.collectAsStateWithLifecycle(initialValue = null)
    val metadata = mediaMetadata ?: return
    // A tablet gets the desktop's player bar: the song on the left, the controls in the middle,
    // like on the right, as wide as the pages.
    val wide = rememberWindowLayout().wide
    val context = LocalContext.current
    val thumbPx = with(LocalDensity.current) { Size.MiniPlayerThumb.roundToPx() }
    // Tinted with the cover's colour, like the full player's glow: a pastel of it on Sonar Day,
    // a deep shade of it on Sonar Night. Shares the player's artwork cache, so no extra load.
    val artwork = rememberPlayerArtwork(metadata.thumbnailUrl)
    val surface = MaterialTheme.colorScheme.surfaceContainerHigh
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val tint = artwork?.accent?.let { lerp(surface, it, if (dark) MINI_TINT_DARK else MINI_TINT_LIGHT) } ?: surface
    val cardColor by animateColorAsState(tint, tween(Motion.ArtworkCrossfade), label = "miniPlayerTint")

    Box(
        modifier = modifier
            .testTag("mini_player_card")
            .fillMaxWidth()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = if (wide) WindowLayout.ContentMaxWidth else Dp.Infinity)
            .fillMaxWidth()
            .height(Size.MiniPlayer)
            .padding(horizontal = Spacing.S)
            .shadow(Spacing.S, ErShape.Medium, ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.Black.copy(alpha = 0.3f))
            .clip(ErShape.Medium)
            .background(cardColor),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(Size.MiniPlayer)
                .swipeToSkip(playerConnection)
                .padding(start = Spacing.S + Spacing.XXS, end = Spacing.XS),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(metadata.thumbnailUrl)
                    .size(thumbPx)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .size(Size.MiniPlayerThumb)
                    .clip(ErShape.Small)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
            Spacer(Modifier.width(Spacing.M))
            Column(Modifier.weight(1f)) {
                Text(
                    text = metadata.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = metadata.artists.joinToString { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (wide) {
                IconButton(onClick = playerConnection::seekToPrevious, enabled = canSkipPrevious) {
                    Icon(
                        painter = painterResource(R.drawable.skip_previous),
                        contentDescription = stringResource(R.string.er_previous),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            IconButton(onClick = playerConnection::togglePlayPause) {
                Icon(
                    painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                    contentDescription = stringResource(if (isPlaying) R.string.er_pause else R.string.er_play),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = playerConnection::seekToNext, enabled = canSkipNext) {
                Icon(
                    painter = painterResource(R.drawable.skip_next),
                    contentDescription = stringResource(R.string.er_next),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (wide) {
                // The controls sit in the middle: the like button balances the song's side.
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                    val liked = currentSong?.song?.liked ?: metadata.liked
                    IconButton(onClick = playerConnection::toggleLike) {
                        Icon(
                            painter = painterResource(if (liked) R.drawable.favorite else R.drawable.favorite_border),
                            contentDescription = stringResource(R.string.er_like),
                            tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
        ProgressLine(
            progress = { progress.fraction },
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = Spacing.M),
        )
    }
}

private const val MINI_TINT_DARK = 0.32f
private const val MINI_TINT_LIGHT = 0.24f
