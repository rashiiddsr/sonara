/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ermusic.desktop.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import coil3.compose.LocalPlatformContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import com.ermusic.desktop.data.Settings
import com.ermusic.desktop.playback.Lyrics
import com.ermusic.desktop.playback.LyricLine
import com.ermusic.desktop.playback.LyricWord
import com.ermusic.desktop.playback.LyricsRepository
import com.ermusic.desktop.playback.LyricsTranslator
import com.ermusic.desktop.playback.Player
import com.ermusic.desktop.playback.PlayerState
import com.ermusic.desktop.playback.Repeat
import com.ermusic.desktop.playback.Track
import kotlinx.coroutines.delay
import java.awt.Point
import java.awt.Toolkit
import java.awt.image.BufferedImage

/** Nine colours read from the cover, from its top left to its bottom right. */
private suspend fun coverColors(context: coil3.PlatformContext, url: String?): List<Color>? {
    val request = ImageRequest.Builder(context).data(artUrl(url, BACKDROP_PIXELS)).build()
    val bitmap = (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap() ?: return null
    return (0 until BACKDROP_GRID).flatMap { row ->
        (0 until BACKDROP_GRID).map { column ->
            val x = (bitmap.width * (2 * column + 1)) / (2 * BACKDROP_GRID)
            val y = (bitmap.height * (2 * row + 1)) / (2 * BACKDROP_GRID)
            Color(bitmap.getColor(x, y))
        }
    }
}

/** Soft pools of the cover's own colours behind the player, fading from song to song. */
@Composable
private fun CoverBackdrop(track: Track) {
    val context = LocalPlatformContext.current
    val colors by produceState<List<Color>?>(null, track.artUrl) { value = coverColors(context, track.artUrl) }
    Crossfade(colors, animationSpec = tween(BACKDROP_FADE_MS), modifier = Modifier.fillMaxSize()) { palette ->
        Canvas(Modifier.fillMaxSize()) {
            if (palette == null) return@Canvas
            val radius = maxOf(size.width, size.height) * BACKDROP_POOL
            palette.forEachIndexed { index, color ->
                val center = Offset(size.width * (index % BACKDROP_GRID + 0.5f) / BACKDROP_GRID, size.height * (index / BACKDROP_GRID + 0.5f) / BACKDROP_GRID)
                drawCircle(Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center, radius), radius, center)
            }
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = BACKDROP_SCRIM)))
}

/** The song's line and its times; apart from the bar so only this part follows the position. */
@Composable
private fun SeekBar(state: PlayerState, player: Player, modifier: Modifier = Modifier, accent: Color = MaterialTheme.colorScheme.onSurface) {
    val position by player.position.collectAsState()
    var dragging by remember { mutableStateOf<Float?>(null) }
    val max = state.durationMs.coerceAtLeast(1).toFloat()
    val shown = dragging ?: position.toFloat()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Text(formatTime(shown.toLong()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(Spacing.S))
        ThinSlider(
            value = shown.coerceIn(0f, max) / max,
            onChange = { dragging = it * max },
            onFinished = {
                dragging?.let { player.seek(it.toLong()) }
                dragging = null
            },
            color = accent,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Spacing.S))
        Text(formatTime(state.durationMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PlayPause(state: PlayerState, player: Player, size: Dp) {
    FilledIconButton(
        onClick = player::toggle,
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
        shape = CircleShape,
        modifier = Modifier.size(size).handCursor(),
    ) {
        if (state.loading) {
            CircularProgressIndicator(strokeWidth = Spacing.XXS, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(size / 2))
        } else {
            Icon(icon(if (state.playing) "pause" else "play"), if (state.playing) "Pause" else "Play", modifier = Modifier.size(size / 2))
        }
    }
}

@Composable
private fun Transport(state: PlayerState, player: Player, playSize: Dp = PLAY_IN_BAR) {
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
        SymbolButton("shuffle", "Shuffle", tint = if (state.shuffle) colors.primary else colors.onSurfaceVariant, iconSize = Size.IconSmall, onClick = player::toggleShuffle)
        SymbolButton("skip_previous", "Previous", onClick = player::previous)
        PlayPause(state, player, playSize)
        SymbolButton("skip_next", "Next", onClick = player::next)
        SymbolButton(
            if (state.repeat == Repeat.One) "repeat_one" else "repeat",
            "Repeat",
            tint = if (state.repeat == Repeat.Off) colors.onSurfaceVariant else colors.primary,
            iconSize = Size.IconSmall,
            onClick = player::cycleRepeat,
        )
    }
}

@Composable
private fun SleepTimerButton(state: PlayerState, player: Player) {
    var open by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Box {
        SymbolButton("bedtime", "Sleep timer", tint = if (state.sleepActive) colors.primary else colors.onSurfaceVariant, iconSize = Size.IconSmall) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = ErShape.Medium, containerColor = colors.surfaceContainerHighest) {
            val at = state.sleepAtMs
            if (at != null || state.sleepAfterSong) {
                val left = at?.let { "Stops in ${((it - System.currentTimeMillis()) / MS_PER_MINUTE + 1)} min" } ?: "Stops after this song"
                Text(left, style = MaterialTheme.typography.labelLarge, color = colors.primary, modifier = Modifier.padding(horizontal = Spacing.L, vertical = Spacing.S))
                AppMenuItem("Turn off", "close") {
                    open = false
                    player.cancelSleep()
                }
                HorizontalDivider(color = colors.outlineVariant)
            }
            SLEEP_MINUTES.forEach { minutes ->
                AppMenuItem("$minutes minutes", "timer") {
                    open = false
                    player.sleepIn(minutes)
                }
            }
            AppMenuItem("End of this song", "music_note") {
                open = false
                player.sleepAfterSong()
            }
        }
    }
}

@Composable
private fun VolumeControl(state: PlayerState, player: Player, width: Dp = VOLUME_WIDTH) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        SymbolButton("volume_up", "Mute", tint = if (state.muted) colors.outline else colors.onSurfaceVariant, iconSize = Size.IconSmall, onClick = player::toggleMute)
        ThinSlider(value = if (state.muted) 0f else state.volume, onChange = player::setVolume, modifier = Modifier.width(width))
    }
}

