/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ermusic.desktop.ui

import androidx.compose.runtime.collectAsState
import com.ermusic.desktop.playback.Connection
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ermusic.desktop.data.Downloads
import com.ermusic.desktop.data.History
import com.ermusic.desktop.data.SpeedDial
import com.ermusic.desktop.playback.Credit
import com.ermusic.desktop.playback.Track
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.pages.HomePage
import com.metrolist.music.utils.YouTubePlaylistEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import java.time.LocalTime

/**
 * A page's data: still loading (null), or the answer. A page that failed for want of a connection
 * loads again by itself once the connection is back.
 */
@Composable
private fun <T> load(vararg keys: Any?, fetch: suspend () -> Result<T>): State<Result<T>?> =
    produceState<Result<T>?>(null, *keys) {
        value = null
        while (true) {
            val result = fetch()
            value = result
            if (result.isSuccess || Connection.check()) break
            Connection.awaitOnline()
        }
    }

@Composable
private fun <T> Loaded(result: Result<T>?, skeleton: @Composable () -> Unit, content: @Composable (T) -> Unit) {
    val online by Connection.online.collectAsState()
    when {
        result == null -> skeleton()
        result.isFailure -> Message(if (online) "Couldn't load this page. Try again in a moment." else OFFLINE_PAGE)
        else -> content(result.getOrThrow())
    }
}

private val PagePadding = PaddingValues(start = Spacing.L, end = Spacing.S, top = Spacing.S, bottom = Spacing.XXL)

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) colors.onPrimary else colors.onSurface,
        maxLines = 1,
        modifier =
            Modifier
                .clip(ErShape.Pill)
                .background(if (selected) colors.primary else colors.surfaceContainerHigh)
                .clickable(onClick = onClick)
                .handCursor()
                .padding(horizontal = Spacing.L, vertical = Spacing.S),
    )
}

@Composable
private fun PageTitle(text: String) {
    Text(text, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(start = Spacing.S, top = Spacing.S, bottom = Spacing.M))
}

// ---- Home ----

private fun greeting(): String =
    when (LocalTime.now().hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }

@Composable
private fun QuickTile(track: Track, app: AppState, width: Dp, onClick: () -> Unit) {
    var menuAt by remember { mutableStateOf<IntOffset?>(null) }
    Box(Modifier.width(width)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(QUICK_TILE_HEIGHT)
                    .clip(ErShape.Small)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(onClick = onClick)
                    .handCursor()
                    .onRightClick { menuAt = it },
        ) {
            Art(track.artUrl, Modifier.size(QUICK_TILE_HEIGHT), androidx.compose.ui.graphics.RectangleShape, pixels = 120)
            Spacer(Modifier.width(Spacing.M))
            Text(track.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = Spacing.S))
        }
        TrackMenu(track, app, menuAt) { menuAt = null }
    }
}

private val QUICK_TILE_HEIGHT = 64.dp
private val QUICK_TILE_MIN = 240.dp
private const val QUICK_TILES = 8

/** One big card leading the page: something picked for the user. */
@Composable
private fun Hero(item: YTItem, app: AppState) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = Spacing.S, end = Spacing.L, bottom = Spacing.XL)
                .clip(ErShape.Large)
                .background(Brush.horizontalGradient(listOf(colors.primaryContainer, colors.surfaceContainerHigh)))
                .clickable { app.open(item) }
                .handCursor()
                .padding(Spacing.L),
    ) {
        Art(item.thumbnail, Modifier.size(HERO_ART), ErShape.Medium, pixels = 500)
        Spacer(Modifier.width(Spacing.XL))
        Column(Modifier.weight(1f)) {
            Text("FEATURED FOR YOU", style = MaterialTheme.typography.labelSmall, color = colors.primary)
            Spacer(Modifier.height(Spacing.XS))
            Text(item.title, style = MaterialTheme.typography.displaySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(Spacing.XS))
            ItemCredits(item, app, MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(Spacing.L))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Button(onClick = { app.playItem(item) }, modifier = Modifier.handCursor()) {
                    Icon(icon("play"), null, modifier = Modifier.size(Size.IconSmall))
                    Spacer(Modifier.width(Spacing.XS))
                    Text("Play")
                }
                OutlinedButton(onClick = { app.playItem(item, shuffle = true) }, modifier = Modifier.handCursor()) {
                    Icon(icon("shuffle"), null, modifier = Modifier.size(Size.IconSmall))
                    Spacer(Modifier.width(Spacing.XS))
                    Text("Shuffle")
                }
            }
        }
    }
}

private val HERO_ART = 168.dp

@Composable
private fun HomeSkeleton() {
    Column(Modifier.fillMaxSize().padding(start = Spacing.L, top = Spacing.S)) {
        Box(Modifier.padding(start = Spacing.S, bottom = Spacing.M).width(SKELETON_TITLE).height(Spacing.XXL).skeleton(ErShape.Pill))
        Row(Modifier.padding(start = Spacing.S, bottom = Spacing.L), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            repeat(SKELETON_CHIPS) { Box(Modifier.width(SKELETON_CHIP).height(Spacing.XXL).skeleton(ErShape.Pill)) }
        }
        Box(Modifier.fillMaxWidth().padding(start = Spacing.S, end = Spacing.L, bottom = Spacing.XL).height(HERO_ART + Spacing.XXL).skeleton(ErShape.Large))
        repeat(2) { ShelfSkeleton() }
    }
}

