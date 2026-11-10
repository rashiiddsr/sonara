/**
 * Sonara (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.player

import androidx.media3.common.Player
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.displayCutout
import com.metrolist.music.ui.utils.fadingEdge
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.music.R
import com.metrolist.music.extensions.metadata
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.ui.component.BottomSheetState
import com.metrolist.music.ui.theme.ErShape
import com.metrolist.music.ui.theme.Motion
import com.metrolist.music.ui.theme.Size
import com.metrolist.music.ui.theme.Spacing
import com.metrolist.music.ui.theme.WindowLayout

/** What the panel beside the player shows; none at first. */
private enum class SidePanel { Lyrics, Queue }

/**
 * The player on a tablet in landscape. It opens as the full player, in the middle: cover, title,
 * controls and actions. Lyrics or Queue slide the player to the left and bring their panel in
 * from the right; the same button (or back) closes it and the player glides back to the middle.
 * The full-screen button in the top row turns the tablet into a now-playing screen, like a TV.
 */
@Composable
internal fun TabletPlayer(
    state: BottomSheetState,
    navController: NavController,
    playerConnection: PlayerConnection,
    metadata: MediaMetadata,
    progress: PlaybackProgress,
    seekPreview: androidx.compose.runtime.MutableState<Long?>,
    @Suppress("UNUSED_PARAMETER") layout: WindowLayout,
) {
    var panel by rememberSaveable { mutableStateOf<SidePanel?>(null) }
    var nowPlaying by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = panel != null && !nowPlaying) { panel = null }

    if (nowPlaying) {
        NowPlayingScreen(metadata, playerConnection, progress, seekPreview, onExit = { nowPlaying = false })
        return
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = Spacing.XL, vertical = Spacing.M),
    ) {
        val playerWidth = playerPaneWidth(maxWidth)
        // In the middle on its own; at the left edge while a panel is open.
        val playerX by animateDpAsState(
            targetValue = if (panel == null) (maxWidth - playerWidth) / 2 else 0.dp,
            animationSpec = Motion.medium(),
            label = "playerSlide",
        )
        Column(
            Modifier
                .offset(x = playerX)
                .width(playerWidth)
                .fillMaxHeight(),
        ) {
            TopRow(
                state,
                playerConnection,
                metadata,
                extraAction = {
                    IconButton(onClick = { nowPlaying = true }) {
                        Icon(painterResource(R.drawable.fullscreen), contentDescription = stringResource(R.string.er_now_playing_fullscreen))
                    }
                },
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = Spacing.M).swipeToSkip(playerConnection),
            ) { PlayerArtwork(metadata) }
            Controls(state, navController, playerConnection, metadata, progress, seekPreview)
            Spacer(Modifier.height(Spacing.L))
            TabletActionRow(
                panel = panel,
                onPanel = { panel = if (panel == it) null else it },
                playerConnection = playerConnection,
            )
            Spacer(Modifier.height(Spacing.S))
        }
        AnimatedVisibility(
            visible = panel != null,
            enter = fadeIn(Motion.medium()) + slideInHorizontally(Motion.medium()) { it / 3 },
            exit = fadeOut(Motion.fast()) + slideOutHorizontally(Motion.medium()) { it / 3 },
            modifier = Modifier
                .padding(start = playerWidth + Spacing.XXL)
                .fillMaxSize(),
        ) {
            // Keeps showing the last panel while it slides out.
            var shown by remember { mutableStateOf(SidePanel.Lyrics) }
            panel?.let { shown = it }
            PanelArea(shown, metadata, seekPreview, state, Modifier.fillMaxSize())
        }
    }
}

/** The player column: a third of the width, never cramped, never more than half. */
private fun playerPaneWidth(available: Dp): Dp = (available * PLAYER_PANE_SHARE).coerceIn(WindowLayout.SidePaneWidth, available / 2)

