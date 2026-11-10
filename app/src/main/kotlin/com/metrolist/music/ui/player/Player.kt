/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.player

import com.metrolist.music.ui.theme.rememberWindowLayout
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.rememberTextMeasurer
import com.metrolist.music.db.entities.LyricsEntity
import com.metrolist.music.lyrics.LyricsUtils
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.extensions.metadata
import com.metrolist.music.extensions.move
import com.metrolist.music.extensions.toggleRepeatMode
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.ui.component.BottomSheet
import com.metrolist.music.ui.component.BottomSheetState
import com.metrolist.music.ui.component.ErSlider
import com.metrolist.music.ui.component.LocalBottomSheetPageState
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.MediaMetadataListItem
import com.metrolist.music.ui.menu.PlayerMenu
import com.metrolist.music.ui.menu.QueueMenu
import com.metrolist.music.ui.theme.ErShape
import com.metrolist.music.ui.theme.Motion
import com.metrolist.music.ui.theme.Size
import com.metrolist.music.ui.theme.Spacing
import com.metrolist.music.ui.utils.ShowMediaInfo
import com.metrolist.music.ui.utils.resize
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

enum class PlayerPage { Artwork, Queue }

/**
 * The player sheet: mini player when collapsed, full player when expanded.
 *
 * Portrait: edge-to-edge artwork that fades into the background, then the controls and the
 * Lyrics / Queue / Sleep timer row. Lyrics replace the artwork in place; the queue and the sleep
 * timer open as bottom sheets.
 *
 * Performance rules: drag progress is only read inside the sheet's graphicsLayer lambdas, and
 * the ticking playback position is only read by [ErSlider]'s draw phase, [TimeLabels] and the
 * lyrics. Nothing here reads either at the root, so dragging the sheet or playing a track does
 * not recompose the player.
 */
@Composable
fun BottomSheetPlayer(
    state: BottomSheetState,
    navController: NavController,
    modifier: Modifier = Modifier,
    pureBlack: Boolean,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val progress = rememberPlaybackProgress(playerConnection)
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val artwork = rememberPlayerArtwork(mediaMetadata?.thumbnailUrl)
    var page by rememberSaveable { mutableStateOf(PlayerPage.Artwork) }
    val collapsed by remember(state) { derivedStateOf { state.isCollapsed } }
    LaunchedEffect(collapsed) { if (collapsed) page = PlayerPage.Artwork }

    BottomSheet(
        state = state,
        modifier = modifier,
        background = { ArtworkBackground(artwork = artwork, pureBlack = pureBlack) },
        onDismiss = {
            playerConnection.player.stop()
            playerConnection.player.clearMediaItems()
        },
        collapsedContent = { MiniPlayer(progress = progress) },
    ) {
        val metadata = mediaMetadata ?: return@BottomSheet
        PlayerContent(
            state = state,
            navController = navController,
            playerConnection = playerConnection,
            metadata = metadata,
            progress = progress,
            page = page,
            onPageChange = { page = it },
        )
    }
}

@Composable
private fun PlayerContent(
    state: BottomSheetState,
    navController: NavController,
    playerConnection: PlayerConnection,
    metadata: MediaMetadata,
    progress: PlaybackProgress,
    page: PlayerPage,
    onPageChange: (PlayerPage) -> Unit,
) {
    // Seek preview while the slider is being dragged; read lazily by time labels and lyrics.
    val seekPreview = remember { mutableStateOf<Long?>(null) }

    BackHandler(enabled = page != PlayerPage.Artwork) { onPageChange(PlayerPage.Artwork) }

    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val layout = rememberWindowLayout()
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        when {
            // Tablets in landscape: the player in the middle, lyrics and the queue in a panel that
            // slides in beside it. Upright, a tablet uses the phone player.
            layout.tabletLandscape -> TabletPlayer(state, navController, playerConnection, metadata, progress, seekPreview, layout)
            landscape -> Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(Modifier.fillMaxSize().padding(horizontal = Spacing.XL, vertical = Spacing.L)) {
                    PageArea(page, metadata, seekPreview, state, Modifier.weight(1f).fillMaxHeight())
                    Spacer(Modifier.width(Spacing.XXL))
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                        TopRow(state, playerConnection, metadata)
                        Spacer(Modifier.weight(1f))
                        Controls(state, navController, playerConnection, metadata, progress, seekPreview)
                        Spacer(Modifier.height(Spacing.L))
                        ActionRow(state, playerConnection, metadata, seekPreview)
                    }
                }
            }
            else -> PortraitPlayer(state, navController, playerConnection, metadata, progress, seekPreview, page, onPageChange)
        }
    }
}

