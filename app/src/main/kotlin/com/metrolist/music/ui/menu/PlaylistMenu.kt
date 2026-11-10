/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.menu

import com.metrolist.music.utils.ShareItem
import com.metrolist.music.utils.ShareCard
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.music.LocalNavController
import com.metrolist.music.ui.screens.playlist.radioRoute
import com.metrolist.music.utils.deletePlaylistEverywhere
import com.metrolist.music.ui.component.LocalSheetHost
import com.metrolist.music.utils.appScope
import com.metrolist.music.utils.savePlaylistEdits
import com.metrolist.music.utils.YouTubePlaylistEditor
import com.metrolist.music.ui.component.PlaylistEditorSheet
import com.metrolist.music.ui.component.ErToast
import android.content.Intent
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadService
import com.metrolist.innertube.YouTube
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalDownloadUtil
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.db.entities.Playlist
import com.metrolist.music.db.entities.PlaylistSong
import com.metrolist.music.db.entities.Song
import com.metrolist.music.db.entities.SpeedDialItem
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.playback.ExoDownloadService
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.ui.component.DefaultDialog
import com.metrolist.music.ui.component.Material3MenuGroup
import com.metrolist.music.ui.component.Material3MenuItemData
import com.metrolist.music.ui.component.NewAction
import com.metrolist.music.ui.component.NewActionGrid
import com.metrolist.music.ui.component.PlaylistListItem
import com.metrolist.music.ui.component.TextFieldDialog
import com.metrolist.music.ui.menu.ExportDialog
import com.metrolist.music.utils.PlaylistExporter
import com.metrolist.music.utils.getExportFileUri
import com.metrolist.music.utils.saveToPublicDocuments
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