/** The bar along the bottom of the window. */
@Composable
fun PlayerBar(app: AppState, state: PlayerState) {
    val player = app.player
    val colors = MaterialTheme.colorScheme
    val track = state.current
    var menuAt by remember { mutableStateOf<IntOffset?>(null) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(Size.PlayerBar).padding(horizontal = Spacing.L),
    ) {
        Box(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.onRightClick { menuAt = it }) {
                if (track != null) {
                    Art(track.artUrl, Modifier.size(BAR_ART).clickable { app.sidePanel = SidePanel.NowPlaying }.handCursor(), pixels = 160)
                    Spacer(Modifier.width(Spacing.M))
                    Column(Modifier.weight(1f, fill = false)) {
                        Text(track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        CreditsText(track.allCredits, app, MaterialTheme.typography.bodySmall, colors.onSurfaceVariant)
                        if (state.offline) {
                            Text(OFFLINE_SONG, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        } else {
                            state.error?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = colors.error, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                    Spacer(Modifier.width(Spacing.XS))
                    val liked = track.id in app.likedIds
                    SymbolButton(if (liked) "favorite" else "favorite_border", "Like", tint = if (liked) colors.primary else colors.onSurfaceVariant, iconSize = Size.IconSmall) { app.toggleLike(track) }
                    SymbolButton("playlist_add", "Add to playlist", tint = colors.onSurfaceVariant, iconSize = Size.IconSmall) { app.addToPlaylistFor = listOf(track) }
                }
            }
            track?.let { TrackMenu(it, app, menuAt) { menuAt = null } }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1.1f)) {
            Transport(state, player)
            SeekBar(state, player, Modifier.fillMaxWidth())
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End, modifier = Modifier.weight(1f)) {
            SymbolButton("lyrics", "Lyrics", tint = panelTint(app.nowPlayingOpen), iconSize = Size.IconSmall) { app.nowPlayingOpen = !app.nowPlayingOpen }
            SymbolButton("queue_music", "Queue", tint = panelTint(app.sidePanel == SidePanel.Queue), iconSize = Size.IconSmall) { togglePanel(app, SidePanel.Queue) }
            SleepTimerButton(state, player)
            VolumeControl(state, player)
            SymbolButton("mini_player", "Mini player", tint = colors.onSurfaceVariant, iconSize = Size.IconSmall) { app.miniPlayer = true }
            SymbolButton("fullscreen", "Full screen", tint = colors.onSurfaceVariant, iconSize = Size.IconSmall, onClick = app::toggleFullscreen)
        }
    }
}

@Composable
private fun panelTint(active: Boolean) = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

private fun togglePanel(app: AppState, panel: SidePanel) {
    if (app.sidePanel == panel) {
        app.sidePanel = null
        app.sidePanelDismissed = true
    } else {
        app.sidePanel = panel
        app.sidePanelDismissed = false
    }
}

/** The queue, down the right side of the window. */
@Composable
fun QueuePanel(app: AppState, state: PlayerState) {
    val player = app.player
    val colors = MaterialTheme.colorScheme
    val list = rememberLazyListState(initialFirstVisibleItemIndex = state.index.coerceAtLeast(0))
    Column(Modifier.width(Size.Queue).fillMaxHeight().panel().padding(Spacing.S)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = Spacing.S)) {
            Text("Queue", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            SymbolButton("delete", "Clear queue", tint = colors.onSurfaceVariant, iconSize = Size.IconSmall, onClick = player::clearQueue)
            SymbolButton("side_panel", "Now playing", tint = colors.onSurfaceVariant, iconSize = Size.IconSmall) { app.sidePanel = SidePanel.NowPlaying }
            SymbolButton("close", "Close", tint = colors.onSurfaceVariant, iconSize = Size.IconSmall) {
                app.sidePanel = null
                app.sidePanelDismissed = true
            }
        }
        ScrollingColumn(Modifier.fillMaxSize(), state = list) {
            itemsIndexed(state.queue, key = { _, track -> track.id }, contentType = { _, _ -> "track" }) { index, track ->
                var menuAt by remember { mutableStateOf<IntOffset?>(null) }
                Box {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(end = Spacing.S)
                                .height(Size.ListRow)
                                .clip(ErShape.Small)
                                .background(if (index == state.index) colors.primaryContainer else Color.Transparent)
                                .clickable { player.jumpTo(index) }
                                .handCursor()
                                .onRightClick { menuAt = it }
                                .padding(horizontal = Spacing.S),
                    ) {
                        Art(track.artUrl, Modifier.size(Size.ListThumb), pixels = 120)
                        Spacer(Modifier.width(Spacing.M))
                        Column(Modifier.weight(1f)) {
                            Text(track.title, style = MaterialTheme.typography.titleSmall, color = if (index == state.index) colors.primary else colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            CreditsText(track.allCredits, app, MaterialTheme.typography.bodySmall, colors.onSurfaceVariant)
                        }
                        if (index != state.index) SymbolButton("close", "Remove from queue", tint = colors.onSurfaceVariant, iconSize = Size.IconSmall) { player.removeAt(index) }
                    }
                    TrackMenu(track, app, menuAt, extra = if (index != state.index) { { close -> AppMenuItem("Remove from queue", "close") { player.removeAt(index); close() } } } else null) { menuAt = null }
                }
            }
        }
    }
}

private sealed interface LyricsLoad {
    data object Loading : LyricsLoad
    data object Missing : LyricsLoad
    data class Found(val lyrics: Lyrics) : LyricsLoad
}

/** One word, filling from left to right over the time it is sung. */
@Composable
private fun SungWord(word: LyricWord, style: TextStyle, position: State<Long>) {
    val bright = MaterialTheme.colorScheme.onSurface
    val dim = bright.copy(alpha = UNSUNG_WORD_ALPHA)
    val progress by remember(word) {
        derivedStateOf { ((position.value - word.startMs).toFloat() / (word.endMs - word.startMs).coerceAtLeast(1)).coerceIn(0f, 1f) }
    }
    val text = if (word.trailingSpace) word.text + " " else word.text
    when (progress) {
        0f -> Text(text, style = style.copy(color = dim))
        1f -> Text(text, style = style.copy(color = bright))
        else -> Text(text, style = style.copy(brush = Brush.horizontalGradient(0f to bright, progress to bright, (progress + WORD_FILL_EDGE).coerceAtMost(1f) to dim, 1f to dim)))
    }
}

/**
 * A line the source timed word by word. Words sit in a wrapping row so each can fill by itself;
 * pieces of one word (no space between them) stay together when the line wraps.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordSyncedLine(words: List<LyricWord>, style: TextStyle, position: State<Long>, centered: Boolean) {
    val groups =
        remember(words) {
            val all = mutableListOf(mutableListOf<LyricWord>())
            words.forEach { word ->
                all.last().add(word)
                if (word.trailingSpace) all.add(mutableListOf())
            }
            all.filter { it.isNotEmpty() }
        }
    FlowRow(horizontalArrangement = if (centered) Arrangement.Center else Arrangement.Start) {
        groups.forEach { group -> Row { group.forEach { SungWord(it, style, position) } } }
    }
}

@Composable
private fun LyricRow(line: LyricLine, translation: String?, distance: Int, synced: Boolean, position: State<Long>, large: Boolean, centered: Boolean, onSeek: () -> Unit) {
    val active = distance == 0
    val alpha by animateFloatAsState(
        when {
            !synced || active -> 1f
            distance == 1 -> NEAR_LINE_ALPHA
            distance == 2 -> MIDDLE_LINE_ALPHA
            else -> FAR_LINE_ALPHA
        },
        tween(LINE_FADE_MS),
    )
    // As on the phone: the other lines shrink a little and the sung line keeps its full size, so
    // it never grows past the edges of the pane.
    val scale by animateFloatAsState(if (active || !synced) 1f else OTHER_LINE_SCALE, tween(LINE_FADE_MS, easing = FastOutSlowInEasing))
    val size = if (large) LARGE_LYRIC else NORMAL_LYRIC
    val align = if (centered) TextAlign.Center else TextAlign.Start
    val style = TextStyle(fontFamily = MaterialTheme.typography.headlineMedium.fontFamily, fontWeight = FontWeight.ExtraBold, fontSize = size, lineHeight = size * LYRIC_LINE_HEIGHT, textAlign = align)
    Column(
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (synced) Modifier.clickable(interactionSource = null, indication = null, onClick = onSeek).handCursor() else Modifier)
            .padding(vertical = if (large) LARGE_LINE_GAP else NORMAL_LINE_GAP)
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(if (centered) 0.5f else 0f, 0.5f)
            },
    ) {
        if (active && synced && line.words != null) {
            WordSyncedLine(line.words, style, position, centered)
        } else {
            Text(line.text, style = style.copy(color = MaterialTheme.colorScheme.onSurface))
        }
        if (LyricsTranslator.shouldShow(line.text, translation)) {
            Spacer(Modifier.height(Spacing.XS))
            Text(translation.orEmpty(), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium, textAlign = align), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Three breathing dots while the song hasn't reached its first line. */
@Composable
private fun WaitingDots(centered: Boolean) {
    val transition = rememberInfiniteTransition()
    val breath by transition.animateFloat(DOT_MIN_ALPHA, 1f, infiniteRepeatable(tween(DOT_BREATH_MS), RepeatMode.Reverse))
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S, if (centered) Alignment.CenterHorizontally else Alignment.Start), modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.L)) {
        repeat(DOTS) { Box(Modifier.size(Spacing.M).graphicsLayer { alpha = breath }.clip(CircleShape).background(MaterialTheme.colorScheme.onSurface)) }
    }
}

