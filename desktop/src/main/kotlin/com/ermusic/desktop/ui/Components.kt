/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ermusic.desktop.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ermusic.desktop.data.Settings
import com.ermusic.desktop.playback.Credit
import com.ermusic.desktop.playback.toTrack
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.EpisodeItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.PodcastItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import kotlinx.coroutines.launch

@Composable
fun icon(name: String) = painterResource("icons/$name.xml")

/** The thumbnail URL at [size] pixels, when the URL says its size. */
fun artUrl(url: String?, size: Int): String? = url?.replace(Regex("=w\\d+-h\\d+.*$"), "=w$size-h$size-l90-rj")

fun formatTime(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

@Composable
fun Art(url: String?, modifier: Modifier = Modifier, shape: Shape = ErShape.Small, pixels: Int = 240) {
    AsyncImage(
        model = artUrl(url, pixels),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
    )
}

fun Modifier.handCursor(): Modifier = pointerHoverIcon(PointerIcon.Hand)

/** A moving sheen, the placeholder for anything still loading. */
@Composable
fun Modifier.skeleton(shape: Shape = ErShape.Small): Modifier {
    val transition = rememberInfiniteTransition()
    val travel by transition.animateFloat(
        initialValue = -SHIMMER_SPAN,
        targetValue = SHIMMER_END,
        animationSpec = infiniteRepeatable(tween(SHIMMER_MS, easing = LinearEasing), RepeatMode.Restart),
    )
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val light = MaterialTheme.colorScheme.surfaceContainerHighest
    return clip(shape).background(Brush.linearGradient(listOf(base, light, base), start = Offset(travel, 0f), end = Offset(travel + SHIMMER_SPAN, SHIMMER_SPAN / 2)))
}

private const val SHIMMER_SPAN = 700f
private const val SHIMMER_END = 2400f
private const val SHIMMER_MS = 1400

@Composable
fun SkeletonText(widthFraction: Float, height: Dp = Spacing.M) {
    Box(Modifier.fillMaxWidth(widthFraction).height(height).skeleton(ErShape.Pill))
}

@Composable
fun CardSkeleton(width: Dp = Size.Card) {
    Column(Modifier.width(width).padding(Spacing.S)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).skeleton(ErShape.Medium))
        Spacer(Modifier.height(Spacing.M))
        SkeletonText(0.8f)
        Spacer(Modifier.height(Spacing.S))
        SkeletonText(0.5f, Spacing.S)
    }
}

@Composable
fun RowSkeleton() {
    Row(Modifier.fillMaxWidth().height(Size.ListRow).padding(horizontal = Spacing.S), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(Size.ListThumb).skeleton())
        Spacer(Modifier.width(Spacing.M))
        Column(Modifier.weight(1f)) {
            SkeletonText(0.4f)
            Spacer(Modifier.height(Spacing.S))
            SkeletonText(0.25f, Spacing.S)
        }
    }
}

@Composable
fun ShelfSkeleton() {
    Column(Modifier.padding(bottom = Spacing.XL)) {
        Box(Modifier.padding(start = Spacing.S, bottom = Spacing.M).width(SHELF_TITLE_SKELETON).height(Spacing.XL).skeleton(ErShape.Pill))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) { repeat(SHELF_SKELETON_CARDS) { CardSkeleton() } }
    }
}

private val SHELF_TITLE_SKELETON = 220.dp
private const val SHELF_SKELETON_CARDS = 7

@Composable
fun ListSkeleton(rows: Int = 10) {
    Column(Modifier.fillMaxSize().padding(Spacing.L)) { repeat(rows) { RowSkeleton() } }
}

@Composable
fun SymbolButton(name: String, description: String, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurface, iconSize: Dp = Size.Icon, onClick: () -> Unit) {
    Hint(description) {
        IconButton(onClick = onClick, modifier = modifier.handCursor()) {
            Icon(icon(name), contentDescription = description, tint = tint, modifier = Modifier.size(iconSize))
        }
    }
}