@Composable
private fun PanelArea(
    panel: SidePanel,
    metadata: MediaMetadata,
    seekPreview: androidx.compose.runtime.State<Long?>,
    state: BottomSheetState,
    modifier: Modifier,
) {
    Column(modifier) {
        if (panel == SidePanel.Lyrics) {
            Row(Modifier.fillMaxWidth().height(Size.TopBar), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.lyrics), style = MaterialTheme.typography.titleMedium)
            }
        } else {
            // The queue card has its own "Up next" heading; keep the row so the card lines up.
            Spacer(Modifier.height(Size.TopBar))
        }
        AnimatedContent(
            targetState = panel,
            transitionSpec = { fadeIn(Motion.medium()) togetherWith fadeOut(Motion.fast()) },
            label = "tabletPanel",
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { target ->
            when (target) {
                // Lyrics sit on the cover's glow, no card: a reading column, centred.
                SidePanel.Lyrics ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        LyricsPage(
                            mediaMetadata = metadata,
                            sliderPositionProvider = { seekPreview.value },
                            modifier = Modifier.widthIn(max = WindowLayout.ReadingMaxWidth).fillMaxSize(),
                        )
                    }
                // The queue is a list, so it gets a soft card to sit on.
                SidePanel.Queue ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(bottom = Spacing.M)
                            .clip(ErShape.Large)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = QUEUE_CARD_ALPHA)),
                    ) {
                        QueuePage(playerBottomSheetState = state, modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.M))
                    }
            }
        }
    }
}

/**
 * Full screen, like a TV's now-playing screen, in two balanced halves: on the left the cover,
 * large, with the title, the time and small controls under it; on the right the lyrics as they
 * play, a short window of about five lines that fades at its edges. The Sonara mark sits top
 * left, the song coming up next bottom right. System bars hide and the screen stays on; back or
 * the button in the corner returns.
 */
@Composable
private fun NowPlayingScreen(
    metadata: MediaMetadata,
    playerConnection: PlayerConnection,
    progress: PlaybackProgress,
    seekPreview: androidx.compose.runtime.State<Long?>,
    onExit: () -> Unit,
) {
    BackHandler(onBack = onExit)
    ImmersiveWhileShown()
    val repeatMode by playerConnection.repeatMode.collectAsStateWithLifecycle()
    val queue by playerConnection.queueWindows.collectAsStateWithLifecycle()
    val upNext =
        remember(metadata.id, queue) {
            val player = runCatching { playerConnection.player }.getOrNull()
            player?.nextMediaItemIndex?.takeIf { it != C.INDEX_UNSET }?.let { player.getMediaItemAt(it).metadata }
        }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.displayCutout)
            .padding(horizontal = NOW_PLAYING_MARGIN_H, vertical = NOW_PLAYING_MARGIN_V),
    ) {
        // Corners: the mark top left, the way back top right.
        Row(Modifier.align(Alignment.TopStart), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.logo_mark), contentDescription = null, modifier = Modifier.size(Size.IconButton))
            Spacer(Modifier.width(Spacing.S))
            Text(stringResource(R.string.er_brand), style = MaterialTheme.typography.titleLarge)
        }
        IconButton(onClick = onExit, modifier = Modifier.align(Alignment.TopEnd)) {
            Icon(painterResource(R.drawable.fullscreen_exit), contentDescription = stringResource(R.string.er_lyrics_exit_fullscreen))
        }

        // The cover as large as the height allows once the title, time and controls fit under it.
        val cover = (maxHeight * NOW_PLAYING_COVER_SHARE).coerceAtMost(maxWidth * 0.4f)
        val lyricsHeight = maxHeight * NOW_PLAYING_LYRICS_SHARE
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize().padding(top = Size.IconButton),
        ) {
            Column(Modifier.width(cover)) {
                Box(Modifier.size(cover), contentAlignment = Alignment.Center) { PlayerArtwork(metadata) }
                Spacer(Modifier.height(Spacing.L))
                Text(
                    metadata.title,
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    metadata.artists.joinToString { it.name },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(Spacing.M))
                TimeLine(progress)
                Spacer(Modifier.height(Spacing.S))
                TransportControls(playerConnection)
            }
            Spacer(Modifier.width(Spacing.XXL * 2))
            // A short window onto the lyrics: about five lines, fading out at the top and bottom.
            Box(
                Modifier
                    .weight(1f)
                    .height(lyricsHeight)
                    .fadingEdge(vertical = NOW_PLAYING_FADE),
            ) {
                LyricsPage(
                    mediaMetadata = metadata,
                    sliderPositionProvider = { seekPreview.value },
                    modifier = Modifier.widthIn(max = WindowLayout.ReadingMaxWidth).fillMaxSize(),
                )
            }
        }

        // The next song shows up only near the end, counting down to it (not when the song
        // repeats itself).
        val remaining = progress.duration - progress.position
        val showUpNext = upNext != null && repeatMode != Player.REPEAT_MODE_ONE && progress.duration > 0 &&
            remaining in 0..UP_NEXT_LEAD_MS
        AnimatedVisibility(
            visible = showUpNext,
            enter = fadeIn(Motion.medium()) + slideInHorizontally(Motion.medium()) { it / 2 },
            exit = fadeOut(Motion.fast()) + slideOutHorizontally(Motion.medium()) { it / 2 },
            modifier = Modifier.align(Alignment.BottomEnd),
        ) {
            var shown by remember { mutableStateOf(upNext) }
            upNext?.let { shown = it }
            shown?.let { next -> UpNextCard(next, remainingMs = remaining.coerceAtLeast(0L)) }
        }
    }
}