/**
 * Lyrics that follow the song. The line being sung sits a third of the way down, fills as it is
 * sung, and the lines around it fade with distance; the list stops by itself at its first and
 * last lines. Scrolling by hand holds it for a moment; a click seeks.
 */
@Composable
private fun LyricsPane(player: Player, track: Track, modifier: Modifier = Modifier, large: Boolean = false, centered: Boolean = false) {
    val loaded by produceState<LyricsLoad>(LyricsLoad.Loading, track.id) {
        value = LyricsLoad.Loading
        value = LyricsRepository.fetch(track)?.let(LyricsLoad::Found) ?: LyricsLoad.Missing
    }
    when (val result = loaded) {
        LyricsLoad.Loading -> Column(modifier.padding(top = Spacing.XL), verticalArrangement = Arrangement.spacedBy(Spacing.XL)) { repeat(LYRIC_SKELETON_LINES) { SkeletonText(LYRIC_SKELETON_WIDTH - it * LYRIC_SKELETON_STEP, Spacing.XXL) } }
        LyricsLoad.Missing -> Message("No lyrics for this song.", modifier)
        is LyricsLoad.Found -> FoundLyrics(result.lyrics, player, track, modifier, large, centered)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun FoundLyrics(lyrics: Lyrics, player: Player, track: Track, modifier: Modifier, large: Boolean, centered: Boolean) {
    val position = player.position.collectAsState()
    val translateOn = Settings.translator.value
    val language = Settings.translateLanguage.value
    val translations by produceState(emptyMap<String, String>(), track.id, lyrics, translateOn, language) {
        value = emptyMap()
        if (translateOn) value = LyricsTranslator.translate(track.id, lyrics.lines.map { it.text }, language)
    }
    // All of it belongs to these lyrics: a new song starts at the top, not where the last one
    // ended, even when its lyrics were ready and the page never showed "loading" in between.
    val list = remember(lyrics) { LazyListState() }
    // Scrolling by hand (wheel or touchpad) holds the lyrics where they are; left alone for a
    // moment, they glide back to the line being sung, as on the phone, even between lines.
    var lastScrollAt by remember(lyrics) { mutableLongStateOf(0L) }
    var held by remember(lyrics) { mutableStateOf(false) }
    var placed by remember(lyrics) { mutableStateOf(false) }
    val active by remember(lyrics) { derivedStateOf { if (lyrics.synced) lyrics.lines.indexOfLast { it.timeMs <= position.value } else -1 } }

    BoxWithConstraints(
        modifier.onPointerEvent(PointerEventType.Scroll) {
            lastScrollAt = System.currentTimeMillis()
            held = true
        },
    ) {
        val anchor = constraints.maxHeight / ANCHOR_DIVISOR
        LaunchedEffect(lastScrollAt) {
            if (!held) return@LaunchedEffect
            delay(RESYNC_AFTER_MS)
            held = false
        }
        LaunchedEffect(active, held, lyrics) {
            if (held) return@LaunchedEffect
            if (active < 0) {
                // Before the first line (sought back to the start, say): the top of the lyrics.
                if (lyrics.synced && placed) list.animateScrollToItem(0)
                return@LaunchedEffect
            }
            val target = active + 1
            val shown = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == target }
            when {
                // Opening the lyrics mid-song: straight to the line, nothing to glide from.
                !placed -> list.scrollToItem(target, -anchor)
                shown != null -> list.animateScrollBy((shown.offset - anchor).toFloat(), tween(LINE_SCROLL_MS, easing = FastOutSlowInEasing))
                else -> list.animateScrollToItem(target, -anchor)
            }
            placed = true
        }
        LazyColumn(
            state = list,
            contentPadding = PaddingValues(top = Spacing.XXL, bottom = maxHeight / 2),
            modifier = Modifier.fillMaxSize().fadeEdges(LYRIC_FADE, LYRIC_FADE),
        ) {
            item(key = "intro", contentType = "intro") { if (active < 0 && lyrics.synced) WaitingDots(centered) else Spacer(Modifier.height(1.dp)) }
            itemsIndexed(lyrics.lines, key = { index, _ -> index }, contentType = { _, _ -> "line" }) { index, line ->
                LyricRow(
                    line,
                    translations[line.text.trim()],
                    distance = if (active < 0) index + 1 else kotlin.math.abs(index - active),
                    synced = lyrics.synced,
                    position = position,
                    large = large,
                    centered = centered,
                ) { player.seek(line.timeMs) }
            }
            item(key = "credit", contentType = "credit") {
                Column(
                    horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(Spacing.XS),
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.XXL),
                ) {
                    val style = MaterialTheme.typography.bodySmall
                    val color = MaterialTheme.colorScheme.onSurfaceVariant
                    val align = if (centered) TextAlign.Center else TextAlign.Start
                    Text("Lyrics from ${lyrics.source}", style = style, color = color, textAlign = align)
                    if (translateOn && translations.isNotEmpty()) Text("Translated automatically, it may not be exact", style = style, color = color, textAlign = align)
                    Text("Lyrics belong to their songwriters and publishers. Shown for personal, non-commercial use.", style = style, color = color, textAlign = align)
                }
            }
        }
    }
}