@Composable
fun PlaylistMenu(
    playlist: Playlist,
    coroutineScope: CoroutineScope,
    onDismiss: () -> Unit,
    autoPlaylist: Boolean? = false,
    downloadPlaylist: Boolean? = false,
    songList: List<Song>? = emptyList(),
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val downloadUtil = LocalDownloadUtil.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val navController = LocalNavController.current
    val dbPlaylist by database.playlist(playlist.id).collectAsStateWithLifecycle(initialValue = playlist)
    var songs by remember {
        mutableStateOf(emptyList<Song>())
    }

    LaunchedEffect(Unit) {
        if (autoPlaylist == false) {
            database.playlistSongs(playlist.id).collect {
                songs = it.map(PlaylistSong::song)
            }
        } else {
            if (songList != null) {
                songs = songList
            }
        }
    }

    var downloadState by remember {
        mutableIntStateOf(Download.STATE_STOPPED)
    }

    val editable: Boolean = playlist.playlist.isEditable == true

    val isPinned by database.speedDialDao.isPinned(playlist.id).collectAsStateWithLifecycle(initialValue = false)

    var showExportDialog by remember { mutableStateOf(false) }

    LaunchedEffect(songs) {
        if (songs.isEmpty()) return@LaunchedEffect
        downloadUtil.downloads.collect { downloads ->
            downloadState =
                if (songs.all { downloads[it.id]?.state == Download.STATE_COMPLETED }) {
                    Download.STATE_COMPLETED
                } else if (songs.all {
                        downloads[it.id]?.state == Download.STATE_QUEUED ||
                            downloads[it.id]?.state == Download.STATE_DOWNLOADING ||
                            downloads[it.id]?.state == Download.STATE_COMPLETED
                    }
                ) {
                    Download.STATE_DOWNLOADING
                } else {
                    Download.STATE_STOPPED
                }
        }
    }

    val sheetHost = LocalSheetHost.current

    // Read while the menu is open, so Edit opens with the description, visibility and Collaborate
    // already there. On appScope: opening Edit closes this menu, and the reads should finish.
    LaunchedEffect(playlist.playlist.browseId) {
        val id = playlist.playlist.browseId ?: return@LaunchedEffect
        if (!playlist.playlist.isEditable) return@LaunchedEffect
        if (YouTubePlaylistEditor.cachedDetails(id) == null) appScope.launch { YouTubePlaylistEditor.details(id) }
        appScope.launch { YouTubePlaylistEditor.collaboration(id) }
    }

    // The menu closes first and the editor opens on its own (see [SheetHostState]).
    fun openEditor() {
        val editing = playlist
        onDismiss()
        sheetHost.show {
            PlaylistEditorSheet(
                initialTitle = editing.playlist.name,
                editing = true,
                coverUrl = editing.thumbnails.firstOrNull() ?: editing.playlist.thumbnailUrl,
                loadDetails = editing.playlist.browseId?.let { id -> { YouTubePlaylistEditor.details(id) } },
                initialDetails = editing.playlist.browseId?.let(YouTubePlaylistEditor::cachedDetails),
                defaultCovers = editing.songThumbnails.filterNotNull(),
                collaboratePlaylistId = editing.playlist.browseId?.takeIf { editing.playlist.isEditable },
                initialCustomCover = editing.playlist.browseId == null && editing.playlist.thumbnailUrl?.startsWith("file:") == true,
                onDismiss = sheetHost::dismiss,
                onDone = { title, description, privacy, cover ->
                    savePlaylistEdits(context, database, editing.playlist, title, description, privacy, cover)
                },
            )
        }
    }

    var showRemoveDownloadDialog by remember {
        mutableStateOf(false)
    }

    if (showRemoveDownloadDialog) {
        DefaultDialog(
            onDismiss = { showRemoveDownloadDialog = false },
            content = {
                Text(
                    text =
                        stringResource(
                            R.string.remove_download_playlist_confirm,
                            playlist.playlist.name,
                        ),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                TextButton(
                    onClick = {
                        showRemoveDownloadDialog = false
                    },
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }

                TextButton(
                    onClick = {
                        showRemoveDownloadDialog = false
                        songs.forEach { song ->
                            DownloadService.sendRemoveDownload(
                                context,
                                ExoDownloadService::class.java,
                                song.id,
                                false,
                            )
                        }
                    },
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    var showDeletePlaylistDialog by remember {
        mutableStateOf(false)
    }

    if (showDeletePlaylistDialog) {
        DefaultDialog(
            onDismiss = { showDeletePlaylistDialog = false },
            content = {
                Text(
                    text = stringResource(R.string.delete_playlist_confirm, playlist.playlist.name),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                TextButton(
                    onClick = {
                        showDeletePlaylistDialog = false
                    },
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }

                TextButton(
                    onClick = {
                        showDeletePlaylistDialog = false
                        onDismiss()
                        deletePlaylistEverywhere(context, database, playlist.playlist)
                    },
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    PlaylistListItem(
        playlist = playlist,
        trailingContent = {
            if (playlist.playlist.isEditable != true) {
                IconButton(
                    onClick = {
                        database.query {
                            dbPlaylist?.playlist?.toggleLike()?.let { update(it) }
                        }
                    },
                ) {
                    Icon(
                        painter =
                            painterResource(
                                if (dbPlaylist?.playlist?.bookmarkedAt !=
                                    null
                                ) {
                                    R.drawable.favorite
                                } else {
                                    R.drawable.favorite_border
                                },
                            ),
                        tint =
                            if (dbPlaylist?.playlist?.bookmarkedAt !=
                                null
                            ) {
                                MaterialTheme.colorScheme.error
                            } else {
                                LocalContentColor.current
                            },
                        contentDescription = null,
                    )
                }
            }
        },
    )

    HorizontalDivider()

    Spacer(modifier = Modifier.height(12.dp))

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    LazyColumn(
        contentPadding =
            PaddingValues(
                start = 0.dp,
                top = 0.dp,
                end = 0.dp,
                bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
            ),
    ) {
        item {
            NewActionGrid(
                actions =
                    listOfNotNull(
                        NewAction(
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.play),
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            text = stringResource(R.string.play),
                            onClick = {
                                onDismiss()
                                if (songs.isNotEmpty()) {
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = playlist.playlist.name,
                                            items = songs.map(Song::toMediaItem),
                                        ),
                                    )
                                }
                            },
                        ),
                        NewAction(
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.shuffle),
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            text = stringResource(R.string.shuffle),
                            onClick = {
                                onDismiss()
                                if (songs.isNotEmpty()) {
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = playlist.playlist.name,
                                            items = songs.shuffled().map(Song::toMediaItem),
                                        ),
                                    )
                                }
                            },
                        ),
                        playlist.playlist.browseId?.let { browseId ->
                        NewAction(
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.share),
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            text = stringResource(R.string.share),
                            onClick = {
                                onDismiss()
                                ShareCard.share(
                                    context,
                                    ShareItem(
                                        kind = context.getString(R.string.er_type_playlist),
                                        title = playlist.playlist.name,
                                        subtitle = context.resources.getQuantityString(R.plurals.n_song, playlist.songCount, playlist.songCount),
                                        coverUrl = playlist.thumbnails.firstOrNull() ?: playlist.playlist.thumbnailUrl,
                                        link = "https://music.youtube.com/playlist?list=${browseId.removePrefix("VL")}",
                                    ),
                                )
                            },
                        )
                        },
                    ),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp),
                columns = 3,
            )
        }

        item {
            Material3MenuGroup(
                items =
                    buildList {
                        playlist.playlist.browseId?.let { browseId ->
                            add(
                                Material3MenuItemData(
                                    title = { Text(text = stringResource(R.string.start_radio)) },
                                    description = { Text(text = stringResource(R.string.start_radio_desc)) },
                                    icon = {
                                        Icon(
                                            painter = painterResource(R.drawable.radio),
                                            contentDescription = null,
                                        )
                                    },
                                    // Opens the radio as a page first; nothing plays until a pick.
                                    onClick = {
                                        navController.navigate(
                                            radioRoute(
                                                endpoint = WatchEndpoint(playlistId = "RDAMPL$browseId", params = playlist.playlist.radioEndpointParams),
                                                name = playlist.playlist.name,
                                                thumbnailUrl = playlist.thumbnails.firstOrNull() ?: playlist.playlist.thumbnailUrl,
                                            ),
                                        )
                                        onDismiss()
                                    },
                                ),
                            )
                        }
                        add(
                            Material3MenuItemData(
                                title = { Text(text = stringResource(R.string.play_next)) },
                                description = { Text(text = stringResource(R.string.play_next_desc)) },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.playlist_play),
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    coroutineScope.launch {
                                        playerConnection.playNext(songs.map { it.toMediaItem() })
                                    }
                                    onDismiss()
                                },
                            ),
                        )
                        add(
                            Material3MenuItemData(
                                title = { Text(text = stringResource(R.string.add_to_queue)) },
                                description = { Text(text = stringResource(R.string.add_to_queue_desc)) },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.queue_music),
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    onDismiss()
                                    playerConnection.addToQueue(songs.map { it.toMediaItem() })
                                },
                            ),
                        )
                    },
            )
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        item {
            Material3MenuGroup(
                items =
                    buildList {
                        if (editable && autoPlaylist != true) {
                            add(
                                Material3MenuItemData(
                                    title = { Text(text = stringResource(R.string.edit)) },
                                    description = { Text(text = stringResource(R.string.edit_desc)) },
                                    icon = {
                                        Icon(
                                            painter = painterResource(R.drawable.edit),
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = {
                                        openEditor()
                                    },
                                ),
                            )
                        }
                        add(
                            Material3MenuItemData(
                                title = {
                                    Text(
                                        text = if (isPinned) stringResource(R.string.unpin_from_speed_dial) else stringResource(R.string.pin_to_speed_dial),
                                    )
                                },
                                icon = {
                                    Icon(
                                        painter = painterResource(if (isPinned) R.drawable.remove else R.drawable.add),
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        if (isPinned) {
                                            database.speedDialDao.delete(playlist.id)
                                        } else {
                                            database.speedDialDao.insert(
                                                SpeedDialItem(
                                                    id = playlist.id,
                                                    title = playlist.playlist.name,
                                                    subtitle = null,
                                                    subtitleIds = null,
                                                    thumbnailUrl = playlist.thumbnails.firstOrNull(),
                                                    type = "LOCAL_PLAYLIST",
                                                ),
                                            )
                                        }
                                    }
                                    onDismiss()
                                },
                            ),
                        )
                        if (downloadPlaylist != true) {
                            add(
                                when (downloadState) {
                                    Download.STATE_COMPLETED -> {
                                        Material3MenuItemData(
                                            title = {
                                                Text(
                                                    text = stringResource(R.string.remove_download),
                                                )
                                            },
                                            icon = {
                                                Icon(
                                                    painter = painterResource(R.drawable.offline),
                                                    contentDescription = null,
                                                )
                                            },
                                            onClick = {
                                                showRemoveDownloadDialog = true
                                            },
                                        )
                                    }

                                    Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> {
                                        Material3MenuItemData(
                                            title = { Text(text = stringResource(R.string.downloading)) },
                                            icon = {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(24.dp),
                                                    strokeWidth = 2.dp,
                                                )
                                            },
                                            onClick = {
                                                showRemoveDownloadDialog = true
                                            },
                                        )
                                    }

                                    else -> {
                                        Material3MenuItemData(
                                            title = { Text(text = stringResource(R.string.action_download)) },
                                            description = { Text(text = stringResource(R.string.download_desc)) },
                                            icon = {
                                                Icon(
                                                    painter = painterResource(R.drawable.download),
                                                    contentDescription = null,
                                                )
                                            },
                                            onClick = {
                                                songs.forEach { downloadUtil.download(it) }
                                            },
                                        )
                                    }
                                },
                            )
                        }
                        // Only the owner can delete a YouTube playlist; a followed or joined one would
                        // just disappear here and come back with the next sync.
                        if (autoPlaylist != true && (playlist.playlist.isEditable || playlist.playlist.browseId == null)) {
                            add(
                                Material3MenuItemData(
                                    title = { Text(text = stringResource(R.string.delete)) },
                                    description = { Text(text = stringResource(R.string.delete_desc)) },
                                    icon = {
                                        Icon(
                                            painter = painterResource(R.drawable.delete),
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = {
                                        showDeletePlaylistDialog = true
                                    },
                                ),
                            )
                        }
                        playlist.playlist.shareLink?.let { shareLink ->
                        }
                    },
            )
        }
    }

    val exportPlaylistStr = stringResource(R.string.export_playlist)

    if (showExportDialog) {
        ExportDialog(
            onDismiss = { showExportDialog = false },
            onShare = { format ->
                val playlistSongs =
                    songs.map { s ->
                        com.metrolist.music.db.entities.PlaylistSong(
                            map =
                                com.metrolist.music.db.entities.PlaylistSongMap(
                                    songId = s.id,
                                    playlistId = playlist.id,
                                    position = 0,
                                ),
                            song = s,
                        )
                    }
                val result =
                    when (format) {
                        "csv" -> PlaylistExporter.exportPlaylistAsCSV(context, playlist.playlist.name, playlistSongs)
                        "m3u" -> PlaylistExporter.exportPlaylistAsM3U(context, playlist.playlist.name, playlistSongs)
                        else -> Result.failure(IllegalArgumentException("Unknown format"))
                    }
                result
                    .onSuccess { file ->
                        val uri = getExportFileUri(context, file)
                        val mimeType = if (format == "csv") "text/csv" else "audio/x-mpegurl"
                        val shareIntent =
                            Intent(Intent.ACTION_SEND).apply {
                                type = mimeType
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                        context.startActivity(Intent.createChooser(shareIntent, exportPlaylistStr))
                    }.onFailure {
                        ErToast.show(context, R.string.export_failed)
                    }
                showExportDialog = false
            },
            onSave = { format ->
                val playlistSongs =
                    songs.map { s ->
                        com.metrolist.music.db.entities.PlaylistSong(
                            map =
                                com.metrolist.music.db.entities.PlaylistSongMap(
                                    songId = s.id,
                                    playlistId = playlist.id,
                                    position = 0,
                                ),
                            song = s,
                        )
                    }
                val export =
                    when (format) {
                        "csv" -> PlaylistExporter.exportPlaylistAsCSV(context, playlist.playlist.name, playlistSongs)
                        "m3u" -> PlaylistExporter.exportPlaylistAsM3U(context, playlist.playlist.name, playlistSongs)
                        else -> Result.failure(IllegalArgumentException("Unknown format"))
                    }
                export
                    .onSuccess { file ->
                        val mimeType = if (format == "csv") "text/csv" else "audio/x-mpegurl"
                        val save = saveToPublicDocuments(context, file, mimeType)
                        save
                            .onSuccess { ErToast.show(context, R.string.export_success) }
                            .onFailure { ErToast.show(context, R.string.export_failed) }
                    }.onFailure {
                        ErToast.show(context, R.string.export_failed)
                    }
                showExportDialog = false
            },
        )
    }
}
