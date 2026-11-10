/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.library

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.metrolist.music.LocalSyncUtils
import com.metrolist.music.ui.component.RefreshBox
import com.metrolist.music.constants.AlbumSortType
import com.metrolist.music.LocalDatabase
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.metrolist.music.LocalNavController
import com.metrolist.music.R
import com.metrolist.music.constants.AlbumViewTypeKey
import com.metrolist.music.constants.ChipSortTypeKey
import com.metrolist.music.constants.LibraryFilter
import com.metrolist.music.constants.LibraryViewType
import com.metrolist.music.constants.PlaylistViewTypeKey
import com.metrolist.music.ui.component.ChipsRow
import com.metrolist.music.utils.rememberEnumPreference

/**
 * Library tab, kept to what YouTube Music's library is about: "Liked music", "Downloaded", the
 * account's playlists (own and saved), saved albums, subscribed artists and podcasts, all synced
 * with YouTube.
 */
@Composable
fun LibraryScreen() {
    val navController = LocalNavController.current
    val database = LocalDatabase.current
    var playlistViewType by rememberEnumPreference(PlaylistViewTypeKey, LibraryViewType.GRID)
    // 0 = All (default: playlists, albums and artists; podcasts and files on the phone keep
    // their own tabs), 1 = Playlists, 2 = Albums, 5 = Artists (subscriptions), 3 = Podcasts,
    // 4 = On device. Shown in YouTube Music's order.
    var tab by rememberSaveable { mutableIntStateOf(TAB_ALL) }
    val chips = listOf(
        TAB_ALL to stringResource(R.string.filter_all),
        1 to stringResource(R.string.filter_playlists),
        2 to stringResource(R.string.filter_albums),
        5 to stringResource(R.string.filter_artists),
        3 to stringResource(R.string.filter_podcasts),
        4 to stringResource(R.string.er_library_device),
    )
    val header = @Composable { ChipsRow(chips = chips, currentValue = tab, onValueUpdate = { tab = it }) }
    // What "All" adds after the playlists: saved albums and subscribed artists, newest first.
    val savedAlbums by remember(database) {
        database.albumsLikedByCreateDateAsc().map { list -> list.filterNot { it.album.isLocal }.reversed() }
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val subscribedArtists by remember(database) {
        database.artistsBookmarkedByCreateDateAsc().map { list ->
            list.filterNot { it.artist.isPodcastChannel || it.artist.isLocal }.reversed()
        }
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    // Pull to refresh: the account's playlists and saved albums from YouTube.
    val syncUtils = LocalSyncUtils.current
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    RefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            if (!isRefreshing) {
                isRefreshing = true
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            syncUtils.syncSavedPlaylistsSuspend()
                            syncUtils.syncLikedAlbumsSuspend()
                            syncUtils.syncArtistsSubscriptionsSuspend()
                            syncUtils.syncPodcastSubscriptionsSuspend()
                        }
                    } finally {
                        isRefreshing = false
                    }
                }
            }
        },
    ) {
        if (tab == 2) {
            LibraryAlbumsScreen(navController, onDeselect = { tab = TAB_ALL }, header = header)
        } else if (tab == 5) {
            LibrarySubscribedArtistsScreen(navController, header = header)
        } else if (tab == 3) {
            LibraryPodcastsScreen(navController, header = header)
        } else if (tab == 4) {
            LibraryDeviceScreen(header = header)
        } else {
            val all = tab == TAB_ALL
            LibraryPlaylistsScreen(
                navController = navController,
                filterContent = header,
                viewType = playlistViewType,
                onViewTypeChange = { playlistViewType = it },
                albums = if (all) savedAlbums else emptyList(),
                artists = if (all) subscribedArtists else emptyList(),
            )
        }
    }
}

private const val TAB_ALL = 0