/** Lyrics in the middle of the window, over the song's colours; the panels stay as they are. */
@Composable
fun LyricsPage(app: AppState, state: PlayerState) {
    val track = state.current ?: return
    SonaraTheme(forceDark = true) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            CoverBackdrop(track)
            LyricsPane(app.player, track, Modifier.fillMaxSize().padding(horizontal = Spacing.XXXL, vertical = Spacing.S))
        }
    }
}

private val HiddenCursor by lazy {
    PointerIcon(Toolkit.getDefaultToolkit().createCustomCursor(BufferedImage(CURSOR_SIZE, CURSOR_SIZE, BufferedImage.TYPE_INT_ARGB), Point(0, 0), "hidden"))
}

/** A record turning while the song plays, the cover at its centre. */
@Composable
private fun SpinningDisc(track: Track, playing: Boolean, size: Dp) {
    var angle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        var last = withFrameMillis { it }
        while (true) {
            withFrameMillis { now ->
                angle = (angle + (now - last) * DISC_DEGREES_PER_MS) % FULL_TURN
                last = now
            }
        }
    }
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size).graphicsLayer { rotationZ = angle }) {
        Canvas(Modifier.fillMaxSize()) {
            val outer = this.size.minDimension / 2
            drawCircle(Color(0xFF0C0C0E), outer)
            repeat(DISC_GROOVES) { groove ->
                drawCircle(Color.White.copy(alpha = GROOVE_ALPHA), outer * (DISC_LABEL + (1 - DISC_LABEL) * (groove + 1) / (DISC_GROOVES + 1)), style = Stroke(1f))
            }
            drawArc(Color.White.copy(alpha = DISC_SHINE), SHINE_START, SHINE_SWEEP, useCenter = true)
            drawArc(Color.White.copy(alpha = DISC_SHINE), SHINE_START + FULL_TURN / 2, SHINE_SWEEP, useCenter = true)
        }
        Art(track.artUrl, Modifier.size(size * DISC_LABEL), CircleShape, pixels = 700)
        Box(Modifier.size(size * DISC_HOLE).clip(CircleShape).background(Color(0xFF0C0C0E)))
    }
}