private val SKELETON_TITLE = 320.dp
private val SKELETON_CHIP = 96.dp
private const val SKELETON_CHIPS = 8
private const val HOME_PAGES_UP_FRONT = 2
private const val LOAD_MORE_WITHIN = 3

@Composable
private fun ShelfFor(title: String, items: List<YTItem>, app: AppState, subtitle: String? = null) {
    val songs = items.filterIsInstance<SongItem>()
    if (songs.size == items.size && songs.size >= SONG_GRID_MINIMUM) SongGridShelf(title, songs, app, subtitle) else Shelf(title, items, app, subtitle)
}

private const val SONG_GRID_MINIMUM = 8

@Composable
fun HomeScreen(app: AppState) {
    val home = app.home
    val online by Connection.online.collectAsState()
    val list = home.list

    suspend fun loadMore() {
        val token = home.continuation ?: return
        home.continuation = null
        YouTube.home(token).onSuccess { page ->
            home.sections.addAll(page.sections.filter { new -> home.sections.none { it.title == new.title } })
            home.continuation = page.continuation
        }
    }

    LaunchedEffect(home.chip) {
        if (home.loaded && home.loadedChip == home.chip) return@LaunchedEffect
        home.loading = true
        home.failed = false
        home.sections.clear()
        home.continuation = null
        var first = YouTube.home(params = home.chip?.endpoint?.params).getOrNull()
        // Offline: show it, and load Home once the connection is back.
        while (first == null && !Connection.check()) {
            home.failed = true
            home.loading = false
            Connection.awaitOnline()
            home.failed = false
            home.loading = true
            first = YouTube.home(params = home.chip?.endpoint?.params).getOrNull()
        }
        if (first == null) {
            home.failed = true
        } else {
            if (home.chips.isEmpty()) home.chips = first.chips.orEmpty()
            home.sections.addAll(first.sections)
            home.continuation = first.continuation
            home.loaded = true
            home.loadedChip = home.chip
            repeat(HOME_PAGES_UP_FRONT) { loadMore() }
        }
        home.loading = false
    }
    LaunchedEffect(list) {
        snapshotFlow { (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= list.layoutInfo.totalItemsCount - LOAD_MORE_WITHIN }
            .filter { it }
            .collect { loadMore() }
    }

    when {
        home.failed -> Message(if (online) "Couldn't load Home. Try again in a moment." else OFFLINE_PAGE)
        home.loading && home.sections.isEmpty() -> HomeSkeleton()
        else ->
            ScrollingColumn(Modifier.fillMaxSize(), state = list, contentPadding = PagePadding) {
                item(key = "greeting", contentType = "greeting") {
                    val name = app.profile?.name?.substringBefore(' ')
                    PageTitle(if (name.isNullOrBlank()) greeting() else "${greeting()}, $name")
                }
                if (home.chips.isNotEmpty()) {
                    item(key = "chips", contentType = "chips") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), contentPadding = PaddingValues(start = Spacing.S, end = Spacing.L), modifier = Modifier.padding(bottom = Spacing.L)) {
                            item(key = "all", contentType = "chip") { Chip("All", home.chip == null) { home.chip = null } }
                            items(home.chips, key = { it.title }, contentType = { "chip" }) { option -> Chip(option.title, option == home.chip) { home.chip = option } }
                        }
                    }
                }
                val recent = (SpeedDial.tracks + app.accountHistory.tracks() + History.tracks).distinctBy { it.id }.distinctBy { it.artUrl }.take(QUICK_TILES)
                if (recent.isNotEmpty() && home.chip == null) {
                    item(key = "jump", contentType = "jump") {
                        Column(Modifier.padding(start = Spacing.S, end = Spacing.L, bottom = Spacing.XL)) {
                            FillGrid(recent, QUICK_TILE_MIN, Spacing.S) { track: Track, width ->
                                QuickTile(track, app, width) { app.player.playRadio(track) }
                            }
                        }
                    }
                }
                home.sections.firstNotNullOfOrNull { section -> section.items.firstOrNull { (it is PlaylistItem || it is AlbumItem) && it.allowed() } }?.let { feature ->
                    if (home.chip == null) item(key = "hero", contentType = "hero") { Hero(feature, app) }
                }
                items(home.sections, key = { it.title }, contentType = { "shelf" }) { section ->
                    ShelfFor(section.title, section.items, app, section.label)
                }
                if (home.continuation != null) item(key = "more", contentType = "more") { ShelfSkeleton() }
            }
    }
}

// ---- Browse ----

private data class BrowseData(
    val moods: List<com.metrolist.innertube.pages.MoodAndGenres>,
    val releases: List<AlbumItem>,
    val charts: List<com.metrolist.innertube.pages.ChartsPage.ChartSection>,
)