/** A small label that comes up when the mouse rests on something. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Hint(text: String, content: @Composable () -> Unit) {
    TooltipArea(
        tooltip = {
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clip(ErShape.Small).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(horizontal = Spacing.S, vertical = Spacing.XS),
            )
        },
        delayMillis = HINT_DELAY_MS,
        content = content,
    )
}

private const val HINT_DELAY_MS = 500

/** Whether the user's content settings let this item show. */
fun YTItem.allowed(): Boolean =
    !(Settings.hideExplicit.value && explicit) && !(Settings.hideVideos.value && this is SongItem && isVideoSong)

@Composable
fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

const val OFFLINE_PAGE = "You're offline. Downloads and songs you've played lately still play; this page loads when you're back online."
const val OFFLINE_SONG = "Offline · plays when you're back online"

/** A strip over the page while YouTube Music can't be reached. */
@Composable
fun OfflineStrip(app: AppState) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().background(colors.surfaceContainerHighest).padding(horizontal = Spacing.L, vertical = Spacing.S),
    ) {
        Text("You're offline. Downloaded songs still play.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurface, modifier = Modifier.weight(1f))
        Text(
            "Downloads",
            style = MaterialTheme.typography.labelLarge,
            color = colors.primary,
            modifier = Modifier.clip(ErShape.Pill).clickable { app.go(Route.Downloads) }.handCursor().padding(horizontal = Spacing.M, vertical = Spacing.XS),
        )
    }
}

@Composable
fun Message(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(Spacing.XXL), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A vertical list with the desktop scrollbar at its edge. */
@Composable
fun ScrollingColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(),
    content: LazyListScope.() -> Unit,
) {
    Box(modifier) {
        LazyColumn(state = state, contentPadding = contentPadding, modifier = Modifier.fillMaxSize(), content = content)
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(state),
            style = LocalScrollbarStyle.current.copy(
                unhoverColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
                hoverColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f),
            ),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )
    }
}

/** A thin line with a small thumb; [value] runs from 0 to 1. */
@Composable
fun ThinSlider(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier, onFinished: () -> Unit = {}, color: Color = MaterialTheme.colorScheme.onSurface) {
    val change by rememberUpdatedState(onChange)
    val finished by rememberUpdatedState(onFinished)
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = TRACK_ALPHA)
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Canvas(
        modifier
            .height(SLIDER_TOUCH_HEIGHT)
            .hoverable(hover)
            .handCursor()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    // The press is the slider's: a click on it must not also reach what is under
                    // it, such as full screen's background, where a click pauses.
                    down.consume()
                    change((down.position.x / size.width).coerceIn(0f, 1f))
                    drag(down.id) { moved ->
                        change((moved.position.x / size.width).coerceIn(0f, 1f))
                        moved.consume()
                    }
                    finished()
                }
            },
    ) {
        val y = size.height / 2
        val stroke = SLIDER_TRACK.toPx()
        val x = size.width * value.coerceIn(0f, 1f)
        drawLine(track, Offset(0f, y), Offset(size.width, y), stroke, StrokeCap.Round)
        drawLine(color, Offset(0f, y), Offset(x, y), stroke, StrokeCap.Round)
        drawCircle(color, (if (hovered) SLIDER_THUMB_HOVER else SLIDER_THUMB).toPx(), Offset(x, y))
    }
}

const val TRACK_ALPHA = 0.2f
private val SLIDER_TOUCH_HEIGHT = 20.dp
private val SLIDER_TRACK = 4.dp
private val SLIDER_THUMB = 5.dp
private val SLIDER_THUMB_HOVER = 7.dp

/** Fades the content out at its top and bottom edges. */
fun Modifier.fadeEdges(top: Dp, bottom: Dp): Modifier =
    graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawWithContent {
        drawContent()
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = 0f, endY = top.toPx()), blendMode = BlendMode.DstIn)
        drawRect(Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = size.height - bottom.toPx(), endY = size.height), blendMode = BlendMode.DstIn)
    }