/** A vertical slider; [value] runs from 0 at the bottom to 1 at the top. */
@Composable
fun VerticalSlider(value: Float, height: Dp, onChange: (Float) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val change by rememberUpdatedState(onChange)
    Canvas(
        Modifier
            .width(Spacing.XL)
            .height(height)
            .handCursor()
            .pointerInput(Unit) {
                fun set(y: Float) = change(1f - (y / this.size.height).coerceIn(0f, 1f))
                awaitEachGesture {
                    val down = awaitFirstDown()
                    // The press is the slider's, not the background's under it.
                    down.consume()
                    set(down.position.y)
                    drag(down.id) { moved ->
                        set(moved.position.y)
                        moved.consume()
                    }
                }
            },
    ) {
        val x = this.size.width / 2
        val stroke = Spacing.XS.toPx()
        val y = this.size.height * (1 - value.coerceIn(0f, 1f))
        drawLine(colors.onSurface.copy(alpha = TRACK_ALPHA), Offset(x, 0f), Offset(x, this.size.height), stroke, StrokeCap.Round)
        drawLine(colors.primary, Offset(x, this.size.height), Offset(x, y), stroke, StrokeCap.Round)
        drawCircle(colors.onSurface, stroke * 1.5f, Offset(x, y))
    }
}

