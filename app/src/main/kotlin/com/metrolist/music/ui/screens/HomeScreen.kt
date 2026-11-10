/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens

import com.metrolist.music.ui.theme.rememberWindowLayout
import com.metrolist.music.ui.theme.WindowLayout
import kotlinx.coroutines.launch
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.layout.Arrangement
import com.metrolist.music.ui.component.SpeedDialGridItem
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.LocalIsOnline
import com.metrolist.music.LocalNavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.CONTENT_TYPE_HEADER
import com.metrolist.music.constants.CONTENT_TYPE_LIST
import com.metrolist.music.ui.component.AlbumGridItem
import com.metrolist.music.ui.component.ArtistGridItem
import com.metrolist.music.ui.component.ChipOption
import com.metrolist.music.ui.component.ErChipRow
import com.metrolist.music.ui.component.ErrorState
import com.metrolist.music.ui.component.OfflineState
import com.metrolist.music.ui.component.SectionHeader
import com.metrolist.music.ui.component.SkeletonCarousel
import com.metrolist.music.ui.component.SongGridItem
import com.metrolist.music.ui.component.SongListItem
import com.metrolist.music.ui.component.YouTubeGridItem
import com.metrolist.music.db.entities.Album
import com.metrolist.music.db.entities.Artist
import com.metrolist.music.db.entities.Song
import com.metrolist.music.ui.theme.Size
import com.metrolist.music.ui.theme.Spacing
import com.metrolist.music.viewmodels.HomeViewModel
import com.metrolist.music.models.MediaMetadata
import kotlinx.coroutines.flow.MutableStateFlow

private const val QUICK_PICK_ROWS = 4
private const val CONTENT_TYPE_CAROUSEL = 10
private const val CONTENT_TYPE_QUICK_PICKS = 11

