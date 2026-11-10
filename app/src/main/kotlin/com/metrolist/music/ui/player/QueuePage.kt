/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.player

import com.metrolist.music.utils.appScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import java.time.LocalDateTime
import com.metrolist.music.ui.component.ErToast
import com.metrolist.music.db.entities.PlaylistEntity
import com.metrolist.music.LocalSyncUtils
import com.metrolist.music.LocalDatabase
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.extensions.metadata
import com.metrolist.music.extensions.move
import com.metrolist.music.ui.component.BottomSheetState
import com.metrolist.music.ui.component.EmptyState
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.MediaMetadataListItem
import com.metrolist.music.ui.menu.QueueMenu
import com.metrolist.music.ui.theme.ErShape
import com.metrolist.music.ui.theme.Spacing
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private const val CONTENT_TYPE_QUEUE_ITEM = "queue_item"

/**
 * Queue page inside the player: tap to play, drag the handle to reorder, swipe to remove.
 */
@Composable
fun QueuePage(
    playerBottomSheetState: BottomSheetState,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val queueWindows by playerConnection.queueWindows.collectAsStateWithLifecycle()
    val currentWindowIndex by playerConnection.currentWindowIndex.collectAsStateWithLifecycle()
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val queueTitle by playerConnection.queueTitle.collectAsStateWithLifecycle()
    val database = LocalDatabase.current
    val syncUtils = LocalSyncUtils.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val savedText = stringResource(R.string.er_saved_as_playlist)
    val saveQueue: () -> Unit = {
        val songs = queueWindows.mapNotNull { it.mediaItem.metadata }
        if (songs.isNotEmpty()) {
            appScope.launch {
                val playlistId = syncUtils.createPlaylist(
                    PlaylistEntity(name = queueTitle ?: context.getString(R.string.er_up_next), bookmarkedAt = LocalDateTime.now(), isEditable = true),
                    syncWithYouTube = true,
                )
                if (playlistId == null) {
                    withContext(Dispatchers.Main) { ErToast.show(context, R.string.er_playlist_create_failed) }
                } else {
                    database.withTransaction { songs.forEach { insert(it) } }
                    val playlist = database.playlist(playlistId).first() ?: return@launch
                    database.addSongsToPlaylist(playlist, songs.map { it.id to null })
                    playlist.playlist.browseId?.let { syncUtils.scheduleAddToPlaylist(it, playlistId, songs.map { song -> song.id }) }
                    withContext(Dispatchers.Main) { ErToast.show(context, savedText) }
                }
            }
        }
    }

    val items = remember { mutableStateListOf<Timeline.Window>() }
    LaunchedEffect(queueWindows) {
        items.clear()
        items.addAll(queueWindows)
    }
    val currentUid = queueWindows.getOrNull(currentWindowIndex)?.uid

    val listState = rememberLazyListState()
    var dragRange by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val reorderState =
        rememberReorderableLazyListState(lazyListState = listState) { from, to ->
            dragRange = (dragRange?.first ?: from.index) to to.index
            items.move(from.index, to.index)
        }

    LaunchedEffect(reorderState.isAnyItemDragging) {
        if (reorderState.isAnyItemDragging) return@LaunchedEffect
        val (from, to) = dragRange ?: return@LaunchedEffect
        dragRange = null
        if (from == to) return@LaunchedEffect
        val player = playerConnection.player
        if (!player.shuffleModeEnabled) {
            player.moveMediaItem(from, to)
        } else {
            player.setShuffleOrder(
                DefaultShuffleOrder(
                    queueWindows.map { it.firstPeriodIndex }.toMutableList().move(from, to).toIntArray(),
                    System.currentTimeMillis(),
                ),
            )
        }
    }

    LaunchedEffect(currentWindowIndex) {
        if (currentWindowIndex in queueWindows.indices) listState.scrollToItem(currentWindowIndex)
    }

    if (items.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            EmptyState(icon = R.drawable.queue_music, title = stringResource(R.string.er_queue_empty))
        }
        return
    }

    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f).padding(vertical = Spacing.S)) {
                Text(stringResource(R.string.er_up_next), style = MaterialTheme.typography.titleMedium)
                queueTitle?.let {
                    Text(
                        stringResource(R.string.er_playing_from, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            // Like YouTube Music's "Save": the current mix becomes a playlist on the account.
            TextButton(onClick = saveQueue) { Text(stringResource(R.string.er_save)) }
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().clip(ErShape.Medium)) {
            itemsIndexed(
                items = items,
                key = { _, window -> window.uid.hashCode() },
                contentType = { _, _ -> CONTENT_TYPE_QUEUE_ITEM },
            ) { _, window ->
                ReorderableItem(reorderState, key = window.uid.hashCode()) {
                    val current by rememberUpdatedState(window)
                    val dismissState = rememberSwipeToDismissBoxState()
                    LaunchedEffect(dismissState.currentValue) {
                        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
                            playerConnection.player.removeMediaItem(current.firstPeriodIndex)
                        }
                    }
                    val isActive = window.uid == currentUid
                    val metadata = window.mediaItem.metadata ?: return@ReorderableItem
                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = {
                            // Drawn behind a transparent row, so only paint it during a swipe;
                            // otherwise every row looks tinted red.
                            if (dismissState.dismissDirection != SwipeToDismissBoxValue.Settled) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.errorContainer),
                                )
                            }
                        },
                    ) {
                        MediaMetadataListItem(
                            mediaMetadata = metadata,
                            isActive = isActive,
                            isPlaying = isActive && isPlaying,
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            menuState.show {
                                                QueueMenu(
                                                    mediaMetadata = metadata,
                                                    playerBottomSheetState = playerBottomSheetState,
                                                    onShowDetailsDialog = {},
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        },
                                    ) {
                                        Icon(painterResource(R.drawable.more_vert), contentDescription = stringResource(R.string.er_more))
                                    }
                                    IconButton(onClick = {}, modifier = Modifier.draggableHandle()) {
                                        Icon(painterResource(R.drawable.drag_handle), contentDescription = stringResource(R.string.er_reorder))
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Transparent)
                                .clickable {
                                    if (isActive) {
                                        playerConnection.togglePlayPause()
                                    } else {
                                        playerConnection.player.seekToDefaultPosition(current.firstPeriodIndex)
                                        playerConnection.player.playWhenReady = true
                                    }
                                },
                        )
                    }
                }
            }
        }
    }
}