@Composable
private fun PortraitPlayer(
    state: BottomSheetState,
    navController: NavController,
    playerConnection: PlayerConnection,
    metadata: MediaMetadata,
    progress: PlaybackProgress,
    seekPreview: androidx.compose.runtime.MutableState<Long?>,
    page: PlayerPage,
    onPageChange: (PlayerPage) -> Unit,
) {
    // The cover as a rounded card with a shadow on the cover-coloured glow, the line being sung
    // (when the song has synced lyrics), then the controls and the actions.
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars)) {
        Box(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = Spacing.S)) {
            TopRow(state, playerConnection, metadata)
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = Spacing.XL)
                .swipeToSkip(playerConnection),
        ) { PlayerArtwork(metadata) }
        Column(Modifier.padding(horizontal = Spacing.XL)) {
            LyricsPeek(playerConnection, progress, seekPreview)
            Spacer(Modifier.height(Spacing.M))
            Controls(state, navController, playerConnection, metadata, progress, seekPreview)
            Spacer(Modifier.height(Spacing.L))
            ActionRow(state, playerConnection, metadata, seekPreview)
            Spacer(Modifier.height(Spacing.M))
        }
    }
}

@Composable
internal fun TopRow(
    state: BottomSheetState,
    playerConnection: PlayerConnection,
    metadata: MediaMetadata,
    extraAction: (@Composable () -> Unit)? = null,
) {
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(Size.TopBar),
    ) {
        IconButton(onClick = state::collapseSoft) {
            Icon(painterResource(R.drawable.expand_more), contentDescription = stringResource(R.string.er_collapse_player))
        }
        Spacer(Modifier.weight(1f))
        extraAction?.invoke()
        CastButton()
        IconButton(
            onClick = {
                menuState.show {
                    PlayerMenu(
                        mediaMetadata = metadata,
                        playerBottomSheetState = state,
                        onShowDetailsDialog = {
                            bottomSheetPageState.show { ShowMediaInfo(metadata.id) }
                        },
                        onDismiss = menuState::dismiss,
                    )
                }
            },
        ) {
            Icon(painterResource(R.drawable.more_vert), contentDescription = stringResource(R.string.er_more))
        }
    }
}

@Composable
private fun PageArea(
    page: PlayerPage,
    metadata: MediaMetadata,
    seekPreview: androidx.compose.runtime.State<Long?>,
    state: BottomSheetState,
    modifier: Modifier,
) {
    AnimatedContent(
        targetState = page,
        transitionSpec = { fadeIn(Motion.medium()) togetherWith fadeOut(Motion.fast()) },
        label = "playerPage",
        modifier = modifier,
    ) { target ->
        when (target) {
            PlayerPage.Artwork -> PlayerArtwork(metadata)
            PlayerPage.Queue -> PlayerArtwork(metadata)
        }
    }
}

@Composable
internal fun PlayerArtwork(metadata: MediaMetadata) {
    val context = LocalContext.current
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val side: Dp = minOf(maxWidth, maxHeight - Spacing.L)
        val px = with(LocalDensity.current) { side.roundToPx() }
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(metadata.thumbnailUrl?.resize(px, px))
                .size(px)
                .crossfade(Motion.ArtworkCrossfade)
                .build(),
            contentDescription = null,
            modifier = Modifier
                .size(side)
                .aspectRatio(1f)
                .shadow(Spacing.L, ErShape.Large, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(ErShape.Large)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
    }
}