private const val SPEED_DIAL_ROWS = 3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    @Suppress("UNUSED_PARAMETER") snackbarHostState: SnackbarHostState,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val navController = LocalNavController.current
    val playerConnection = LocalPlayerConnection.current
    val isOnline = LocalIsOnline.current
    val actions = rememberItemActions()
    val scope = rememberCoroutineScope()

    val homePage by viewModel.homePage.collectAsStateWithLifecycle()
    val quickPicks by viewModel.quickPicks.collectAsStateWithLifecycle()
    val keepListening by viewModel.keepListening.collectAsStateWithLifecycle()
    val wide = rememberWindowLayout().wide
    val speedDial by viewModel.speedDialItems.collectAsStateWithLifecycle()
    val pinnedIds by viewModel.pinnedSpeedDialItems.collectAsStateWithLifecycle()
    val accountPlaylists by viewModel.accountPlaylists.collectAsStateWithLifecycle()
    val selectedChip by viewModel.selectedChip.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val homeError by viewModel.homeError.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadHomeData() }
    val mediaMetadata by (playerConnection?.mediaMetadata ?: remember { MutableStateFlow<MediaMetadata?>(null) })
        .collectAsStateWithLifecycle()
    val isPlaying by (playerConnection?.isPlaying ?: remember { MutableStateFlow(false) })
        .collectAsStateWithLifecycle()
    val playingId = mediaMetadata?.id
    val playingAlbumId = mediaMetadata?.album?.id

    val listState = rememberLazyListState()
    // Sections arrive at different times; Compose keeps the scroll anchored to whatever was on top
    // first, which pushed Speed dial and Quick picks above the screen. Until the user scrolls, the
    // feed stays at its top.
    LaunchedEffect(listState) {
        var userScrolled = false
        launch { snapshotFlow { listState.isScrollInProgress }.collect { if (it) userScrolled = true } }
        snapshotFlow { listState.layoutInfo.totalItemsCount }.collect {
            if (!userScrolled && listState.firstVisibleItemIndex != 0) listState.scrollToItem(0)
        }
    }
    val reachedEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 2
        }
    }
    LaunchedEffect(reachedEnd, homePage?.continuation) {
        val continuation = homePage?.continuation
        if (reachedEnd && continuation != null && isOnline) viewModel.loadMoreYouTubeItems(continuation)
    }
    val scrollToTop =
        navController.currentBackStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsStateWithLifecycle()
    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            listState.animateScrollToItem(0)
            navController.currentBackStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    val hasLocal = !quickPicks.isNullOrEmpty() || !keepListening.isNullOrEmpty()
    val contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()

    if (!isOnline && !hasLocal) {
        Box(Modifier.fillMaxSize().testTag("home_feed")) {
            OfflineState(onOpenDownloads = { navController.navigate("downloads") })
        }
        return
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize().testTag("home_feed"),
        ) {
            if (!isOnline) {
                item(key = "offline_banner", contentType = CONTENT_TYPE_HEADER) {
                    OfflineState(onOpenDownloads = { navController.navigate("downloads") })
                }
            }

            homePage?.chips?.takeIf { it.isNotEmpty() && isOnline }?.let { chips ->
                item(key = "chips", contentType = CONTENT_TYPE_HEADER) {
                    ErChipRow(
                        options = chips.map { ChipOption(it, it.title) },
                        selected = selectedChip,
                        onSelect = viewModel::toggleChip,
                    )
                }
            }

            // Speed dial: what you pinned first, then what you play most, three rows of tiles.
            speedDial.takeIf { it.isNotEmpty() }?.let { tiles ->
                item(key = "speed_dial", contentType = CONTENT_TYPE_CAROUSEL) {
                    val pinned = remember(pinnedIds) { pinnedIds.map { it.id }.toSet() }
                    val tile = if (wide) WindowLayout.TabletTile else Size.CardThumbSmall - Spacing.S
                    SectionHeader(title = stringResource(R.string.speed_dial))
                    LazyHorizontalGrid(
                        rows = GridCells.Fixed(SPEED_DIAL_ROWS),
                        contentPadding = PaddingValues(horizontal = Spacing.Gutter),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                        verticalArrangement = Arrangement.spacedBy(Spacing.S),
                        modifier = Modifier.height(tile * SPEED_DIAL_ROWS + Spacing.S * (SPEED_DIAL_ROWS - 1)),
                    ) {
                        items(tiles, key = { "dial_${it.id}" }, contentType = { "speed_dial" }) { item ->
                            SpeedDialGridItem(
                                item = item,
                                isPinned = item.id in pinned,
                                isActive = item.isCurrent(playingId, playingAlbumId),
                                isPlaying = isPlaying,
                                modifier = Modifier.width(tile).itemClicks(actions, item),
                            )
                        }
                    }
                }
            }

            quickPicks?.takeIf { it.isNotEmpty() }?.let { songs ->
                item(key = "quick_picks", contentType = CONTENT_TYPE_QUICK_PICKS) {
                    SectionHeader(title = stringResource(R.string.quick_picks))
                    LazyHorizontalGrid(
                        rows = GridCells.Fixed(QUICK_PICK_ROWS),
                        contentPadding = PaddingValues(horizontal = Spacing.S),
                        modifier = Modifier.height(Size.ListRow * QUICK_PICK_ROWS),
                    ) {
                        items(songs, key = { it.id }, contentType = { CONTENT_TYPE_LIST }) { song ->
                            SongListItem(
                                song = song,
                                isActive = song.id == playingId,
                                isPlaying = isPlaying,
                                isSwipeable = false,
                                modifier = Modifier
                                    .width(Size.CardThumb * 2 + Spacing.XXXL)
                                    .itemClicks(actions, song),
                            )
                        }
                    }
                }
            }

            keepListening?.takeIf { it.isNotEmpty() }?.let { items ->
                item(key = "keep_listening", contentType = CONTENT_TYPE_CAROUSEL) {
                    SectionHeader(title = stringResource(R.string.keep_listening))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = Spacing.XS),
                    ) {
                        items(items, key = { "${it::class.simpleName}_${it.id}" }, contentType = { it::class.simpleName }) { item ->
                            val itemModifier = Modifier.width(Size.CardThumb + Spacing.L).itemClicks(actions, item)
                            when (item) {
                                is Song -> SongGridItem(song = item, modifier = itemModifier, isActive = item.id == playingId, isPlaying = isPlaying)
                                is Album -> AlbumGridItem(album = item, modifier = itemModifier, coroutineScope = scope, isActive = item.id == playingAlbumId, isPlaying = isPlaying)
                                is Artist -> ArtistGridItem(artist = item, modifier = itemModifier)
                                else -> {}
                            }
                        }
                    }
                }
            }

            accountPlaylists?.takeIf { it.isNotEmpty() && isOnline }?.let { playlists ->
                item(key = "account_playlists", contentType = CONTENT_TYPE_CAROUSEL) {
                    SectionHeader(title = stringResource(R.string.your_youtube_playlists))
                    LazyRow(contentPadding = PaddingValues(horizontal = Spacing.XS)) {
                        items(playlists, key = { it.id }, contentType = { "playlist" }) { playlist ->
                            YouTubeGridItem(
                                item = playlist,
                                modifier = Modifier.width(Size.CardThumb + Spacing.L).itemClicks(actions, playlist),
                            )
                        }
                    }
                }
            }

            homePage?.sections?.forEachIndexed { index, section ->
                item(key = "section_${index}_${section.title}", contentType = CONTENT_TYPE_CAROUSEL) {
                    SectionHeader(title = section.title, subtitle = section.label)
                    LazyRow(contentPadding = PaddingValues(horizontal = Spacing.XS)) {
                        items(section.items, key = { it.id }, contentType = { it::class.simpleName }) { item ->
                            YouTubeGridItem(
                                item = item,
                                isActive = item.isCurrent(playingId, playingAlbumId),
                                isPlaying = isPlaying,
                                thumbnailRatio = if (item is SongItem && item.isVideoSong) 16f / 9 else 1f,
                                modifier = Modifier.itemClicks(actions, item),
                            )
                        }
                    }
                }
            }

            when {
                homePage == null && homeError && !isLoading && !isRefreshing && isOnline ->
                    item(key = "error", contentType = "error") { ErrorState(onRetry = viewModel::refresh) }

                homePage == null && isOnline ->
                    items(3, key = { "skeleton_$it" }, contentType = { "skeleton" }) { SkeletonCarousel() }

                else -> item(key = "bottom_space", contentType = "space") { Spacer(Modifier.height(Spacing.L)) }
            }
        }
    }
}