fun YTItem.subtitle(): String =
    when (this) {
        is SongItem -> artists.joinToString { it.name }
        is AlbumItem -> listOfNotNull(artists?.joinToString { it.name }, year?.toString()).joinToString(" · ")
        is PlaylistItem -> listOfNotNull(author?.name, songCountText).joinToString(" · ")
        is ArtistItem -> "Artist"
        is PodcastItem -> author?.name.orEmpty()
        is EpisodeItem -> author?.name.orEmpty()
    }

fun List<SongItem>.tracks() = map { it.toTrack() }

private fun Artist.credit() = Credit(name, id)

/** The people an item is by, each of them a link. */
fun YTItem.credits(): List<Credit> =
    when (this) {
        is SongItem -> artists.map { it.credit() }
        is AlbumItem -> artists.orEmpty().map { it.credit() }
        is PlaylistItem -> listOfNotNull(author?.credit())
        is PodcastItem -> listOfNotNull(author?.credit())
        is EpisodeItem -> listOfNotNull(author?.credit())
        is ArtistItem -> emptyList()
    }

/** What is said after the names: the year, the song count. */
private fun YTItem.creditNote(): String? =
    when (this) {
        is AlbumItem -> year?.toString()
        is PlaylistItem -> songCountText
        is ArtistItem -> "Artist"
        else -> null
    }

/** Names that each open their own page, underlined when the mouse is on them. */
@Composable
fun CreditsText(
    credits: List<Credit>,
    app: AppState,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    note: String? = null,
    maxLines: Int = 1,
    textAlign: TextAlign = TextAlign.Unspecified,
) {
    val hovered = TextLinkStyles(hoveredStyle = SpanStyle(color = MaterialTheme.colorScheme.onSurface, textDecoration = TextDecoration.Underline))
    Text(
        buildAnnotatedString {
            credits.forEachIndexed { index, credit ->
                if (index > 0) append(", ")
                withLink(LinkAnnotation.Clickable("credit-$index", hovered) { app.openArtist(credit) }) { append(credit.name) }
            }
            note?.let {
                if (credits.isNotEmpty()) append(" · ")
                append(it)
            }
        },
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
        modifier = modifier,
    )
}

@Composable
fun ItemCredits(item: YTItem, app: AppState, style: TextStyle = MaterialTheme.typography.bodySmall) {
    CreditsText(item.credits(), app, style, MaterialTheme.colorScheme.onSurfaceVariant, note = item.creditNote())
}

/** Where a menu asked for by right click (or the ⋮ button) should open. */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.onRightClick(open: (IntOffset) -> Unit): Modifier =
    onPointerEvent(PointerEventType.Press) { event ->
        if (event.button == PointerButton.Secondary) {
            val at = event.changes.first().position
            open(IntOffset(at.x.toInt(), at.y.toInt()))
        }
    }

@Composable
fun PlayBadge(size: Dp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledIconButton(
        onClick = onClick,
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
        modifier = modifier.size(size).handCursor(),
    ) { Icon(icon("play"), "Play", modifier = Modifier.size(size / 2)) }
}

@Composable
fun ItemCard(item: YTItem, app: AppState, modifier: Modifier = Modifier, width: Dp = Size.Card) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    var menuAt by remember { mutableStateOf<IntOffset?>(null) }
    val shape = if (item is ArtistItem) CircleShape else ErShape.Medium
    val lift by animateFloatAsState(if (hovered) HOVER_SCALE else 1f, tween(HOVER_MS, easing = FastOutSlowInEasing))
    Box(modifier.width(width)) {
        Column(
            Modifier
                .fillMaxWidth()
                .hoverable(hover)
                .clip(ErShape.Medium)
                .clickable { app.activate(item) }
                .handCursor()
                .onRightClick { menuAt = it }
                .background(if (hovered) MaterialTheme.colorScheme.onSurface.copy(alpha = HOVER_ALPHA) else Color.Transparent)
                .padding(Spacing.S),
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                Art(item.thumbnail, Modifier.fillMaxSize().scale(lift), shape, pixels = 400)
                if (hovered) {
                    PlayBadge(Size.IconButton, { if (item is SongItem) app.activate(item) else app.playItem(item) }, Modifier.align(Alignment.BottomEnd).padding(Spacing.S))
                }
            }
            Spacer(Modifier.height(Spacing.S))
            Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            ItemCredits(item, app)
        }
        ItemMenu(item, app, menuAt) { menuAt = null }
    }
}

