/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.menu

import com.metrolist.music.ui.component.ErToast
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadService
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalDownloadUtil
import com.metrolist.music.LocalNavController
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.ListItemHeight
import com.metrolist.music.db.entities.SpeedDialItem
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.playback.ExoDownloadService
import com.metrolist.music.ui.component.BottomSheetState
import com.metrolist.music.ui.component.ListDialog
import com.metrolist.music.ui.component.Material3MenuGroup
import com.metrolist.music.ui.component.Material3MenuItemData
import com.metrolist.music.ui.component.NewAction
import com.metrolist.music.ui.component.NewActionGrid
import com.metrolist.music.ui.theme.Size
import com.metrolist.music.ui.theme.Spacing
import com.metrolist.music.ui.utils.podcastRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Opens the system share sheet for a YouTube Music link. */
fun shareLink(context: android.content.Context, title: String, link: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, title)
        .putExtra(Intent.EXTRA_TEXT, link)
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
private fun MenuIcon(icon: Int) {
    Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(Size.Icon))
}

/**
 * Menu for the playing track: one flat list, no audio controls. Volume and speed belong to the
 * system; the equalizer lives in Settings.
 */
@Composable
fun PlayerMenu(
    mediaMetadata: MediaMetadata?,
    playerBottomSheetState: BottomSheetState,
    isQueueTrigger: Boolean? = false,
    onShowDetailsDialog: () -> Unit,
    onDismiss: () -> Unit,
) {
    mediaMetadata ?: return
    val navController = LocalNavController.current
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val coroutineScope = rememberCoroutineScope()
    val downloadUtil = LocalDownloadUtil.current

    val librarySong by database.song(mediaMetadata.id).collectAsStateWithLifecycle(initialValue = null)
    val download by downloadUtil.getDownload(mediaMetadata.id).collectAsStateWithLifecycle(initialValue = null)
    val isPinned by database.speedDialDao.isPinned(mediaMetadata.id).collectAsStateWithLifecycle(initialValue = false)
    val artists = remember(mediaMetadata.artists) { mediaMetadata.artists.filter { it.id != null } }
    // Podcast episodes carry a playlist id as their "album".
    val isPodcast = mediaMetadata.album?.let { !it.id.startsWith("MPREb_") } ?: false

    var showChoosePlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var showSelectArtistDialog by rememberSaveable { mutableStateOf(false) }

    AddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        onGetSong = {
            database.withTransaction { insert(mediaMetadata) }
            listOf(mediaMetadata.id)
        },
        onGetSongIds = { listOf(mediaMetadata.id) },
        onDismiss = { showChoosePlaylistDialog = false },
    )

    if (showSelectArtistDialog) {
        ListDialog(onDismiss = { showSelectArtistDialog = false }) {
            items(artists) { artist ->
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .fillParentMaxWidth()
                        .height(ListItemHeight)
                        .clickable {
                            navController.navigate("artist/${artist.id}")
                            showSelectArtistDialog = false
                            playerBottomSheetState.collapseSoft()
                            onDismiss()
                        }.padding(horizontal = Spacing.XL),
                ) {
                    Text(artist.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }

    val startingRadioText = stringResource(R.string.starting_radio)
    LazyColumn(
        contentPadding = PaddingValues(
            top = Spacing.S,
            bottom = Spacing.S + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
        ),
    ) {
        item(key = "actions", contentType = "actions") {
            NewActionGrid(
                actions = listOf(
                    NewAction(
                        icon = { MenuIcon(R.drawable.radio) },
                        text = stringResource(R.string.start_radio),
                        onClick = {
                            ErToast.show(context, startingRadioText)
                            playerConnection.startRadioSeamlessly()
                            onDismiss()
                        },
                    ),
                    NewAction(
                        icon = { MenuIcon(R.drawable.playlist_add) },
                        text = stringResource(R.string.add_to_playlist),
                        onClick = { showChoosePlaylistDialog = true },
                    ),
                    NewAction(
                        icon = { MenuIcon(R.drawable.share) },
                        text = stringResource(R.string.share),
                        onClick = {
                            shareLink(context, mediaMetadata.title, "https://music.youtube.com/watch?v=${mediaMetadata.id}")
                            onDismiss()
                        },
                    ),
                ),
                columns = 3,
                modifier = Modifier.padding(horizontal = Spacing.S, vertical = Spacing.S),
            )
        }

        item(key = "items", contentType = "items") {
            Material3MenuGroup(
                items = buildList {
                    if (artists.isNotEmpty() && !isPodcast) {
                        add(
                            Material3MenuItemData(
                                title = { Text(stringResource(R.string.view_artist)) },
                                description = { Text(mediaMetadata.artists.joinToString { it.name }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                icon = { MenuIcon(R.drawable.artist) },
                                onClick = {
                                    if (artists.size == 1) {
                                        navController.navigate("artist/${artists[0].id}")
                                        playerBottomSheetState.collapseSoft()
                                        onDismiss()
                                    } else {
                                        showSelectArtistDialog = true
                                    }
                                },
                            ),
                        )
                    }
                    mediaMetadata.album?.let { album ->
                        add(
                            Material3MenuItemData(
                                title = { Text(stringResource(if (isPodcast) R.string.view_podcast else R.string.view_album)) },
                                description = { Text(album.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                icon = { MenuIcon(if (isPodcast) R.drawable.mic else R.drawable.album) },
                                onClick = {
                                    navController.navigate(if (isPodcast) podcastRoute(album.id) else "album/${album.id}")
                                    playerBottomSheetState.collapseSoft()
                                    onDismiss()
                                },
                            ),
                        )
                    }
                    add(
                        when (download?.state) {
                            Download.STATE_COMPLETED -> Material3MenuItemData(
                                title = { Text(stringResource(R.string.remove_download)) },
                                icon = { MenuIcon(R.drawable.offline) },
                                onClick = {
                                    DownloadService.sendRemoveDownload(context, ExoDownloadService::class.java, mediaMetadata.id, false)
                                },
                            )
                            Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> Material3MenuItemData(
                                title = { Text(stringResource(R.string.downloading)) },
                                icon = { CircularProgressIndicator(Modifier.size(Size.Icon), strokeWidth = Spacing.XXS) },
                                onClick = {
                                    DownloadService.sendRemoveDownload(context, ExoDownloadService::class.java, mediaMetadata.id, false)
                                },
                            )
                            else -> Material3MenuItemData(
                                title = { Text(stringResource(R.string.action_download)) },
                                icon = { MenuIcon(R.drawable.download) },
                                onClick = { downloadUtil.download(mediaMetadata) },
                            )
                        },
                    )
                    add(
                        Material3MenuItemData(
                            title = { Text(stringResource(if (isPinned) R.string.unpin_from_speed_dial else R.string.pin_to_speed_dial)) },
                            icon = { MenuIcon(if (isPinned) R.drawable.remove else R.drawable.add) },
                            onClick = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    if (isPinned) {
                                        database.speedDialDao.delete(mediaMetadata.id)
                                    } else {
                                        database.speedDialDao.insert(SpeedDialItem.fromYTItem(mediaMetadata.toYTItem()))
                                    }
                                }
                                onDismiss()
                            },
                        ),
                    )
                    add(
                        Material3MenuItemData(
                            title = { Text(stringResource(R.string.details)) },
                            icon = { MenuIcon(R.drawable.info) },
                            onClick = {
                                onShowDetailsDialog()
                                onDismiss()
                            },
                        ),
                    )
                },
            )
        }
    }
}