@Composable
internal fun Controls(
    state: BottomSheetState,
    navController: NavController,
    playerConnection: PlayerConnection,
    metadata: MediaMetadata,
    progress: PlaybackProgress,
    seekPreview: androidx.compose.runtime.MutableState<Long?>,
) {
    Column {
        TitleRow(state, navController, playerConnection, metadata)
        Spacer(Modifier.height(Spacing.M))
        ErSlider(
            progress = { progress.fraction },
            onSeeking = { f -> seekPreview.value = f?.let { (it * progress.duration).toLong() } },
            onSeekFinished = { f ->
                val target = (f * progress.duration).toLong()
                playerConnection.seekTo(target)
                progress.position = target
            },
            activeColor = MaterialTheme.colorScheme.onSurface,
        )
        TimeLabels(progress = progress, seekPreview = { seekPreview.value })
        Spacer(Modifier.height(Spacing.S))
        TransportControls(playerConnection)
    }
}

@Composable
private fun TitleRow(
    state: BottomSheetState,
    navController: NavController,
    playerConnection: PlayerConnection,
    metadata: MediaMetadata,
) {
    val currentSong by playerConnection.currentSong.collectAsStateWithLifecycle(initialValue = null)
    val liked = currentSong?.song?.liked ?: metadata.liked
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(
                text = metadata.title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                modifier = Modifier
                    .basicMarquee(iterations = 1)
                    .clickable(enabled = metadata.album != null) {
                        metadata.album?.let {
                            state.collapseSoft()
                            navController.navigate("album/${it.id}")
                        }
                    },
            )
            // Each artist of a collaboration opens its own page; the commas between them do not.
            // It scrolls once when the names do not fit, like the title, so every name can be reached.
            // The links look like the rest of the line (no blue, no underline); only a press dims them.
            val artistColor = MaterialTheme.colorScheme.onSurfaceVariant
            val linkStyles = remember(artistColor) {
                TextLinkStyles(
                    style = SpanStyle(color = artistColor, textDecoration = TextDecoration.None),
                    pressedStyle = SpanStyle(color = artistColor.copy(alpha = PRESSED_ARTIST_ALPHA), textDecoration = TextDecoration.None),
                )
            }
            val artists = remember(metadata.artists, navController, linkStyles) {
                buildAnnotatedString {
                    metadata.artists.forEachIndexed { index, artist ->
                        if (index > 0) append(", ")
                        val id = artist.id
                        if (id == null) {
                            append(artist.name)
                        } else {
                            withLink(
                                LinkAnnotation.Clickable(tag = id, styles = linkStyles) {
                                    state.collapseSoft()
                                    navController.navigate("artist/$id")
                                },
                            ) { append(artist.name) }
                        }
                    }
                }
            }
            Text(
                text = artists,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.basicMarquee(iterations = 1),
            )
        }
        IconButton(onClick = playerConnection::toggleLike) {
            Icon(
                painter = painterResource(if (liked) R.drawable.favorite else R.drawable.favorite_border),
                contentDescription = stringResource(if (liked) R.string.er_unlike else R.string.er_like),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun TimeLabels(
    progress: PlaybackProgress,
    seekPreview: () -> Long?,
) {
    // Recomposes once per second at most, and only this row.
    val seconds by remember { derivedStateOf { (seekPreview() ?: progress.position) / 1000L } }
    val total by remember { derivedStateOf { progress.duration / 1000L } }
    Row(Modifier.fillMaxWidth()) {
        Text(
            formatTime(seconds * 1000L),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(
            if (total > 0) formatTime(total * 1000L) else "",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Transport: a teal play button and white/grey toggles. The artwork never tints the controls,
 * so they read the same on every cover.
 */
@Composable
internal fun TransportControls(playerConnection: PlayerConnection) {
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val shuffle by playerConnection.shuffleModeEnabled.collectAsStateWithLifecycle()
    val repeatMode by playerConnection.repeatMode.collectAsStateWithLifecycle()
    val canPrev by playerConnection.canSkipPrevious.collectAsStateWithLifecycle()
    val canNext by playerConnection.canSkipNext.collectAsStateWithLifecycle()
    val on = MaterialTheme.colorScheme.onSurface
    val off = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)

    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        ToggleButton(R.drawable.shuffle, stringResource(R.string.er_shuffle), shuffle, on, off) {
            playerConnection.player.shuffleModeEnabled = !shuffle
        }
        IconButton(onClick = playerConnection::seekToPrevious, enabled = canPrev, modifier = Modifier.size(Size.IconButton + Spacing.S)) {
            Icon(painterResource(R.drawable.skip_previous), stringResource(R.string.er_previous), Modifier.size(Size.Icon + Spacing.S))
        }
        // The one teal thing on the player, glowing a little.
        val accent = MaterialTheme.colorScheme.primary
        IconButton(
            onClick = playerConnection::togglePlayPause,
            colors = IconButtonDefaults.iconButtonColors(containerColor = accent, contentColor = MaterialTheme.colorScheme.onPrimary),
            modifier = Modifier
                .size(Size.PlayButton)
                .shadow(Spacing.L, CircleShape, clip = false, ambientColor = accent, spotColor = accent)
                .clip(CircleShape),
        ) {
            Icon(
                painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                contentDescription = stringResource(if (isPlaying) R.string.er_pause else R.string.er_play),
                modifier = Modifier.size(Size.Icon + Spacing.M),
            )
        }
        IconButton(onClick = playerConnection::seekToNext, enabled = canNext, modifier = Modifier.size(Size.IconButton + Spacing.S)) {
            Icon(painterResource(R.drawable.skip_next), stringResource(R.string.er_next), Modifier.size(Size.Icon + Spacing.S))
        }
        ToggleButton(
            if (repeatMode == REPEAT_MODE_ONE) R.drawable.repeat_one else R.drawable.repeat,
            stringResource(R.string.er_repeat),
            repeatMode != REPEAT_MODE_OFF,
            on,
            off,
        ) { playerConnection.player.toggleRepeatMode() }
    }
}

/** Shuffle/repeat: white with a small dot when on, grey when off. */
@Composable
private fun ToggleButton(
    icon: Int,
    description: String,
    active: Boolean,
    on: Color,
    off: Color,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(painterResource(icon), contentDescription = description, tint = if (active) on else off)
            Spacer(Modifier.height(Spacing.XXS))
            Box(
                Modifier
                    .size(Spacing.XS)
                    .clip(CircleShape)
                    .background(if (active) on else Color.Transparent),
            )
        }
    }
}

/** Lyrics swap in place of the artwork; the queue and the sleep timer open as bottom sheets. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionRow(
    state: BottomSheetState,
    playerConnection: PlayerConnection,
    metadata: MediaMetadata,
    seekPreview: androidx.compose.runtime.State<Long?>,
) {
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    var showQueue by rememberSaveable { mutableStateOf(false) }
    var showSleepTimer by rememberSaveable { mutableStateOf(false) }
    val sleepTimer = playerConnection.service.sleepTimer
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier.fillMaxWidth(),
    ) {
        ActionButton(
            icon = R.drawable.lyrics,
            label = stringResource(R.string.lyrics),
            selected = showLyrics,
            onClick = { showLyrics = true },
        )
        ActionButton(
            icon = R.drawable.queue_music,
            label = stringResource(R.string.queue),
            selected = false,
            onClick = { showQueue = true },
        )
        ActionButton(
            icon = R.drawable.bedtime,
            label = stringResource(R.string.sleep_timer),
            selected = sleepTimer?.isActive == true,
            onClick = { showSleepTimer = true },
        )
    }
    // Lyrics open in a sheet over the player, like the queue, so the cover stays where it is.
    if (showLyrics) {
        ModalBottomSheet(
            onDismissRequest = { showLyrics = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = ErShape.SheetTop,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            LyricsPage(
                mediaMetadata = metadata,
                sliderPositionProvider = { seekPreview.value },
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(LYRICS_SHEET_HEIGHT)
                    .padding(horizontal = Spacing.M),
            )
        }
    }
    if (showQueue) {
        ModalBottomSheet(
            onDismissRequest = { showQueue = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = ErShape.SheetTop,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            QueuePage(
                playerBottomSheetState = state,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .padding(horizontal = Spacing.L),
            )
        }
    }
    if (showSleepTimer && sleepTimer != null) {
        SleepTimerSheet(sleepTimer = sleepTimer, onDismiss = { showSleepTimer = false })
    }
}

@Composable
internal fun ActionButton(
    icon: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(ErShape.Medium)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.L, vertical = Spacing.S),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(Size.Icon))
        Spacer(Modifier.height(Spacing.XS))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

/**
 * The line being sung, alone, between the cover and the title; a long line wraps to two or three
 * lines. Only a preview: the full lyrics open from the Lyrics button. Shown only for synced lyrics; only this line recomposes as the
 * position moves, and a new line fades in over the old one.
 */
@Composable
internal fun LyricsPeek(
    playerConnection: PlayerConnection,
    progress: PlaybackProgress,
    seekPreview: androidx.compose.runtime.State<Long?>,
    textStyle: androidx.compose.ui.text.TextStyle? = null,
) {
    val entity by playerConnection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)
    val text = entity?.lyrics
    val lines = remember(text) {
        if (text == null || text == LyricsEntity.LYRICS_NOT_FOUND || !text.trimStart().startsWith("[")) {
            emptyList()
        } else {
            runCatching { LyricsUtils.parseLyrics(text) }.getOrDefault(emptyList()).filter { it.text.isNotBlank() }
        }
    }
    if (lines.isEmpty()) return
    val index by remember(lines) {
        derivedStateOf { LyricsUtils.findCurrentLineIndex(lines, seekPreview.value ?: progress.position) }
    }
    // No frame: the line with a thin teal bar beside it, as tall as the text (one, two or three
    // lines), so the mark always fits the lyric. A long line wraps instead of being cut. The space
    // is as tall as the song's longest line needs, measured once per song, so the cover never
    // jumps; the text sits at the bottom of it, on the title.
    val style = textStyle ?: MaterialTheme.typography.titleMedium
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = Spacing.M)) {
        val textWidth = with(density) { (maxWidth - LyricsPeekBar - Spacing.M).roundToPx().coerceAtLeast(1) }
        val reserved = remember(lines, textWidth, style) {
            val tallest = lines.maxOf { line ->
                measurer.measure(
                    line.text,
                    style,
                    maxLines = LYRICS_PEEK_MAX_LINES,
                    constraints = Constraints(maxWidth = textWidth),
                ).size.height
            }
            with(density) { tallest.toDp() }
        }
        AnimatedContent(
            targetState = lines.getOrNull(index)?.text ?: "\u266a",
            transitionSpec = { fadeIn(Motion.medium()) togetherWith fadeOut(Motion.fast()) },
            contentAlignment = Alignment.BottomStart,
            label = "lyricsPeek",
            modifier = Modifier.fillMaxWidth().height(reserved),
        ) { line ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
                Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .width(LyricsPeekBar)
                            .clip(ErShape.Pill)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Spacer(Modifier.width(Spacing.M))
                    Text(
                        line,
                        style = style,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = LYRICS_PEEK_TEXT_ALPHA),
                        maxLines = LYRICS_PEEK_MAX_LINES,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Width of the teal bar beside the lyric preview. */
private val LyricsPeekBar = 3.dp

private const val LYRICS_PEEK_TEXT_ALPHA = 0.85f
private const val LYRICS_PEEK_MAX_LINES = 3

/** How much a pressed artist name dims in the player. */
private const val PRESSED_ARTIST_ALPHA = 0.6f

/** Share of the screen the lyrics sheet takes. */
private const val LYRICS_SHEET_HEIGHT = 0.9f
