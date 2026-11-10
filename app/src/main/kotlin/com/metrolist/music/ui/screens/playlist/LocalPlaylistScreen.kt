/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.playlist

import com.metrolist.music.ui.component.DetailPane
import com.metrolist.music.ui.theme.topBarBackdrop
import com.metrolist.music.ui.theme.backdropTopBarColors
import com.metrolist.music.ui.theme.rememberUseDarkTheme
import com.metrolist.music.utils.deletePlaylistEverywhere
import androidx.compose.material3.CircularProgressIndicator
import com.metrolist.music.ui.component.RefreshBox
import com.metrolist.music.ui.theme.Spacing
import com.metrolist.music.ui.theme.Size
import androidx.compose.runtime.collectAsState
import com.metrolist.music.utils.Collaborations
import com.metrolist.music.ui.menu.PlaylistMenu
import timber.log.Timber
import com.metrolist.music.utils.appScope
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.metrolist.music.utils.savePlaylistEdits
import kotlinx.coroutines.flow.first
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.utils.YouTubePlaylistEditor
import com.metrolist.music.ui.component.AddSongsSheet
import com.metrolist.music.ui.component.PlaylistEditorSheet
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.util.fastForEachReversed
import androidx.compose.ui.util.fastSumBy
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalDownloadUtil
import com.metrolist.music.LocalNavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.LocalSyncUtils
import com.metrolist.music.R
import com.metrolist.music.constants.DarkModeKey
import com.metrolist.music.constants.PlaylistEditLockKey
import com.metrolist.music.constants.PlaylistSongSortDescendingKey
import com.metrolist.music.constants.PlaylistSongSortType
import com.metrolist.music.constants.PlaylistSongSortTypeKey
import com.metrolist.music.constants.SwipeToRemoveSongKey
import com.metrolist.music.db.entities.Playlist
import com.metrolist.music.db.entities.PlaylistSong
import com.metrolist.music.db.entities.PlaylistSongMap
import com.metrolist.music.extensions.move
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.playback.ExoDownloadService
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.ui.component.ActionPromptDialog
import com.metrolist.music.ui.component.DefaultDialog
import com.metrolist.music.ui.component.DraggableScrollbar
import com.metrolist.music.ui.component.EmptyPlaceholder
import com.metrolist.music.ui.component.ExpandableText
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.OverlayEditButton
import com.metrolist.music.ui.component.ErToast
import com.metrolist.music.ui.component.SongListItem
import com.metrolist.music.ui.component.SortHeader
import com.metrolist.music.ui.component.TextFieldDialog
import com.metrolist.music.ui.menu.CustomThumbnailMenu
import com.metrolist.music.ui.menu.LocalPlaylistMenu
import com.metrolist.music.ui.menu.SelectionSongMenu
import com.metrolist.music.ui.menu.SongMenu
import com.metrolist.music.ui.screens.settings.DarkMode
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.makeTimeString
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.utils.reportException
import com.metrolist.music.viewmodels.LocalPlaylistViewModel
import com.yalantis.ucrop.UCrop
import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.LocalDateTime