/** The controls under the record, as wide as the progress line above them. */
@Composable
private fun SpreadTransport(state: PlayerState, player: Player, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = modifier.fillMaxWidth()) {
        SymbolButton("shuffle", "Shuffle", tint = if (state.shuffle) colors.primary else colors.onSurfaceVariant, iconSize = Size.IconSmall, onClick = player::toggleShuffle)
        SymbolButton("skip_previous", "Previous", onClick = player::previous)
        PlayPause(state, player, Size.IconButton + Spacing.S)
        SymbolButton("skip_next", "Next", onClick = player::next)
        SymbolButton(
            if (state.repeat == Repeat.One) "repeat_one" else "repeat",
            "Repeat",
            tint = if (state.repeat == Repeat.Off) colors.onSurfaceVariant else colors.primary,
            iconSize = Size.IconSmall,
            onClick = player::cycleRepeat,
        )
    }
}

/**
 * Full screen, filling the display like a video, in two columns: the turning record with the song
 * and its controls under it, and the lyrics. The cursor and the extra buttons go away when the
 * mouse rests. A click on the background pauses, a double click leaves.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ImmersivePlayer(app: AppState, state: PlayerState) {
    val track = state.current ?: return
    val player = app.player
    var moved by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var pointerAt by remember { mutableStateOf<Offset?>(null) }
    var active by remember { mutableStateOf(true) }
    var showLyrics by remember { mutableStateOf(true) }
    LaunchedEffect(moved) {
        active = true
        delay(CONTROLS_HIDE_MS)
        active = false
    }
    SonaraTheme(forceDark = true) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onPointerEvent(PointerEventType.Move) { event ->
                    // Compose also reports "moves" whenever the layout under a resting pointer
                    // changes, as it does with every new lyric line; only a real move counts.
                    val at = event.changes.first().position
                    val before = pointerAt
                    pointerAt = at
                    if (before != null && (at - before).getDistance() >= MOVE_THRESHOLD) moved = System.currentTimeMillis()
                }
                .pointerHoverIcon(if (active) PointerIcon.Default else HiddenCursor)
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { app.changeFullscreen(false) }, onTap = { player.toggle() }) },
        ) {
            CoverBackdrop(track)
            val columnWidth = maxWidth * IMMERSIVE_LEFT
            val disc = min(maxHeight * IMMERSIVE_DISC_HEIGHT, columnWidth)
            val lyricsHeight = maxHeight * IMMERSIVE_LYRICS_HEIGHT
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (showLyrics) Arrangement.Start else Arrangement.Center,
                modifier = Modifier.fillMaxSize().padding(horizontal = IMMERSIVE_MARGIN),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(columnWidth).pointerInput(Unit) { detectTapGestures { } },
                ) {
                    SpinningDisc(track, state.playing, disc)
                    Spacer(Modifier.height(Spacing.XL))
                    Text(track.title, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(Spacing.XS))
                    CreditsText(track.allCredits, app, MaterialTheme.typography.titleLarge, MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    track.albumName?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    Spacer(Modifier.height(Spacing.M))
                    SeekBar(state, player, Modifier.fillMaxWidth())
                    SpreadTransport(state, player)
                }
                if (showLyrics) {
                    Spacer(Modifier.width(IMMERSIVE_GAP))
                    LyricsPane(player, track, Modifier.weight(1f).height(lyricsHeight), large = true, centered = true)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.TopStart).padding(Spacing.XL)) {
                Image(painterResource("icons/logo_mark.xml"), null, modifier = Modifier.size(Size.Logo))
                Spacer(Modifier.width(Spacing.M))
                Text("Sonara", style = MaterialTheme.typography.titleLarge)
            }
            AnimatedVisibility(active, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.L)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VolumeControl(state, player)
                    SymbolButton("lyrics", "Lyrics", tint = if (showLyrics) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, iconSize = Size.IconSmall) { showLyrics = !showLyrics }
                    SleepTimerButton(state, player)
                    SymbolButton("fullscreen_exit", "Exit full screen", iconSize = Size.IconSmall) { app.changeFullscreen(false) }
                }
            }
            UpNext(state, player, Modifier.align(Alignment.BottomStart).padding(Spacing.XL))
        }
    }
}

/** What's left of the right panel when it is hidden: a strip that brings it back. */
@Composable
fun CollapsedPanel(app: AppState) {
    Hint("Show Now Playing view") {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .width(COLLAPSED_PANEL_WIDTH)
                    .fillMaxHeight()
                    .panel()
                    .clickable {
                        app.sidePanel = SidePanel.NowPlaying
                        app.sidePanelDismissed = false
                    }.handCursor(),
        ) { Icon(icon("chevron_left"), "Show Now Playing view", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(Size.IconSmall)) }
    }
}