/** A thin progress line with the elapsed and total time. */
@Composable
private fun TimeLine(progress: PlaybackProgress) {
    Column(Modifier.widthIn(max = WindowLayout.ReadingMaxWidth)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(Size.SliderTrack)
                .clip(ErShape.Pill)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = TRACK_ALPHA)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress.fraction)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        Spacer(Modifier.height(Spacing.XS))
        Row(Modifier.fillMaxWidth()) {
            Text(formatTime(progress.position), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(formatTime(progress.duration), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun UpNextCard(
    next: MediaMetadata,
    remainingMs: Long,
    modifier: Modifier = Modifier,
) {
    val seconds = ((remainingMs + 999) / 1000).toInt()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .widthIn(max = WindowLayout.SidePaneWidth)
            .clip(ErShape.Medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = QUEUE_CARD_ALPHA))
            .padding(Spacing.S),
    ) {
        // The cover inside a ring that empties as the song runs out.
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(Size.ListThumb + Spacing.M)) {
            CircularProgressIndicator(
                progress = { (remainingMs.toFloat() / UP_NEXT_LEAD_MS).coerceIn(0f, 1f) },
                strokeWidth = Spacing.XXS,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = TRACK_ALPHA),
                modifier = Modifier.fillMaxSize(),
            )
            AsyncImage(
                model = next.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(Size.ListThumb - Spacing.XS).clip(CircleShape),
            )
        }
        Spacer(Modifier.width(Spacing.M))
        Column(Modifier.padding(end = Spacing.S)) {
            Text(
                stringResource(R.string.er_up_next_in, seconds),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(next.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                next.artists.joinToString { it.name },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Hides the system bars and keeps the screen on while the now-playing screen is up. */
@Composable
private fun ImmersiveWhileShown() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        view.keepScreenOn = true
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            view.keepScreenOn = false
        }
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

/** Lyrics and Queue open (or close) the panel; the sleep timer still opens its sheet. */
@Composable
private fun TabletActionRow(
    panel: SidePanel?,
    onPanel: (SidePanel) -> Unit,
    playerConnection: PlayerConnection,
) {
    var showSleepTimer by rememberSaveable { mutableStateOf(false) }
    val sleepTimer = playerConnection.service.sleepTimer
    Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
        ActionButton(
            icon = R.drawable.lyrics,
            label = stringResource(R.string.lyrics),
            selected = panel == SidePanel.Lyrics,
            onClick = { onPanel(SidePanel.Lyrics) },
        )
        ActionButton(
            icon = R.drawable.queue_music,
            label = stringResource(R.string.queue),
            selected = panel == SidePanel.Queue,
            onClick = { onPanel(SidePanel.Queue) },
        )
        ActionButton(
            icon = R.drawable.bedtime,
            label = stringResource(R.string.sleep_timer),
            selected = sleepTimer?.isActive == true,
            onClick = { showSleepTimer = true },
        )
    }
    if (showSleepTimer && sleepTimer != null) {
        SleepTimerSheet(sleepTimer = sleepTimer, onDismiss = { showSleepTimer = false })
    }
}

private const val PLAYER_PANE_SHARE = 0.34f
private const val QUEUE_CARD_ALPHA = 0.55f
private const val TRACK_ALPHA = 0.2f
private const val NOW_PLAYING_COVER_SHARE = 0.56f
private const val NOW_PLAYING_LYRICS_SHARE = 0.6f
private val NOW_PLAYING_MARGIN_H = 64.dp
private val NOW_PLAYING_MARGIN_V = 40.dp

/** How long before the end of a song the next one is announced. */
private const val UP_NEXT_LEAD_MS = 15_000L
private val NOW_PLAYING_FADE = 72.dp