@SuppressLint("RememberReturnType")
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LocalPlaylistScreen(
    navController: NavController,
    viewModel: LocalPlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current

    // Deleted (from its menu or the dialog here, once YouTube agreed): back to the list instead
    // of staying on an empty page.
    val shownPlaylist by viewModel.playlist.collectAsStateWithLifecycle()
    var playlistWasShown by remember { mutableStateOf(false) }
    LaunchedEffect(shownPlaylist) {
        if (shownPlaylist != null) {
            playlistWasShown = true
        } else if (playlistWasShown) {
            playlistWasShown = false
            navController.popBackStack()
        }
    }
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val songs by viewModel.playlistSongs.collectAsStateWithLifecycle()
    val onlinePlaylist by viewModel.onlinePlaylist.collectAsStateWithLifecycle()
    val mutableSongs = remember { mutableStateListOf<PlaylistSong>() }
    val playlistLength =
        remember(songs) {
            songs.fastSumBy { it.song.song.duration }
        }
    val (sortType, onSortTypeChange) =
        rememberEnumPreference(
            PlaylistSongSortTypeKey,
            PlaylistSongSortType.CUSTOM,
        )
    val (sortDescending, onSortDescendingChange) =
        rememberPreference(
            PlaylistSongSortDescendingKey,
            true,
        )
    var locked by rememberPreference(PlaylistEditLockKey, defaultValue = true)

    val coroutineScope = rememberCoroutineScope()
    val syncUtils = LocalSyncUtils.current
    val snackbarHostState = remember { SnackbarHostState() }

    var isSearching by rememberSaveable { mutableStateOf(false) }

    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }

    val filteredSongs =
        remember(songs, query) {
            if (query.text.isEmpty()) {
                songs
            } else {
                songs.filter { song ->
                    song.song.song.title
                        .contains(query.text, ignoreCase = true) ||
                        song.song.artists
                            .fastAny { it.name.contains(query.text, ignoreCase = true) }
                }
            }
        }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
        }
    }

    var inSelectMode by remember { mutableStateOf(false) }
    val selection =
        remember {
            mutableStateListOf<Int>()
        }
    var selectionAnchorMapId by remember { mutableStateOf<Int?>(null) }
    val onExitSelectionMode = {
        inSelectMode = false
        selection.clear()
        selectionAnchorMapId = null
    }

    if (isSearching) {
        BackHandler {
            isSearching = false
            query = TextFieldValue()
        }
    } else if (inSelectMode) {
        BackHandler(onBack = onExitSelectionMode)
    }

    val downloadUtil = LocalDownloadUtil.current
    var downloadState by remember {
        mutableIntStateOf(Download.STATE_STOPPED)
    }

    val editable: Boolean = playlist?.playlist?.isEditable == true
    val joinedCollaborations by remember(context) { Collaborations.joined(context) }.collectAsState(initial = emptySet())
    val people by viewModel.people.collectAsStateWithLifecycle()
    val canAddSongs = editable || people?.canAddSongs == true ||
        playlist?.playlist?.browseId?.removePrefix("VL") in joinedCollaborations

    // The owner may remove any song; a collaborator only those it added. Anything else would only
    // vanish here while YouTube keeps it, and come back on the next sync.
    fun canRemove(songId: String) = editable || songId in people?.removable.orEmpty()

    LaunchedEffect(songs) {
        selection.fastForEachReversed { mapId ->
            if (songs.find { it.map.id == mapId } == null) {
                selection.remove(Integer.valueOf(mapId))
            }
        }

        if (selectionAnchorMapId != null && songs.none { it.map.id == selectionAnchorMapId }) {
            selectionAnchorMapId = songs.firstOrNull { it.map.id in selection }?.map?.id
        }
    }

    LaunchedEffect(songs) {
        mutableSongs.apply {
            clear()
            addAll(songs)
        }
        if (songs.isEmpty()) return@LaunchedEffect
        downloadUtil.downloads.collect { downloads ->
            downloadState =
                if (songs.all { downloads[it.song.id]?.state == Download.STATE_COMPLETED }) {
                    Download.STATE_COMPLETED
                } else if (songs.all {
                        downloads[it.song.id]?.state == Download.STATE_QUEUED ||
                            downloads[it.song.id]?.state == Download.STATE_DOWNLOADING ||
                            downloads[it.song.id]?.state == Download.STATE_COMPLETED
                    }
                ) {
                    Download.STATE_DOWNLOADING
                } else {
                    Download.STATE_STOPPED
                }
        }
    }

    var showEditDialog by remember {
        mutableStateOf(false)
    }

    var showAddSongs by rememberSaveable { mutableStateOf(false) }

    if (showEditDialog) {
        playlist?.let { current ->
            val playlistEntity = current.playlist
            PlaylistEditorSheet(
                initialTitle = playlistEntity.name,
                editing = true,
                coverUrl = current.thumbnails.firstOrNull() ?: playlistEntity.thumbnailUrl,
                loadDetails = playlistEntity.browseId?.let { id -> { YouTubePlaylistEditor.details(id) } },
                initialDetails = playlistEntity.browseId?.let(YouTubePlaylistEditor::cachedDetails),
                defaultCovers = current.songThumbnails.filterNotNull(),
                collaboratePlaylistId = playlistEntity.browseId?.takeIf { playlistEntity.isEditable },
                initialCustomCover = playlistEntity.browseId == null && playlistEntity.thumbnailUrl?.startsWith("file:") == true,
                initialCollaboration = viewModel.collaboration.value,
                onCollaboration = viewModel::collaborationChanged,
                onDismiss = { showEditDialog = false },
                onDone = { title, description, privacy, cover ->
                    savePlaylistEdits(context, database, playlistEntity, title, description, privacy, cover)
                },
            )
        }
    }

    if (showAddSongs) {
        AddSongsSheet(
            inPlaylist = remember(songs) { songs.map { it.song.id }.toSet() },
            onRemove = { songId ->
                if (!canRemove(songId)) {
                    ErToast.show(context, R.string.er_remove_not_yours)
                    return@AddSongsSheet
                }
                val target = playlist ?: return@AddSongsSheet
                val entry = songs.lastOrNull { it.song.id == songId } ?: return@AddSongsSheet
                database.transaction {
                    move(entry.map.playlistId, entry.map.position, Int.MAX_VALUE)
                    delete(entry.map.copy(position = Int.MAX_VALUE))
                }
                target.playlist.browseId?.let { browseId ->
                    syncUtils.scheduleRemoveFromPlaylist(browseId, songId, entry.map.playlistId) { entry.map.setVideoId }
                }
            },
            onDismiss = {
                showAddSongs = false
                // Shows the account's picture on the songs it just added.
                viewModel.refreshPeople()
            },
            onAdd = { song ->
                val target = playlist ?: return@AddSongsSheet
                appScope.launch {
                    Timber.tag("SonaraWeb").d("add from sheet: ${song.id} -> ${target.id} (${target.playlist.browseId})")
                    database.withTransaction { insert(song.toMediaMetadata()) }
                    val fresh = database.playlist(target.id).first() ?: return@launch
                    database.addSongsToPlaylist(fresh, listOf(song.id to null))
                    fresh.playlist.browseId?.let { syncUtils.scheduleAddToPlaylist(it, fresh.id, listOf(song.id)) }
                }
            },
        )
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
                            playlist?.playlist!!.name,
                        ),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                TextButton(
                    onClick = { showRemoveDownloadDialog = false },
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }

                TextButton(
                    onClick = {
                        showRemoveDownloadDialog = false
                        if (!editable) {
                            database.transaction {
                                playlist?.id?.let { clearPlaylist(it) }
                            }
                        }
                        songs.forEach { song ->
                            DownloadService.sendRemoveDownload(
                                context,
                                ExoDownloadService::class.java,
                                song.song.id,
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
                    text =
                        stringResource(
                            R.string.delete_playlist_confirm,
                            playlist?.playlist!!.name,
                        ),
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
                        playlist?.let { deletePlaylistEverywhere(context, database, it.playlist) }
                    },
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    val headerItems = 2
    val lazyListState = rememberLazyListState()
    var dragInfo by remember {
        mutableStateOf<Pair<Int, Int>?>(null)
    }
    val reorderableState =
        rememberReorderableLazyListState(
            lazyListState = lazyListState,
            scrollThresholdPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        ) { from, to ->
            if (to.index >= headerItems && from.index >= headerItems) {
                val currentDragInfo = dragInfo
                dragInfo =
                    if (currentDragInfo == null) {
                        (from.index - headerItems) to (to.index - headerItems)
                    } else {
                        currentDragInfo.first to (to.index - headerItems)
                    }

                mutableSongs.move(from.index - headerItems, to.index - headerItems)
            }
        }

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging) {
            dragInfo?.let { (from, to) ->
                viewModel.viewModelScope.launch(Dispatchers.IO) {
                    database.withTransaction {
                        move(viewModel.playlistId, from, to)
                    }

                    // Sync order with YT Music
                    val browseId = viewModel.playlist.value?.playlist?.browseId
                    if (browseId != null) {
                        val playlistSongMap = database.playlistSongMaps(viewModel.playlistId, 0)
                        val setVideoId = playlistSongMap.getOrNull(to)?.setVideoId
                        val successorSetVideoId = playlistSongMap.getOrNull(to + 1)?.setVideoId

                        if (setVideoId != null) {
                            YouTube.moveSongPlaylist(
                                browseId,
                                setVideoId,
                                successorSetVideoId,
                            )
                        }
                    }
                }

                dragInfo = null
            }
        }
    }

    val showTopBarTitle by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex > 0
        }
    }

    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    RefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refresh,
    ) {
        // The header: the list's first item, or the left pane on a landscape tablet (DetailPane).
        val detailHeader: (@Composable () -> Unit)? =
            playlist?.takeIf { !isSearching }?.let { playlist ->
                @Composable {
                            LocalPlaylistHeader(
                                playlist = playlist,
                                songs = songs,
                                onlinePlaylist = onlinePlaylist,
                                onShowEditDialog = { showEditDialog = true },
                                onShowRemoveDownloadDialog = { showRemoveDownloadDialog = true },
                                onshowDeletePlaylistDialog = { showDeletePlaylistDialog = true },
                                onStartSearch = { isSearching = true },
                                snackbarHostState = snackbarHostState,
                                modifier = Modifier,
                            )
                        }
            }
        DetailPane(header = detailHeader) { headerInList ->
            LazyColumn(
                state = lazyListState,
                contentPadding = LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime).asPaddingValues(),
            ) {
                playlist?.let { playlist ->
                    run {
                        if (!isSearching) {
                            if (headerInList) {
                                item(key = "playlist_header") {
                                    detailHeader?.invoke()
                                }
                            }
                        }
    
                        if (canAddSongs) {
                            item(key = "add_song", contentType = "add_song") {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showAddSongs = true }
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                    ) {
                                        Icon(painterResource(R.drawable.add), contentDescription = null)
                                    }
                                    Spacer(Modifier.width(16.dp))
                                    Text(stringResource(R.string.er_add_a_song), style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
    
                        if (playlist.songCount == 0) {
                            item(key = "empty_placeholder", contentType = "empty") {
                                // Still being filled from YouTube: a loading spinner, not "empty".
                                if (isRefreshing) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp).animateItem(),
                                    ) { CircularProgressIndicator() }
                                } else {
                                    EmptyPlaceholder(
                                        icon = R.drawable.music_note,
                                        text = stringResource(R.string.playlist_is_empty),
                                        modifier = Modifier.animateItem(),
                                    )
                                }
                            }
                        }
    
                        item(key = "controls_row") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier =
                                    Modifier
                                        .padding(start = 16.dp)
                                        .animateItem(),
                            ) {
                                SortHeader(
                                    sortType = sortType,
                                    sortDescending = sortDescending,
                                    onSortTypeChange = onSortTypeChange,
                                    onSortDescendingChange = onSortDescendingChange,
                                    sortTypeText = { sortType ->
                                        when (sortType) {
                                            PlaylistSongSortType.CUSTOM -> R.string.sort_by_custom
                                            PlaylistSongSortType.CREATE_DATE -> R.string.sort_by_create_date
                                            PlaylistSongSortType.NAME -> R.string.sort_by_name
                                            PlaylistSongSortType.ARTIST -> R.string.sort_by_artist
                                            PlaylistSongSortType.PLAY_TIME -> R.string.sort_by_play_time
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                if (editable) {
                                    IconButton(
                                        onClick = { locked = !locked },
                                        modifier = Modifier.padding(horizontal = 6.dp),
                                    ) {
                                        Icon(
                                            painter = painterResource(if (locked) R.drawable.lock else R.drawable.lock_open),
                                            contentDescription = null,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
    
                val displayedSongs = if (isSearching) filteredSongs else mutableSongs
    
                itemsIndexed(
                    items = displayedSongs,
                    key = { _, song -> song.map.id },
                ) { index, song ->
                    ReorderableItem(
                        state = reorderableState,
                        key = song.map.id,
                    ) {
                        val currentItem by rememberUpdatedState(song)
    
                        fun deleteFromPlaylist() {
                            // Capture values before deletion — DB entry will be gone afterwards
                            val browseId = playlist?.playlist?.browseId
                            val setVideoId = currentItem.map.setVideoId
                            val songId = currentItem.map.songId
                            val playlistId = currentItem.map.playlistId
    
                            database.transaction {
                                move(playlistId, currentItem.map.position, Int.MAX_VALUE)
                                delete(currentItem.map.copy(position = Int.MAX_VALUE))
                            }
    
                            if (browseId != null) {
                                syncUtils.scheduleRemoveFromPlaylist(
                                    browseId,
                                    songId,
                                    playlistId
                                ) {
                                    setVideoId
                                }
                            }
                        }
    
                        val swipeRemoveEnabled by rememberPreference(SwipeToRemoveSongKey, defaultValue = false)
                        val dismissBoxState =
                            rememberSwipeToDismissBoxState(
                                positionalThreshold = { totalDistance -> totalDistance },
                            )
                        var processedDismiss by remember { mutableStateOf(false) }
                        LaunchedEffect(dismissBoxState.currentValue) {
                            val dv = dismissBoxState.currentValue
                            if (swipeRemoveEnabled && !processedDismiss && (
                                    dv == SwipeToDismissBoxValue.StartToEnd ||
                                        dv == SwipeToDismissBoxValue.EndToStart
                                )
                            ) {
                                processedDismiss = true
                                deleteFromPlaylist()
                            }
                            if (dv == SwipeToDismissBoxValue.Settled) {
                                processedDismiss = false
                            }
                        }
    
                        val onCheckedChange: (Boolean) -> Unit = {
                            if (it) {
                                selection.add(song.map.id)
                            } else {
                                selection.remove(Integer.valueOf(song.map.id))
                            }
                        }
    
                        val content: @Composable () -> Unit = {
                            SongListItem(
                                song = song.song,
                                isActive = song.song.id == mediaMetadata?.id,
                                isPlaying = isPlaying,
                                showInLibraryIcon = true,
                                // Swipe to remove (if turned on) takes the swipe here instead of play next / queue.
                                isSwipeable = !(swipeRemoveEnabled && canRemove(song.song.id)) && !inSelectMode,
                                trailingContent = {
                                    // Who added it, in a collaborative playlist (as YouTube Music shows).
                                    people?.addedBy?.get(song.song.id)?.let { ContributorAvatar(it) }
                                    if (inSelectMode) {
                                        Checkbox(
                                            checked = selection.contains(song.map.id),
                                            onCheckedChange = onCheckedChange,
                                        )
                                    } else {
                                        IconButton(
                                            onClick = {
                                                menuState.show {
                                                    SongMenu(
                                                        originalSong = song.song,
                                                        playlistSong = song.takeIf { canRemove(it.song.id) },
                                                        playlistBrowseId = playlist?.playlist?.browseId,
                                                        onDismiss = menuState::dismiss,
                                                    )
                                                }
                                            },
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.more_vert),
                                                contentDescription = null,
                                            )
                                        }
    
                                        if (sortType == PlaylistSongSortType.CUSTOM && !locked && !inSelectMode && !isSearching && editable) {
                                            IconButton(
                                                onClick = { },
                                                modifier = Modifier.draggableHandle(),
                                            ) {
                                                Icon(
                                                    painter = painterResource(R.drawable.drag_handle),
                                                    contentDescription = null,
                                                )
                                            }
                                        }
                                    }
                                },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {
                                                if (inSelectMode) {
                                                    onCheckedChange(!selection.contains(song.map.id))
                                                } else if (song.song.id == mediaMetadata?.id) {
                                                    playerConnection.openPlayer()
                                                } else {
                                                    playerConnection.playQueue(
                                                        ListQueue(
                                                            title = playlist!!.playlist.name,
                                                            items = songs.map { it.song.toMediaItem() },
                                                            startIndex = songs.indexOfFirst { it.map.id == song.map.id },
                                                        ),
                                                    )
                                                }
                                            },
                                            onLongClick = {
                                                if (!inSelectMode) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    inSelectMode = true
                                                    onCheckedChange(true)
                                                    selectionAnchorMapId = song.map.id
                                                } else {
                                                    val anchorIndex =
                                                        selectionAnchorMapId?.let { anchorMapId ->
                                                            displayedSongs.indexOfFirst { it.map.id == anchorMapId }
                                                        } ?: -1
    
                                                    if (anchorIndex == -1) {
                                                        onCheckedChange(true)
                                                        selectionAnchorMapId = song.map.id
                                                    } else {
                                                        val range = if (anchorIndex <= index) anchorIndex..index else index..anchorIndex
                                                        for (rangeIndex in range) {
                                                            val rangeMapId = displayedSongs[rangeIndex].map.id
                                                            if (rangeMapId !in selection) {
                                                                selection.add(rangeMapId)
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                        ),
                            )
                        }
    
                        if (locked || inSelectMode || !swipeRemoveEnabled || !canRemove(song.song.id)) {
                            Box(modifier = Modifier.animateItem()) {
                                content()
                            }
                        } else {
                            SwipeToDismissBox(
                                state = dismissBoxState,
                                backgroundContent = {},
                                modifier = Modifier.animateItem(),
                            ) {
                                content()
                            }
                        }
                    }
                }
            }
        }

        DraggableScrollbar(
            modifier =
                Modifier
                    .padding(
                        LocalPlayerAwareWindowInsets.current
                            .union(WindowInsets.ime)
                            .asPaddingValues(),
                    ).align(Alignment.CenterEnd),
            scrollState = lazyListState,
            headerItems = 2,
        )

        TopAppBar(
            colors = backdropTopBarColors(),
            modifier = Modifier.topBarBackdrop(),
            title = {
                if (inSelectMode) {
                    Text(pluralStringResource(R.plurals.n_selected, selection.size, selection.size))
                } else if (isSearching) {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.search),
                                style = MaterialTheme.typography.titleLarge,
                            )
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        colors =
                            TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                            ),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                    )
                } else if (showTopBarTitle) {
                    Text(playlist?.playlist?.name.orEmpty())
                }
            },
            navigationIcon = {
                if (inSelectMode) {
                    IconButton(onClick = onExitSelectionMode) {
                        Icon(
                            painter = painterResource(R.drawable.close),
                            contentDescription = null,
                        )
                    }
                } else {
                    IconButton(
                        onClick = {
                            if (isSearching) {
                                isSearching = false
                                query = TextFieldValue()
                            } else {
                                navController.navigateUp()
                            }
                        },
                        onLongClick = {
                            if (!isSearching) {
                                navController.backToMain()
                            }
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                }
            },
            actions = {
                if (inSelectMode) {
                    Checkbox(
                        checked = selection.size == songs.size && selection.isNotEmpty(),
                        onCheckedChange = {
                            if (selection.size == songs.size) {
                                selection.clear()
                            } else {
                                selection.clear()
                                selection.addAll(songs.map { it.map.id })
                            }
                        },
                    )
                    IconButton(
                        enabled = selection.isNotEmpty(),
                        onClick = {
                            menuState.show {
                                SelectionSongMenu(
                                    songSelection =
                                        selection.mapNotNull { mapId ->
                                            songs.find { it.map.id == mapId }?.song
                                        },
                                    songPosition =
                                        selection.mapNotNull { mapId ->
                                            songs.find { it.map.id == mapId }?.takeIf { canRemove(it.song.id) }?.map
                                        },
                                    onDismiss = menuState::dismiss,
                                    clearAction = onExitSelectionMode,
                                )
                            }
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.more_vert),
                            contentDescription = null,
                        )
                    }
                } else if (!isSearching) {
                    // Only search button remains in TopAppBar
                    IconButton(
                        onClick = { isSearching = true },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.search),
                            contentDescription = null,
                        )
                    }
                }
            },
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime))
                    .align(Alignment.BottomCenter),
        )
    }
}