/** The small always-on-top window. */
@Composable
fun MiniPlayerContent(app: AppState, state: PlayerState) {
    val track = state.current
    val player = app.player
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize().padding(Spacing.S)) {
            if (track != null) {
                Art(track.artUrl, Modifier.size(Size.MiniPlayerThumb + Spacing.L), pixels = 160)
                Spacer(Modifier.width(Spacing.M))
                Column(Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artists, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            SymbolButton("skip_previous", "Previous", Modifier.size(Size.Control), onClick = player::previous)
            PlayPause(state, player, Size.Control + Spacing.XS)
            SymbolButton("skip_next", "Next", Modifier.size(Size.Control), onClick = player::next)
            SymbolButton("mini_player", "Back to Sonara", Modifier.size(Size.Control), tint = MaterialTheme.colorScheme.primary, iconSize = Size.IconSmall) { app.miniPlayer = false }
        }
    }
}

@Composable
private fun UpNext(state: PlayerState, player: Player, modifier: Modifier) {
    val position by player.position.collectAsState()
    val next = state.next
    val remaining = state.durationMs - position
    val show = next != null && state.durationMs > 0 && remaining in 0..UP_NEXT_LEAD_MS
    AnimatedVisibility(show, enter = fadeIn() + slideInHorizontally { it / 2 }, exit = fadeOut() + slideOutHorizontally { it / 2 }, modifier = modifier) {
        var shown by remember { mutableStateOf(next) }
        next?.let { shown = it }
        shown?.let { UpNextCard(it, remaining.coerceAtLeast(0)) }
    }
}