/** A category in its own colour, with a cover from inside it tilted in the corner. */
@Composable
private fun CategoryTile(title: String, color: Color, cover: String?, width: Dp, onClick: () -> Unit) {
    val raw = color.copy(alpha = 1f).compositeOver(Color.Black)
    val base = if (raw.luminance() > MAX_TILE_LUMINANCE) lerp(raw, Color.Black, TILE_DARKEN) else raw
    Box(
        modifier =
            Modifier
                .width(width)
                .height(TILE_HEIGHT)
                .clip(ErShape.Medium)
                .background(Brush.linearGradient(listOf(base, lerp(base, Color.Black, TILE_GRADIENT_END))))
                .clickable(onClick = onClick)
                .handCursor(),
    ) {
        if (!cover.isNullOrEmpty()) {
            Art(
                cover,
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = Spacing.M, y = Spacing.S)
                    .size(TILE_COVER)
                    .graphicsLayer { rotationZ = TILE_COVER_TILT }
                    .shadow(Spacing.S, ErShape.Small),
                ErShape.Small,
                pixels = 240,
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(Spacing.L).fillMaxWidth(TILE_TEXT_WIDTH),
        )
    }
}

private val TILE_COVER = 84.dp
private const val TILE_COVER_TILT = 22f
private const val TILE_TEXT_WIDTH = 0.68f

private val TILE_HEIGHT = 112.dp
private val TILE_MIN = 210.dp
private const val MAX_TILE_LUMINANCE = 0.4f
private const val TILE_DARKEN = 0.45f
private const val TILE_GRADIENT_END = 0.4f