@Composable
fun LocalPlaylistHeader(
    playlist: Playlist,
    songs: List<PlaylistSong>,
    onlinePlaylist: PlaylistItem?,
    onShowEditDialog: () -> Unit,
    onShowRemoveDownloadDialog: () -> Unit,
    onshowDeletePlaylistDialog: () -> Unit,
    onStartSearch: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier,
) {
    val navController = LocalNavController.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val context = LocalContext.current
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val syncUtils = LocalSyncUtils.current
    val scope = rememberCoroutineScope()
    val editPlaylistCoverStr = stringResource(R.string.edit_playlist_cover)
    val playlistSyncedStr = stringResource(R.string.playlist_synced)

    val playlistLength =
        remember(songs) {
            songs.fastSumBy { it.song.song.duration }
        }

    val downloadUtil = LocalDownloadUtil.current
    var downloadState by remember {
        mutableIntStateOf(Download.STATE_STOPPED)
    }

    val liked = playlist.playlist.bookmarkedAt != null
    val editable: Boolean = playlist.playlist.isEditable

    val overrideThumbnail = remember { mutableStateOf<String?>(null) }
    var isCustomThumbnail: Boolean =
        playlist.thumbnails.firstOrNull()?.let {
            it.contains("studio_square_thumbnail") || Uri.parse(it).authority == "${context.packageName}.FileProvider"
        } ?: false

    val result = remember { mutableStateOf<Uri?>(null) }
    var pendingCropDestUri by remember { mutableStateOf<Uri?>(null) }
    var showEditNoteDialog by remember { mutableStateOf(false) }

    val cropLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            if (res.resultCode == android.app.Activity.RESULT_OK) {
                val output = res.data?.let { UCrop.getOutput(it) } ?: pendingCropDestUri
                if (output != null) result.value = output
            }
        }

    val cropColor = MaterialTheme.colorScheme
    val darkTheme = rememberUseDarkTheme()

    val pickLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.PickVisualMedia(),
        ) { uri ->
            uri?.let { sourceUri ->
                val destFile = java.io.File(context.cacheDir, "playlist_cover_crop_${System.currentTimeMillis()}.jpg")
                val destUri = FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", destFile)
                pendingCropDestUri = destUri

                val options =
                    UCrop.Options().apply {
                        setCompressionFormat(Bitmap.CompressFormat.JPEG)
                        setCompressionQuality(90)
                        setHideBottomControls(true)
                        setToolbarTitle(editPlaylistCoverStr)

                        setStatusBarLight(!darkTheme)

                        setToolbarColor(cropColor.surface.toArgb())
                        setToolbarWidgetColor(cropColor.inverseSurface.toArgb())
                        setRootViewBackgroundColor(cropColor.surface.toArgb())
                        setLogoColor(cropColor.surface.toArgb())
                    }

                val intent =
                    UCrop
                        .of(sourceUri, destUri)
                        .withAspectRatio(1f, 1f)
                        .withOptions(options)
                        .getIntent(context)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                cropLauncher.launch(intent)
            }
        }

    LaunchedEffect(result.value) {
        val uri = result.value ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            when {
                playlist.playlist.browseId == null -> {
                    overrideThumbnail.value = uri.toString()
                    isCustomThumbnail = true

                    // Update the database with the new thumbnail
                    database.query {
                        update(playlist.playlist.copy(thumbnailUrl = uri.toString()))
                    }
                }

                else -> {
                    val bytes = uriToByteArray(context, uri)
                    YouTube
                        .uploadCustomThumbnailLink(
                            playlist.playlist.browseId,
                            bytes!!,
                        ).onSuccess { newThumbnailUrl ->
                            overrideThumbnail.value = newThumbnailUrl
                            isCustomThumbnail = true

                            // Update the database with the new thumbnail URL
                            database.query {
                                update(playlist.playlist.copy(thumbnailUrl = newThumbnailUrl))
                            }
                        }.onFailure {
                            if (it is ClientRequestException) {
                                snackbarHostState.showSnackbar("${it.response.status.value} ${it.response.status.description}")
                            }
                            reportException(it)
                        }
                }
            }
        }
    }

    LaunchedEffect(songs) {
        if (songs.isEmpty()) return@LaunchedEffect
        downloadUtil.downloads.collect { downloads ->
            downloadState =
                if (songs.all { downloads[it.song.id]?.state == Download.STATE_COMPLETED }) {
                    Download.STATE_COMPLETED
                } else if (songs.all {
                        downloads[it.song.id]?.state == Download.STATE_QUEUED ||
                            downloads[it.song.id]?.state == Download.STATE_DOWNLOADING ||
                            downloads[it.song.id]?.state == Download.STATE_COMPLETED
                    }
                ) {
                    Download.STATE_DOWNLOADING
                } else {
                    Download.STATE_STOPPED
                }
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showEditNoteDialog) {
            ActionPromptDialog(
                title = stringResource(R.string.edit_playlist_cover),
                onDismiss = { showEditNoteDialog = false },
                onConfirm = {
                    showEditNoteDialog = false
                    pickLauncher.launch(
                        PickVisualMediaRequest(mediaType = ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onCancel = { showEditNoteDialog = false },
            ) {
                if (playlist.playlist.browseId != null) {
                    Text(
                        text = stringResource(R.string.edit_playlist_cover_note),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    text = stringResource(R.string.edit_playlist_cover_note_wait),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        }
        // Playlist Thumbnail(s) - Large centered with shadow
        Box(
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
        ) {
            when (playlist.thumbnails.size) {
                0 -> {
                    Surface(
                        modifier =
                            Modifier
                                .size(240.dp)
                                .shadow(
                                    elevation = 16.dp,
                                    shape = RoundedCornerShape(3.dp),
                                ),
                        shape = RoundedCornerShape(3.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.queue_music),
                                contentDescription = null,
                                modifier = Modifier.size(80.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                1 -> {
                    Surface(
                        modifier =
                            Modifier
                                .size(240.dp)
                                .shadow(
                                    elevation = 24.dp,
                                    shape = RoundedCornerShape(3.dp),
                                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                ),
                        shape = RoundedCornerShape(3.dp),
                    ) {
                        AsyncImage(
                            model = overrideThumbnail.value ?: playlist.thumbnails[0],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                else -> {
                    Surface(
                        modifier =
                            Modifier
                                .size(240.dp)
                                .shadow(
                                    elevation = 24.dp,
                                    shape = RoundedCornerShape(3.dp),
                                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                ),
                        shape = RoundedCornerShape(3.dp),
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            listOf(
                                Alignment.TopStart,
                                Alignment.TopEnd,
                                Alignment.BottomStart,
                                Alignment.BottomEnd,
                            ).fastForEachIndexed { index, alignment ->
                                AsyncImage(
                                    model = playlist.thumbnails.getOrNull(index),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier =
                                        Modifier
                                            .align(alignment)
                                            .size(120.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Playlist Name
        Text(
            text = playlist.playlist.name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 32.dp),
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Metadata - Song Count • Duration
        val songCount =
            if (playlist.songCount == 0 && playlist.playlist.remoteSongCount != null) {
                playlist.playlist.remoteSongCount
            } else {
                playlist.songCount
            }
        val nSongs = pluralStringResource(R.plurals.n_song, songCount, songCount)
        val durationText = if (playlistLength > 0) makeTimeString(playlistLength * 1000L) else null
        val metadataString = buildString {
            append(nSongs)
            if (durationText != null) {
                append(" ")
                append(durationText)
            }
        }
        Text(
            text = metadataString,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )

        val onlineAuthor = onlinePlaylist?.author
        if (onlineAuthor != null) {
            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier =
                    Modifier.combinedClickable(
                        onClick = {
                            if (onlineAuthor.id != null) {
                                navController.navigate("artist/${onlineAuthor.id}")
                            }
                        },
                    ),
            ) {
                if (onlinePlaylist.authorAvatarUrl != null) {
                    AsyncImage(
                        model = onlinePlaylist.authorAvatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .size(24.dp)
                                .clip(CircleShape),
                    )
                }
                Text(
                    text = onlineAuthor.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        val description = onlinePlaylist?.description
        if (!description.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))

            ExpandableText(
                text = description,
                modifier = Modifier.padding(horizontal = 32.dp),
                collapsedMaxLines = 3,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action Buttons Row
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Shuffle Button - Smaller secondary button
            Surface(
                onClick = {
                    playerConnection.playQueue(
                        ListQueue(
                            title = playlist.playlist.name,
                            items = songs.shuffled().map { it.song.toMediaItem() },
                        ),
                    )
                },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(48.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.shuffle),
                        contentDescription = stringResource(R.string.shuffle),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            // Play Button - Larger primary circular button
            Surface(
                onClick = {
                    playerConnection.playQueue(
                        ListQueue(
                            title = playlist.playlist.name,
                            items = songs.map { it.song.toMediaItem() },
                        ),
                    )
                },
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
                modifier = Modifier.size(72.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = stringResource(R.string.play),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }

            // Menu Button - Smaller secondary button
            Surface(
                onClick = {
                    menuState.show {
                        PlaylistMenu(
                            playlist = playlist,
                            coroutineScope = scope,
                            onDismiss = menuState::dismiss,
                            songList = songs.map { it.song },
                        )
                    }
                },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(48.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.more_vert),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

/** The small profile picture of whoever added a song to a collaborative playlist. */
@Composable
private fun ContributorAvatar(contributor: YouTubePlaylistEditor.Contributor) {
    AsyncImage(
        model = contributor.avatarUrl,
        contentDescription = contributor.name,
        modifier = Modifier
            .padding(horizontal = Spacing.XS)
            .size(Size.IconSmall)
            .clip(CircleShape),
    )
}

fun uriToByteArray(
    context: Context,
    uri: Uri,
): ByteArray? =
    try {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    } catch (_: SecurityException) {
        null
    }