private const val HOVER_SCALE = 1.03f
private const val HOVER_MS = 180
const val HOVER_ALPHA = 0.06f

/** A titled shelf that pages sideways, with arrows over it while the mouse is on it. */
@Composable
fun Shelf(title: String, items: List<YTItem>, app: AppState, subtitle: String? = null, onShowAll: (() -> Unit)? = null) {
    val shown = items.filter { it.allowed() }
    if (shown.isEmpty()) return
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Column(Modifier.padding(bottom = Spacing.XL)) {
        ShelfHeader(title, subtitle, onShowAll ?: { app.go(Route.ShelfAll(title, shown)) }.takeIf { shown.size > SHOW_ALL_FROM })
        Box(Modifier.hoverable(hover)) {
            LazyRow(state = state, horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                items(shown, key = { it.id }, contentType = { it::class }) { ItemCard(it, app) }
            }
            val page = (state.layoutInfo.visibleItemsInfo.size - 1).coerceAtLeast(1)
            CarouselArrows(
                hovered,
                state.canScrollBackward,
                state.canScrollForward,
                onBack = { scope.launch { state.animateScrollToItem((state.firstVisibleItemIndex - page).coerceAtLeast(0)) } },
                onForward = { scope.launch { state.animateScrollToItem(state.firstVisibleItemIndex + page) } },
            )
        }
    }
}

@Composable
private fun ShelfHeader(title: String, subtitle: String?, onShowAll: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = Spacing.S, end = Spacing.L, bottom = Spacing.S)) {
        Column(Modifier.weight(1f)) {
            subtitle?.let { Text(it.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        onShowAll?.let { open ->
            Text("Show all", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clip(ErShape.Small).clickable(onClick = open).handCursor().padding(Spacing.XS))
        }
    }
}

@Composable
private fun BoxScope.CarouselArrows(visible: Boolean, canBack: Boolean, canForward: Boolean, onBack: () -> Unit, onForward: () -> Unit) {
    if (!visible) return
    if (canBack) CarouselArrow("chevron_left", "Scroll back", Modifier.align(Alignment.CenterStart).padding(start = Spacing.S), onBack)
    if (canForward) CarouselArrow("chevron_right", "Scroll forward", Modifier.align(Alignment.CenterEnd).padding(end = Spacing.L), onForward)
}

@Composable
private fun CarouselArrow(symbol: String, description: String, modifier: Modifier, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = MaterialTheme.colorScheme.onSurface),
        modifier = modifier.size(Size.Control + Spacing.XS).handCursor(),
    ) { Icon(icon(symbol), description) }
}

private const val SHOW_ALL_FROM = 5
private const val SONG_GRID_ROWS = 4
private val SONG_GRID_WIDTH = 360.dp

/** Songs as four rows that page sideways, the way YouTube Music shows Quick picks. */
@Composable
fun SongGridShelf(title: String, allSongs: List<SongItem>, app: AppState, subtitle: String? = null) {
    val songs = allSongs.filter { it.allowed() }
    if (songs.isEmpty()) return
    val state = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Column(Modifier.padding(bottom = Spacing.XL)) {
        ShelfHeader(title, subtitle, { app.go(Route.ShelfAll(title, songs)) }.takeIf { songs.size > SHOW_ALL_FROM })
        Box(Modifier.hoverable(hover)) {
            LazyHorizontalGrid(
                rows = GridCells.Fixed(SONG_GRID_ROWS),
                state = state,
                modifier = Modifier.fillMaxWidth().height(Size.ListRow * SONG_GRID_ROWS),
            ) {
                items(songs.size, key = { songs[it].id }, contentType = { "song" }) { index ->
                    ItemRow(songs[index], app, Modifier.width(SONG_GRID_WIDTH))
                }
            }
            val page = (state.layoutInfo.visibleItemsInfo.size - SONG_GRID_ROWS).coerceAtLeast(SONG_GRID_ROWS)
            CarouselArrows(
                hovered,
                state.canScrollBackward,
                state.canScrollForward,
                onBack = { scope.launch { state.animateScrollToItem((state.firstVisibleItemIndex - page).coerceAtLeast(0)) } },
                onForward = { scope.launch { state.animateScrollToItem(state.firstVisibleItemIndex + page) } },
            )
        }
    }
}

