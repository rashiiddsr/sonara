/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.library

import android.content.Context
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadService
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.Song
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.playback.DownloadUtil
import com.metrolist.music.playback.ExoDownloadService
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.ui.component.EmptyState
import com.metrolist.music.ui.component.PrimaryButton
import com.metrolist.music.ui.component.SecondaryButton
import com.metrolist.music.ui.component.SectionHeader
import com.metrolist.music.ui.component.SongListItem
import com.metrolist.music.ui.screens.rememberItemActions
import com.metrolist.music.ui.theme.Size
import com.metrolist.music.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Download state as shown to the user (RULE-01). */
enum class DownloadStatus { QUEUED, DOWNLOADING, COMPLETED, FAILED }

fun Download.status(): DownloadStatus =
    when (state) {
        Download.STATE_COMPLETED -> DownloadStatus.COMPLETED
        Download.STATE_DOWNLOADING -> DownloadStatus.DOWNLOADING
        Download.STATE_FAILED -> DownloadStatus.FAILED
        // QUEUED, STOPPED (e.g. waiting for Wi-Fi), RESTARTING
        else -> DownloadStatus.QUEUED
    }

@Immutable
data class DownloadRow(
    val song: Song,
    val status: DownloadStatus,
    val percent: Float,
)

@Immutable
data class DownloadsUiState(
    val active: List<DownloadRow> = emptyList(),
    val completed: List<Song> = emptyList(),
    val totalBytes: Long = 0L,
    val loaded: Boolean = false,
)

@HiltViewModel
class DownloadsViewModel
@Inject
constructor(
    database: MusicDatabase,
    downloadUtil: DownloadUtil,
) : ViewModel() {
    val state: StateFlow<DownloadsUiState> =
        downloadUtil.downloads
            .flatMapLatest { downloads ->
                val ids = downloads.values.filter { it.state != Download.STATE_REMOVING }.map { it.request.id }
                combine(
                    flow { emit(if (ids.isEmpty()) emptyList() else database.getSongsByIds(ids)) },
                    database.downloadedSongsByCreateDateAsc(),
                ) { songs, downloaded ->
                    val byId = songs.associateBy { it.id }
                    val active =
                        downloads.values
                            .filter { it.state != Download.STATE_COMPLETED && it.state != Download.STATE_REMOVING }
                            .mapNotNull { d ->
                                byId[d.request.id]?.let { DownloadRow(it, d.status(), d.percentDownloaded.coerceIn(0f, 100f)) }
                            }.sortedBy { it.status != DownloadStatus.DOWNLOADING }
                    DownloadsUiState(
                        active = active,
                        completed = downloaded.asReversed(),
                        totalBytes =
                            downloads.values
                                .filter { it.state == Download.STATE_COMPLETED }
                                .sumOf { it.bytesDownloaded },
                        loaded = true,
                    )
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadsUiState())
}

fun removeDownload(
    context: Context,
    songId: String,
) {
    // Removes only the file and the download state; library and playlist entries stay (RULE-01).
    DownloadService.sendRemoveDownload(context, ExoDownloadService::class.java, songId, false)
}

/** Downloads page: progress, retry for failed items, delete, and total size. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DownloadsScreen(
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = hiltViewModel(),
    header: LazyListScope.() -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val actions = rememberItemActions()
    val downloadUtil = com.metrolist.music.LocalDownloadUtil.current

    LazyColumn(
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        modifier = modifier.fillMaxSize().testTag("downloads_list"),
    ) {
        header()
        if (state.loaded && state.active.isEmpty() && state.completed.isEmpty()) {
            item(key = "empty", contentType = "state") {
                EmptyState(
                    icon = R.drawable.download,
                    title = stringResource(R.string.er_downloads_empty_title),
                    message = stringResource(R.string.er_downloads_empty_message),
                )
            }
            return@LazyColumn
        }

        if (state.active.isNotEmpty()) {
            item(key = "active_header", contentType = "header") {
                SectionHeader(title = stringResource(R.string.er_downloading))
            }
            items(state.active, key = { "active_${it.song.id}" }, contentType = { "download_row" }) { row ->
                SongListItem(
                    song = row.song,
                    isSwipeable = false,
                    showDownloadIcon = false,
                    subtitleOverride =
                        when (row.status) {
                            DownloadStatus.QUEUED -> stringResource(R.string.er_download_queued)
                            DownloadStatus.DOWNLOADING -> stringResource(R.string.er_download_progress, row.percent.toInt())
                            DownloadStatus.FAILED -> stringResource(R.string.er_download_failed)
                            DownloadStatus.COMPLETED -> null
                        },
                    trailingContent = {
                        when (row.status) {
                            DownloadStatus.DOWNLOADING ->
                                CircularProgressIndicator(
                                    progress = { row.percent / 100f },
                                    modifier = Modifier.padding(Spacing.M).size(Size.IconSmall),
                                    strokeWidth = Spacing.XXS,
                                )
                            DownloadStatus.FAILED ->
                                IconButton(onClick = { downloadUtil.download(row.song) }) {
                                    Icon(painterResource(R.drawable.refresh), stringResource(R.string.retry))
                                }
                            else -> {}
                        }
                        IconButton(onClick = { removeDownload(context, row.song.id) }) {
                            Icon(painterResource(R.drawable.close), stringResource(R.string.er_cancel_download))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (state.completed.isNotEmpty()) {
            item(key = "completed_header", contentType = "header") {
                SectionHeader(
                    title = stringResource(R.string.er_downloaded),
                    subtitle =
                        stringResource(
                            R.string.er_downloads_summary,
                            state.completed.size,
                            Formatter.formatShortFileSize(context, state.totalBytes),
                        ),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = Spacing.Gutter, vertical = Spacing.S),
                ) {
                    PrimaryButton(
                        text = stringResource(R.string.er_play),
                        icon = R.drawable.play,
                        onClick = {
                            playerConnection?.playQueue(
                                ListQueue(
                                    title = context.getString(R.string.er_downloaded),
                                    items = state.completed.map { it.toMediaItem() },
                                ),
                            )
                        },
                    )
                    SecondaryButton(
                        text = stringResource(R.string.er_shuffle),
                        icon = R.drawable.shuffle,
                        onClick = {
                            playerConnection?.playQueue(
                                ListQueue(
                                    title = context.getString(R.string.er_downloaded),
                                    items = state.completed.shuffled().map { it.toMediaItem() },
                                ),
                            )
                        },
                    )
                    Spacer(Modifier.width(Spacing.S))
                }
            }
            itemsIndexed(state.completed, key = { _, song -> "done_${song.id}" }, contentType = { _, _ -> "song" }) { index, song ->
                SongListItem(
                    song = song,
                    isSwipeable = false,
                    trailingContent = {
                        IconButton(onClick = { removeDownload(context, song.id) }) {
                            Icon(painterResource(R.drawable.delete), stringResource(R.string.er_delete_download))
                        }
                    },
                    // Play from the downloaded list so the queue works without network (RULE-02/03).
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                playerConnection?.playQueue(
                                    ListQueue(
                                        title = context.getString(R.string.er_downloaded),
                                        items = state.completed.map { it.toMediaItem() },
                                        startIndex = index,
                                    ),
                                )
                            },
                            onLongClick = { actions.menu(song) },
                        ),
                )
            }
        }
    }
}
