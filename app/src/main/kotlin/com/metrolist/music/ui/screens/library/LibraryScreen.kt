/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.library

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
    // 1 = Playlists (default), 2 = Albums, 5 = Artists (subscriptions), 3 = Podcasts,
    // 4 = On device (files on the phone). Shown in YouTube Music's order.
    var tab by rememberSaveable { mutableIntStateOf(1) }
    val chips = listOf(
        1 to stringResource(R.string.filter_playlists),
        2 to stringResource(R.string.filter_albums),
        5 to stringResource(R.string.filter_artists),
        3 to stringResource(R.string.filter_podcasts),
        4 to stringResource(R.string.er_library_device),
    )
    val header = @Composable { ChipsRow(chips = chips, currentValue = tab, onValueUpdate = { tab = it }) }
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
            LibraryAlbumsScreen(navController, onDeselect = { tab = 1 }, header = header)
        } else if (tab == 5) {
            LibrarySubscribedArtistsScreen(navController, header = header)
        } else if (tab == 3) {
            LibraryPodcastsScreen(navController, header = header)
        } else if (tab == 4) {
            LibraryDeviceScreen(header = header)
        } else {
            LibraryPlaylistsScreen(
                navController = navController,
                filterContent = header,
                viewType = playlistViewType,
                onViewTypeChange = { playlistViewType = it },
            )
        }
    }
}