@Composable
fun ItemRow(
    item: YTItem,
    app: AppState,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    row: PlaylistRow? = null,
    addedBy: String? = null,
    menuExtra: (@Composable ColumnScope.(() -> Unit) -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    /** In place of [leading] while the pointer is on the row, such as a handle to drag it by. */
    hoverLeading: (@Composable () -> Unit)? = null,
    /** On the row itself, under its click, such as dragging it into a new place. */
    rowModifier: Modifier = Modifier,
) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    var menuAt by remember { mutableStateOf<IntOffset?>(null) }
    var width by remember { mutableStateOf(0) }
    val current = app.currentId == item.id
    Box(modifier.onSizeChanged { width = it.width }) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(Size.ListRow)
                    .hoverable(hover)
                    .clip(ErShape.Small)
                    .then(rowModifier)
                    .clickable { onClick?.invoke() ?: app.activate(item) }
                    .handCursor()
                    .onRightClick { menuAt = it }
                    .background(if (hovered) MaterialTheme.colorScheme.onSurface.copy(alpha = HOVER_ALPHA) else Color.Transparent)
                    .padding(horizontal = Spacing.S),
        ) {
            if (hovered && hoverLeading != null) hoverLeading() else leading?.invoke()
            Art(item.thumbnail, Modifier.size(Size.ListThumb), if (item is ArtistItem) CircleShape else ErShape.Small, pixels = 120)
            Spacer(Modifier.width(Spacing.M))
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ItemCredits(item, app)
            }
            (item as? SongItem)?.album?.let { album ->
                if (leading != null) {
                    Text(album.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
            }
            addedBy?.let { avatar ->
                Art(avatar, Modifier.padding(horizontal = Spacing.S).size(Size.IconSmall), CircleShape, pixels = 64)
            }
            // Stays while its menu is open: the menu covers the row, which ends the hover, and the
            // button (with its tooltip) shouldn't be torn down the moment it opened something.
            if (hovered || menuAt != null) {
                SymbolButton("more_vert", "More", tint = MaterialTheme.colorScheme.onSurfaceVariant, iconSize = Size.IconSmall) { menuAt = IntOffset(width - MENU_BUTTON_INSET, 0) }
            }
            (item as? SongItem)?.duration?.let {
                Spacer(Modifier.width(Spacing.M))
                Text(formatTime(it * 1000L), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        ItemMenu(item, app, menuAt, row = row, extra = menuExtra) { menuAt = null }
    }
}

private const val MENU_BUTTON_INSET = 320

/** A wrapping grid of equal cells that fills the width it gets. */
@Composable
fun <T> FillGrid(items: List<T>, minCell: Dp, spacing: Dp = Spacing.XS, cell: @Composable (T, Dp) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = ((maxWidth + spacing) / (minCell + spacing)).toInt().coerceAtLeast(1)
        val width = (maxWidth - spacing * (columns - 1)) / columns
        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            items.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(spacing)) { row.forEach { cell(it, width) } }
            }
        }
    }
}

/** An anchor at the pointer, so a dropdown opens where the mouse is. */
@Composable
fun MenuAnchor(at: IntOffset, content: @Composable () -> Unit) {
    Box(Modifier.offset { at }.size(0.dp)) { content() }
}