@Composable
fun BrowseScreen(app: AppState) {
    val page by load(Unit) {
        coroutineScope {
            val moods = async { YouTube.moodAndGenres() }
            val releases = async { YouTube.newReleaseAlbums() }
            val charts = async { YouTube.getChartsPage() }
            moods.await().map { BrowseData(it, releases.await().getOrDefault(emptyList()), charts.await().getOrNull()?.sections.orEmpty()) }
        }
    }
    Loaded(page, skeleton = {
        Column(Modifier.fillMaxSize().padding(start = Spacing.L, top = Spacing.S)) {
            Box(Modifier.padding(start = Spacing.S, bottom = Spacing.L).width(SKELETON_TITLE).height(Spacing.XXL).skeleton(ErShape.Pill))
            repeat(2) { ShelfSkeleton() }
        }
    }) { data ->
        ScrollingColumn(Modifier.fillMaxSize(), contentPadding = PagePadding) {
            item(key = "title", contentType = "title") { PageTitle("Browse") }
            if (data.releases.isNotEmpty()) item(key = "new", contentType = "shelf") { Shelf("New releases", data.releases, app) }
            items(data.charts.filter { it.items.isNotEmpty() }, key = { it.title }, contentType = { "shelf" }) { section ->
                ShelfFor(section.title, section.items, app, "Charts")
            }
            items(data.moods, key = { it.title }, contentType = { "tiles" }) { group ->
                Column(Modifier.padding(start = Spacing.S, end = Spacing.L, bottom = Spacing.XL)) {
                    Text(group.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = Spacing.M))
                    FillGrid(group.items, TILE_MIN, Spacing.S) { mood, width ->
                        LaunchedEffect(mood.endpoint.browseId, mood.endpoint.params) { app.loadCategoryCover(mood.endpoint.browseId, mood.endpoint.params) }
                        CategoryTile(mood.title, Color(mood.stripeColor), app.categoryCovers[app.categoryKey(mood.endpoint.browseId, mood.endpoint.params)], width) {
                            app.go(Route.Category(mood.endpoint.browseId, mood.endpoint.params, mood.title))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryScreen(route: Route.Category, app: AppState) {
    val page by load(route) { YouTube.browse(route.browseId, route.params) }
    Loaded(page, skeleton = { Column(Modifier.fillMaxSize().padding(Spacing.L)) { repeat(3) { ShelfSkeleton() } } }) { result ->
        ScrollingColumn(Modifier.fillMaxSize(), contentPadding = PagePadding) {
            item(key = "title", contentType = "title") { PageTitle(result.title ?: route.title) }
            itemsIndexed(result.items.filter { it.items.isNotEmpty() }, key = { index, _ -> index }, contentType = { _, _ -> "shelf" }) { _, section ->
                ShelfFor(section.title.orEmpty(), section.items, app)
            }
        }
    }
}

// ---- Search ----

private enum class SearchKind(val label: String, val filter: YouTube.SearchFilter?) {
    All("All", null),
    Songs("Songs", YouTube.SearchFilter.FILTER_SONG),
    Videos("Videos", YouTube.SearchFilter.FILTER_VIDEO),
    Albums("Albums", YouTube.SearchFilter.FILTER_ALBUM),
    Artists("Artists", YouTube.SearchFilter.FILTER_ARTIST),
    Featured("Playlists", YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST),
    Community("Community", YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST),
    Podcasts("Podcasts", YouTube.SearchFilter.FILTER_PODCAST),
}

@Composable
fun SearchResultsScreen(query: String, app: AppState) {
    var kind by remember(query) { mutableStateOf(SearchKind.All) }
    Column(Modifier.fillMaxSize().padding(start = Spacing.L).padding(top = Spacing.S)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), contentPadding = PaddingValues(start = Spacing.S, end = Spacing.L), modifier = Modifier.padding(bottom = Spacing.M)) {
            items(SearchKind.entries, key = { it.name }, contentType = { "chip" }) { option -> Chip(option.label, option == kind) { kind = option } }
        }
        val filter = kind.filter
        if (filter == null) {
            val summary by load(query) { YouTube.searchSummary(query, recordSearchHistory = YouTube.cookie != null) }
            Loaded(summary, skeleton = { Column { repeat(3) { ShelfSkeleton() } } }) { page ->
                ScrollingColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(end = Spacing.S, bottom = Spacing.XXL)) {
                    itemsIndexed(page.summaries, key = { index, _ -> index }, contentType = { _, _ -> "section" }) { index, section ->
                        when {
                            index == 0 && section.items.isNotEmpty() -> Hero(section.items.first(), app)
                            section.items.all { it is SongItem } ->
                                Column(Modifier.padding(start = Spacing.S, end = Spacing.L, bottom = Spacing.XL)) {
                                    Text(section.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = Spacing.S))
                                    section.items.filter { it.allowed() }.forEach { ItemRow(it, app) }
                                }
                            else -> Shelf(section.title, section.items, app)
                        }
                    }
                }
            }
        } else {
            val results by load(query, kind) { YouTube.search(query, filter, recordSearchHistory = YouTube.cookie != null) }
            Loaded(results, skeleton = { ListSkeleton() }) { found ->
                if (found.items.isEmpty()) {
                    Message("Nothing found for “$query”.")
                } else {
                    ScrollingColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(end = Spacing.S, bottom = Spacing.XXL)) {
                        items(found.items.filter { it.allowed() }, key = { it.id }, contentType = { it::class }) { ItemRow(it, app, Modifier.padding(end = Spacing.L)) }
                    }
                }
            }
        }
    }
}

// ---- Library ----

private enum class LibraryTab(val label: String) {
    All("All"),
    Playlists("Playlists"),
    Albums("Albums"),
    Artists("Artists"),
    Podcasts("Podcasts"),
}

private const val PLAYLISTS_PAGE = "FEmusic_liked_playlists"
private const val ALBUMS_PAGE = "FEmusic_liked_albums"
private const val ARTISTS_PAGE = "FEmusic_library_corpus_artists"

private suspend fun libraryPage(browseId: String): List<YTItem> = YouTube.library(browseId).getOrNull()?.items.orEmpty()

private fun List<YTItem>.withoutLiked() = filterNot { it.id == LIKED_PLAYLIST_ID || it.id == "SE" }

private suspend fun libraryItems(tab: LibraryTab): List<YTItem> =
    when (tab) {
        LibraryTab.All ->
            coroutineScope {
                val playlists = async { libraryPage(PLAYLISTS_PAGE).withoutLiked() }
                val albums = async { libraryPage(ALBUMS_PAGE) }
                val artists = async { libraryPage(ARTISTS_PAGE) }
                playlists.await() + albums.await() + artists.await()
            }
        LibraryTab.Playlists -> libraryPage(PLAYLISTS_PAGE).withoutLiked()
        LibraryTab.Albums -> libraryPage(ALBUMS_PAGE)
        LibraryTab.Artists -> libraryPage(ARTISTS_PAGE)
        LibraryTab.Podcasts -> YouTube.savedPodcastShows().getOrDefault(emptyList())
    }

/** One cell of the Library grid. */
private sealed interface LibraryEntry {
    data object Liked : LibraryEntry
    data object Downloaded : LibraryEntry
    data class Episodes(val list: EpisodeList) : LibraryEntry
    data class Saved(val item: YTItem) : LibraryEntry
}

/** Liked songs and Downloads, shown as playlists among the others. */
@Composable
private fun CollectionCard(title: String, subtitle: String, symbol: String, colors: List<Color>, width: Dp, onClick: () -> Unit) {
    Column(Modifier.width(width).clip(ErShape.Medium).clickable(onClick = onClick).handCursor().padding(Spacing.S)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(ErShape.Medium).background(Brush.linearGradient(colors)), contentAlignment = Alignment.Center) {
            Icon(icon(symbol), null, tint = Color.White, modifier = Modifier.size(Size.IconButton))
        }
        Spacer(Modifier.height(Spacing.S))
        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private val LikedTile = listOf(Color(0xFFF26DB5), Color(0xFF9F6CF2))
private val DownloadsTile = listOf(Color(0xFF1FB89C), Color(0xFF3A7BD5))

/** The account's two podcast lists, as YouTube Music keeps them at the top of Podcasts. */
enum class EpisodeList(val title: String, val subtitle: String, val symbol: String, val colors: List<Color>) {
    New("New episodes", "Auto playlist", "mic", listOf(Color(0xFFF7A072), Color(0xFFB98BF2))),
    Later("Episodes for later", "Saved episodes", "history", listOf(Color(0xFFC9A5F5), Color(0xFF8C9DF2))),
}

fun songCount(count: Int) = if (count == 1) "1 song" else "$count songs"

@Composable
fun LibraryScreen(app: AppState) {
    var tab by remember { mutableStateOf(LibraryTab.All) }
    val page by load(tab, app.libraryVersion) { runCatching { libraryItems(tab) } }
    Column(Modifier.fillMaxSize().padding(start = Spacing.L, top = Spacing.S)) {
        PageTitle("Library")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), contentPadding = PaddingValues(start = Spacing.S, end = Spacing.L), modifier = Modifier.padding(bottom = Spacing.M)) {
            items(LibraryTab.entries, key = { it.name }, contentType = { "chip" }) { option -> Chip(option.label, option == tab) { tab = option } }
        }
        Loaded(page, skeleton = { Row { repeat(SKELETON_CARDS) { CardSkeleton() } } }) { items ->
            val collections =
                when (tab) {
                    LibraryTab.All, LibraryTab.Playlists -> listOf(LibraryEntry.Liked, LibraryEntry.Downloaded)
                    LibraryTab.Podcasts -> EpisodeList.entries.map { LibraryEntry.Episodes(it) }
                    else -> emptyList()
                }
            val entries = collections + items.filter { it.allowed() }.map { LibraryEntry.Saved(it) }
            if (entries.isEmpty()) {
                Message(
                    when (tab) {
                        LibraryTab.Albums -> "Albums you save on YouTube Music show up here."
                        LibraryTab.Artists -> "No artists yet. Artists you subscribe to on YouTube Music show up here."
                        LibraryTab.Podcasts -> "Shows you save show up here."
                        else -> "Playlists you make or save show up here."
                    },
                )
            } else {
                ScrollingColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(end = Spacing.L, bottom = Spacing.XXL)) {
                    item(key = "grid", contentType = "grid") {
                        FillGrid(entries, Size.Card, Spacing.XS) { entry, width ->
                            when (entry) {
                                LibraryEntry.Liked -> CollectionCard("Liked songs", "Playlist · " + songCount(app.likedIds.size), "favorite", LikedTile, width) { app.go(Route.Playlist(LIKED_PLAYLIST_ID)) }
                                LibraryEntry.Downloaded -> CollectionCard("Downloads", "Playlist · " + songCount(Downloads.tracks.size), "download", DownloadsTile, width) { app.go(Route.Downloads) }
                                is LibraryEntry.Episodes -> entry.list.let { list -> CollectionCard(list.title, list.subtitle, list.symbol, list.colors, width) { app.go(Route.Episodes(list)) } }
                                is LibraryEntry.Saved -> ItemCard(entry.item, app, width = width)
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val SKELETON_CARDS = 6

@Composable
private fun TrackList(tracks: List<Track>, app: AppState, emptyText: String) {
    if (tracks.isEmpty()) {
        Message(emptyText)
        return
    }
    ScrollingColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(end = Spacing.S, bottom = Spacing.XXL)) {
        itemsIndexed(tracks.toList(), key = { _, track -> track.id }, contentType = { _, _ -> "track" }) { index, track ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .padding(end = Spacing.L)
                        .fillMaxWidth()
                        .height(Size.ListRow)
                        .clip(ErShape.Small)
                        .clickable { app.player.play(tracks.toList(), index) }
                        .handCursor()
                        .padding(horizontal = Spacing.S),
            ) {
                Art(track.artUrl, Modifier.size(Size.ListThumb), pixels = 120)
                Spacer(Modifier.width(Spacing.M))
                Column(Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    CreditsText(track.allCredits, app, MaterialTheme.typography.bodySmall, MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(formatTime(track.durationMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---- Album, playlist, artist ----

@Composable
private fun SongList(songs: List<SongItem>, app: AppState, modifier: Modifier = Modifier, rowFor: ((Int) -> PlaylistRow?)? = null, addedBy: ((SongItem) -> String?)? = null, header: (@Composable () -> Unit)? = null) {
    val shown = songs.filter { it.allowed() }
    ScrollingColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Spacing.XXL)) {
        header?.let { content -> item(key = "header", contentType = "header") { content() } }
        itemsIndexed(shown, key = { _, song -> song.setVideoId ?: song.id }, contentType = { _, _ -> "song" }) { index, song ->
            ItemRow(
                song,
                app,
                Modifier.padding(start = Spacing.L, end = Spacing.XL),
                onClick = { app.player.play(shown.tracks(), index) },
                row = rowFor?.invoke(songs.indexOf(song)),
                addedBy = addedBy?.invoke(song),
                leading = {
                    Text("${index + 1}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(SONG_NUMBER_WIDTH))
                },
            )
        }
    }
}

private val SONG_NUMBER_WIDTH = 36.dp

/** The top of an album, playlist or artist page: art, big title, and what can be done with it. */
@Composable
private fun DetailHeader(art: String?, kind: String, title: String, subtitle: String, app: AppState, credits: List<Credit> = emptyList(), round: Boolean = false, actions: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier =
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(colors.primaryContainer, Color.Transparent)))
                .padding(Spacing.XL),
    ) {
        Art(art, Modifier.size(DETAIL_ART), if (round) CircleShape else ErShape.Large, pixels = 700)
        Spacer(Modifier.width(Spacing.XL))
        Column(Modifier.weight(1f)) {
            Text(kind.uppercase(), style = MaterialTheme.typography.labelSmall, color = colors.primary)
            Spacer(Modifier.height(Spacing.XS))
            Text(title, style = MaterialTheme.typography.displaySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(Spacing.XS))
            CreditsText(credits, app, MaterialTheme.typography.bodyMedium, colors.onSurfaceVariant, note = subtitle.takeIf { it.isNotBlank() }, maxLines = 2)
            Spacer(Modifier.height(Spacing.L))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) { actions() }
        }
    }
}

private val DETAIL_ART = 216.dp

@Composable
private fun PlayShuffle(onPlay: () -> Unit, onShuffle: () -> Unit, enabled: Boolean = true) {
    Button(onClick = onPlay, enabled = enabled, modifier = Modifier.handCursor()) {
        Icon(icon("play"), null, modifier = Modifier.size(Size.IconSmall))
        Spacer(Modifier.width(Spacing.XS))
        Text("Play")
    }
    OutlinedButton(onClick = onShuffle, enabled = enabled, modifier = Modifier.handCursor()) {
        Icon(icon("shuffle"), null, modifier = Modifier.size(Size.IconSmall))
        Spacer(Modifier.width(Spacing.XS))
        Text("Shuffle")
    }
}

@Composable
private fun DetailSkeleton() {
    Column(Modifier.fillMaxSize().padding(Spacing.XL)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Box(Modifier.size(DETAIL_ART).skeleton(ErShape.Large))
            Spacer(Modifier.width(Spacing.XL))
            Column(Modifier.weight(1f)) {
                SkeletonText(0.5f, Spacing.XXL)
                Spacer(Modifier.height(Spacing.M))
                SkeletonText(0.3f)
            }
        }
        Spacer(Modifier.height(Spacing.XL))
        repeat(SKELETON_DETAIL_ROWS) { RowSkeleton() }
    }
}

private const val SKELETON_DETAIL_ROWS = 6

@Composable
fun AlbumScreen(id: String, app: AppState) {
    val page by load(id, app.libraryVersion) { YouTube.album(id) }
    Loaded(page, skeleton = { DetailSkeleton() }) { album ->
        val tracks = album.songs.tracks()
        SongList(album.songs, app, header = {
            val subtitle = listOfNotNull(album.album.year?.toString(), songCount(album.songs.size)).joinToString(" · ")
            DetailHeader(album.album.thumbnail, "Album", album.album.title, subtitle, app, album.album.credits()) {
                PlayShuffle({ app.player.play(tracks) }, { app.player.play(tracks.shuffled()) }, tracks.isNotEmpty())
                SaveButton(album.album.playlistId, app)
                SymbolButton("playlist_add", "Add to playlist") { app.addToPlaylistFor = tracks }
                SymbolButton("download", "Download") { app.download(tracks) }
                SymbolButton("share", "Copy link") { app.copyLink(album.album.shareLink) }
            }
        })
    }
}

@Composable
fun PlaylistScreen(id: String, app: AppState) {
    val page by load(id, app.libraryVersion) {
        YouTube.playlist(id).map { it to app.playlistSongs(id).ifEmpty { it.songs } }
    }
    var filter by remember(id) { mutableStateOf("") }
    val people by produceState<YouTubePlaylistEditor.PlaylistPeople?>(null, id, app.libraryVersion, app.profile) {
        value = if (YouTube.cookie == null || id == LIKED_PLAYLIST_ID) null else withContext(Dispatchers.IO) { YouTubePlaylistEditor.people(id, app.profile?.thumbnailUrl) }
    }
    Loaded(page, skeleton = { DetailSkeleton() }) { (playlist, songs) ->
        val info = playlist.playlist
        val editable = info.isEditable && id != LIKED_PLAYLIST_ID
        val query = filter.trim()
        val matching = if (query.isEmpty()) songs else songs.filter { it.title.contains(query, true) || it.subtitle().contains(query, true) }
        val tracks = matching.tracks()
        SongList(matching, app, addedBy = { song -> people?.addedBy?.get(song.id)?.avatarUrl }, rowFor = { index ->
            val canRemove = index in songs.indices && people?.removable?.contains(songs[index].id) == true
            if (!editable && canRemove) {
                PlaylistRow(id, null, null)
            } else if (!editable || query.isNotEmpty() || index !in songs.indices) {
                null
            } else {
                val song = songs[index]
                val setId = song.setVideoId
                PlaylistRow(
                    id,
                    onMoveUp = if (index > 0 && setId != null) ({ app.moveInPlaylist(id, setId, songs[index - 1].setVideoId) }) else null,
                    onMoveDown = if (index < songs.lastIndex && setId != null) ({ app.moveInPlaylist(id, setId, songs.getOrNull(index + 2)?.setVideoId) }) else null,
                )
            }
        }, header = {
            val subtitle = info.songCountText ?: songCount(songs.size)
            Column {
                DetailHeader(info.thumbnail, "Playlist", info.title, subtitle, app, info.credits()) {
                    PlayShuffle({ app.player.play(tracks) }, { app.player.play(tracks.shuffled()) }, tracks.isNotEmpty())
                    if (editable) {
                        SymbolButton("add", "Add songs") { app.addingSongsTo = info }
                        SymbolButton("edit", "Edit details") { app.editing = info }
                        SymbolButton("person_add", "Collaborate") { app.collaborating = info }
                        SymbolButton("delete", "Delete playlist") { app.deletePlaylist(info) }
                    } else if (id != LIKED_PLAYLIST_ID) {
                        if (people?.canAddSongs == true) SymbolButton("add", "Add songs") { app.addingSongsTo = info }
                        SaveButton(id, app)
                    }
                    info.radioEndpoint?.let { endpoint -> SymbolButton("radio", "Radio") { app.openRadio(info.title, info.thumbnail, endpoint) } }
                    SymbolButton("download", "Download") { app.download(tracks) }
                    SymbolButton("share", "Copy link") { app.copyLink(info.shareLink) }
                }
                PageFilter(filter, "Find in playlist", app, Modifier.padding(start = Spacing.L, end = Spacing.XL, bottom = Spacing.M).fillMaxWidth()) { filter = it }
            }
        })
    }
}

/** A small field that narrows what the page lists. */
@Composable
fun PageFilter(value: String, hint: String, app: AppState, modifier: Modifier, onChange: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.height(Size.Control).clip(ErShape.Pill).background(colors.surfaceContainerHigh).padding(horizontal = Spacing.M),
    ) {
        Icon(icon("search"), null, tint = colors.onSurfaceVariant, modifier = Modifier.size(Size.IconSmall))
        Spacer(Modifier.width(Spacing.S))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(hint, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                modifier = Modifier.fillMaxWidth().onFocusChanged { app.typing = it.isFocused },
            )
        }
    }
}

/** A radio shown as a list first: nothing plays until the user picks. */
@Composable
fun RadioScreen(route: Route.Radio, app: AppState) {
    val page by load(route) {
        YouTube.next(route.endpoint).mapCatching { first ->
            var result = first
            var songs = first.items
            repeat(RADIO_PAGES) {
                val token = result.continuation ?: return@repeat
                result = YouTube.next(result.endpoint, token).getOrNull() ?: return@repeat
                songs = songs + result.items
            }
            songs.distinctBy { it.id }
        }
    }
    Loaded(page, skeleton = { DetailSkeleton() }) { songs ->
        val tracks = songs.tracks()
        SongList(songs, app, header = {
            DetailHeader(route.art, "Radio", route.title, songCount(songs.size) + " picked for you", app) {
                PlayShuffle({ app.player.play(tracks) }, { app.player.play(tracks.shuffled()) }, tracks.isNotEmpty())
                SymbolButton("playlist_add", "Add to playlist") { app.addToPlaylistFor = tracks }
                SymbolButton("download", "Download") { app.download(tracks) }
            }
        })
    }
}

private const val RADIO_PAGES = 2

fun episodeCount(count: Int) = if (count == 1) "1 episode" else "$count episodes"

/** New episodes of the saved shows, or the episodes saved for later, as a playlist. */
@Composable
fun EpisodesScreen(list: EpisodeList, app: AppState) {
    val page by load(list, app.libraryVersion) {
        when (list) {
            EpisodeList.New -> YouTube.newEpisodes()
            EpisodeList.Later -> YouTube.episodesForLater()
        }
    }
    Loaded(page, skeleton = { DetailSkeleton() }) { episodes ->
        val tracks = episodes.tracks()
        val empty =
            when (list) {
                EpisodeList.New -> "New episodes of the shows you save show up here."
                EpisodeList.Later -> "Episodes you save for later show up here."
            }
        SongList(episodes, app, header = {
            Column {
                DetailHeader(episodes.firstOrNull()?.thumbnail, "Podcast", list.title, if (episodes.isEmpty()) empty else episodeCount(episodes.size), app) {
                    PlayShuffle({ app.player.play(tracks) }, { app.player.play(tracks.shuffled()) }, tracks.isNotEmpty())
                    if (tracks.isNotEmpty()) SymbolButton("download", "Download") { app.download(tracks) }
                }
            }
        })
    }
}

@Composable
fun ArtistScreen(id: String, app: AppState) {
    val page by load(id) { YouTube.artist(id) }
    Loaded(page, skeleton = { DetailSkeleton() }) { artist ->
        var following by remember(id) { mutableStateOf(artist.isSubscribed) }
        ScrollingColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Spacing.XXL)) {
            item(key = "header", contentType = "header") {
                val subtitle = artist.monthlyListenerCount ?: artist.subscriberCountText.orEmpty()
                DetailHeader(artist.artist.thumbnail, "Artist", artist.artist.title, subtitle, app, round = true) {
                    PlayShuffle({ app.playItem(artist.artist) }, { app.playItem(artist.artist, shuffle = true) })
                    artist.artist.radioEndpoint?.let { endpoint -> SymbolButton("radio", "Radio") { app.openRadio(artist.artist.title, artist.artist.thumbnail, endpoint) } }
                    artist.artist.channelId?.let { channel ->
                        OutlinedButton(onClick = { app.follow(channel, !following) { following = !following } }, modifier = Modifier.handCursor()) {
                            Text(if (following) "Following" else "Follow")
                        }
                    }
                    SymbolButton("share", "Copy link") { app.copyLink(artist.artist.shareLink) }
                }
            }
            items(artist.sections, key = { it.title }, contentType = { "shelf" }) { section ->
                Column(Modifier.padding(start = Spacing.L)) {
                    if (section.items.all { it is SongItem }) {
                        Column(Modifier.padding(start = Spacing.S, end = Spacing.XL, bottom = Spacing.XL)) {
                            Text(section.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = Spacing.S))
                            section.items.filter { it.allowed() }.take(ARTIST_TOP_SONGS).forEach { ItemRow(it, app) }
                        }
                    } else {
                        Shelf(section.title, section.items, app, onShowAll = section.moreEndpoint?.let { endpoint -> { app.go(Route.ArtistItems(section.title, endpoint)) } })
                    }
                }
            }
            artist.description?.takeIf { it.isNotBlank() }?.let { about ->
                item(key = "about", contentType = "about") {
                    Column(Modifier.padding(horizontal = Spacing.XL)) {
                        Text("About", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(Spacing.S))
                        Text(about, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private const val ARTIST_TOP_SONGS = 5

@Composable
private fun AllItems(title: String, items: List<YTItem>, app: AppState) {
    val shown = items.filter { it.allowed() }
    ScrollingColumn(Modifier.fillMaxSize(), contentPadding = PagePadding) {
        item(key = "title", contentType = "title") { PageTitle(title) }
        if (shown.all { it is SongItem }) {
            items(shown, key = { it.id }, contentType = { "song" }) { ItemRow(it, app, Modifier.padding(end = Spacing.L)) }
        } else {
            item(key = "grid", contentType = "grid") { Box(Modifier.padding(end = Spacing.L)) { FillGrid(shown, Size.Card, Spacing.XS) { item, width -> ItemCard(item, app, width = width) } } }
        }
    }
}

@Composable
fun ShelfAllScreen(route: Route.ShelfAll, app: AppState) = AllItems(route.title, route.items, app)

@Composable
fun ArtistItemsScreen(route: Route.ArtistItems, app: AppState) {
    val page by load(route) {
        YouTube.artistItems(route.endpoint).map { first ->
            var items = first.items
            var next = first.continuation
            while (next != null && items.size < ARTIST_ITEMS_LIMIT) {
                val more = YouTube.artistItemsContinuation(next).getOrNull() ?: break
                items = items + more.items
                next = more.continuation
            }
            items.distinctBy { it.id }
        }
    }
    Loaded(page, skeleton = { ListSkeleton() }) { items -> AllItems(route.title, items, app) }
}

private const val ARTIST_ITEMS_LIMIT = 500

/** The account's history, the same one the phone and music.youtube.com show. */
@Composable
fun HistoryScreen(app: AppState) {
    val page by load(app.historyVersion) { YouTube.musicHistory() }
    Loaded(page, skeleton = { ListSkeleton() }) { history ->
        val sections = history.sections.orEmpty().filter { section -> section.songs.any { it.allowed() } }
        if (sections.isEmpty()) {
            Message("Songs you play show up here.")
        } else {
            ScrollingColumn(Modifier.fillMaxSize(), contentPadding = PagePadding) {
                item(key = "title", contentType = "title") { PageTitle("History") }
                sections.forEach { section ->
                    val songs = section.songs.filter { it.allowed() }
                    item(key = "title-" + section.title, contentType = "title") {
                        Text(section.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = Spacing.S, top = Spacing.M, bottom = Spacing.XS))
                    }
                    itemsIndexed(songs, key = { index, song -> section.title + index + song.id }, contentType = { _, _ -> "song" }) { index, song ->
                        ItemRow(
                            song,
                            app,
                            Modifier.padding(end = Spacing.L),
                            onClick = { app.player.play(songs.tracks(), index) },
                            menuExtra = song.historyRemoveToken?.let { token ->
                                { close ->
                                    AppMenuItem("Remove from history", "delete") {
                                        app.removeFromHistory(token)
                                        close()
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SaveButton(playlistId: String, app: AppState) {
    val saved = app.isSaved(playlistId)
    SymbolButton(
        if (saved) "library_add_check" else "library_add",
        if (saved) "Remove from Library" else "Save to Library",
        tint = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
    ) { app.toggleSaved(playlistId) }
}

@Composable
fun DownloadsScreen(app: AppState) {
    Column(Modifier.fillMaxSize().padding(start = Spacing.L, top = Spacing.S)) {
        PageTitle("Downloads")
        TrackList(Downloads.tracks, app, "Downloaded songs play without a connection and show up here.")
    }
}