@Composable
private fun UpNextCard(next: Track, remainingMs: Long) {
    val seconds = (remainingMs + 999) / 1000
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(ErShape.Medium).background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = UP_NEXT_ALPHA)).padding(Spacing.S),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(Size.ListThumb + Spacing.M)) {
            CircularProgressIndicator(
                progress = { (remainingMs.toFloat() / UP_NEXT_LEAD_MS).coerceIn(0f, 1f) },
                strokeWidth = Spacing.XXS,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = TRACK_ALPHA),
                modifier = Modifier.fillMaxSize(),
            )
            Art(next.artUrl, Modifier.size(Size.ListThumb - Spacing.XS), CircleShape, pixels = 120)
        }
        Spacer(Modifier.width(Spacing.M))
        Column(Modifier.padding(end = Spacing.S).widthIn(max = UP_NEXT_TEXT_WIDTH)) {
            Text("Up next in ${seconds}s", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(next.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(next.artists, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private const val BACKDROP_PIXELS = 48
private const val BACKDROP_GRID = 3
private const val BACKDROP_POOL = 0.42f
private const val BACKDROP_FADE_MS = 700
private const val BACKDROP_SCRIM = 0.62f
private val PLAY_IN_BAR = 40.dp
private val BAR_ART = 56.dp
private val VOLUME_WIDTH = 100.dp
private const val MS_PER_MINUTE = 60_000L
private val SLEEP_MINUTES = listOf(5, 10, 15, 30, 45, 60, 90)
private const val PANEL_ALPHA = 0.7f
private const val UNSUNG_WORD_ALPHA = 0.42f
private const val WORD_FILL_EDGE = 0.18f
private const val NEAR_LINE_ALPHA = 0.5f
private const val MIDDLE_LINE_ALPHA = 0.32f
private const val FAR_LINE_ALPHA = 0.18f
private const val LINE_FADE_MS = 420
private const val OTHER_LINE_SCALE = 0.97f
private val NORMAL_LYRIC = 30.sp
private val LARGE_LYRIC = 42.sp
private const val LYRIC_LINE_HEIGHT = 1.28f
private val NORMAL_LINE_GAP = 18.dp
private val LARGE_LINE_GAP = 24.dp
private const val DOT_MIN_ALPHA = 0.3f
private const val DOT_BREATH_MS = 700
private const val DOTS = 3
private const val ANCHOR_DIVISOR = 3
private const val RESYNC_AFTER_MS = 2_500L
private const val LINE_SCROLL_MS = 620
private const val LYRIC_SKELETON_LINES = 6
private const val LYRIC_SKELETON_WIDTH = 0.8f
private const val LYRIC_SKELETON_STEP = 0.08f
private val LYRIC_FADE = 72.dp
private const val CURSOR_SIZE = 16
private const val CONTROLS_HIDE_MS = 3_000L
private val IMMERSIVE_MARGIN = 64.dp
private val IMMERSIVE_GAP = 56.dp
private const val IMMERSIVE_LEFT = 0.34f
private const val IMMERSIVE_DISC_HEIGHT = 0.44f
private const val IMMERSIVE_LYRICS_HEIGHT = 0.72f
private const val MOVE_THRESHOLD = 2f
private val COLLAPSED_PANEL_WIDTH = 28.dp
private const val DISC_DEGREES_PER_MS = 0.04f
private const val FULL_TURN = 360f
private const val DISC_LABEL = 0.62f
private const val DISC_HOLE = 0.05f
private const val DISC_GROOVES = 9
private const val GROOVE_ALPHA = 0.07f
private const val DISC_SHINE = 0.05f
private const val SHINE_START = 30f
private const val SHINE_SWEEP = 40f
private const val UP_NEXT_LEAD_MS = 15_000L
private const val UP_NEXT_ALPHA = 0.82f
private val UP_NEXT_TEXT_WIDTH = 240.dp
