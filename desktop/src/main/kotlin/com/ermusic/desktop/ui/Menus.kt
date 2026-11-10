/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ermusic.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ermusic.desktop.data.Downloads
import com.ermusic.desktop.data.SpeedDial
import com.ermusic.desktop.playback.Track
import com.ermusic.desktop.playback.toTrack
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.EpisodeItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.PodcastItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem

@Composable
fun AppMenuItem(label: String, symbol: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = { Icon(icon(symbol), null, modifier = Modifier.size(Size.IconSmall)) },
        onClick = onClick,
        modifier = Modifier.handCursor(),
    )
}

/** What a song's row in the user's own playlist can do besides what any song can. */
data class PlaylistRow(val playlistId: String, val onMoveUp: (() -> Unit)?, val onMoveDown: (() -> Unit)?)

@Composable
private fun ColumnScope.TrackItems(track: Track, app: AppState, row: PlaylistRow?, extra: (@Composable ColumnScope.(() -> Unit) -> Unit)?, onDismiss: () -> Unit) {
    fun act(block: () -> Unit) {
        block()
        onDismiss()
    }
    val player = app.player
    AppMenuItem("Play next", "playlist_play") { act { player.playNext(listOf(track)) } }
    AppMenuItem("Add to queue", "queue_music") { act { player.addToQueue(listOf(track)) } }
    AppMenuItem("Radio", "radio") { act { app.openRadio(track) } }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    AppMenuItem("Add to playlist", "playlist_add") { act { app.addToPlaylistFor = listOf(track) } }
    AppMenuItem(if (track.id in app.likedIds) "Remove from Liked songs" else "Add to Liked songs", if (track.id in app.likedIds) "favorite" else "favorite_border") { act { app.toggleLike(track) } }
    AppMenuItem(if (SpeedDial.has(track.id)) "Unpin from Home" else "Pin to Home", "check") { act { SpeedDial.toggle(track) } }
    if (Downloads.has(track.id)) {
        AppMenuItem("Remove download", "delete") { act { Downloads.remove(track.id) } }
    } else {
        AppMenuItem("Download", "download") { act { app.download(listOf(track)) } }
    }
    if (row != null) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        row.onMoveUp?.let { move -> AppMenuItem("Move up", "arrow_upward") { act(move) } }
        row.onMoveDown?.let { move -> AppMenuItem("Move down", "arrow_downward") { act(move) } }
        if (track.setVideoId != null) AppMenuItem("Remove from this playlist", "delete") { act { app.removeFromPlaylist(row.playlistId, track) } }
    }
    extra?.invoke(this, onDismiss)
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    track.artistId?.let { id -> AppMenuItem("Go to artist", "person") { act { app.go(Route.Artist(id)) } } }
    track.albumId?.let { id -> AppMenuItem("Go to album", "album") { act { app.go(Route.Album(id)) } } }
    AppMenuItem("Copy link", "link") { act { app.copyLink(track.shareLink) } }
}

/** A song's menu where there is only a [Track]: the queue, the player, the side panel. */
@Composable
fun TrackMenu(track: Track, app: AppState, at: IntOffset?, extra: (@Composable ColumnScope.(() -> Unit) -> Unit)? = null, onDismiss: () -> Unit) {
    if (at == null) return
    MenuAnchor(at) {
        DropdownMenu(expanded = true, onDismissRequest = onDismiss, shape = ErShape.Medium, containerColor = MaterialTheme.colorScheme.surfaceContainerHighest) {
            TrackItems(track, app, null, extra, onDismiss)
        }
    }
}

/** Everything that can be done with an item, on a right click or its ⋮ button. */
@Composable
fun ItemMenu(item: YTItem, app: AppState, at: IntOffset?, row: PlaylistRow? = null, extra: (@Composable ColumnScope.(() -> Unit) -> Unit)? = null, onDismiss: () -> Unit) {
    if (at == null) return
    MenuAnchor(at) {
        DropdownMenu(expanded = true, onDismissRequest = onDismiss, shape = ErShape.Medium, containerColor = MaterialTheme.colorScheme.surfaceContainerHighest) {
            fun act(block: () -> Unit) {
                block()
                onDismiss()
            }
            val song = (item as? SongItem) ?: (item as? EpisodeItem)?.asSongItem()
            if (song != null) {
                TrackItems(song.toTrack(), app, row, extra, onDismiss)
                return@DropdownMenu
            }
            if (item !is ArtistItem) {
                AppMenuItem("Play", "play") { act { app.playItem(item) } }
                AppMenuItem("Shuffle", "shuffle") { act { app.playItem(item, shuffle = true) } }
                AppMenuItem("Play next", "playlist_play") { act { app.withTracks(item, app.player::playNext) } }
                AppMenuItem("Add to queue", "queue_music") { act { app.withTracks(item, app.player::addToQueue) } }
                (item as? PlaylistItem)?.radioEndpoint?.let { endpoint -> AppMenuItem("Radio", "radio") { act { app.openRadio(item.title, item.thumbnail, endpoint) } } }
                AppMenuItem("Add to playlist", "playlist_add") { act { app.withTracks(item) { app.addToPlaylistFor = it } } }
                AppMenuItem("Download", "download") { act { app.withTracks(item, app::download) } }
            } else {
                item.radioEndpoint?.let { endpoint -> AppMenuItem("Radio", "radio") { act { app.openRadio(item.title, item.thumbnail, endpoint) } } }
                AppMenuItem("Shuffle", "shuffle") { act { app.playItem(item, shuffle = true) } }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (item is PlaylistItem && item.isEditable) {
                AppMenuItem("Add songs", "add") { act { app.addingSongsTo = item } }
                AppMenuItem("Edit details", "edit") { act { app.editing = item } }
                AppMenuItem("Collaborate", "person_add") { act { app.collaborating = item } }
                AppMenuItem("Delete playlist", "delete") { act { app.deletePlaylist(item) } }
            }
            if (item is AlbumItem) SaveMenuItem(item.playlistId, app, onDismiss)
            if (item is PlaylistItem && !item.isEditable) SaveMenuItem(item.id, app, onDismiss)
            if (item is PodcastItem) SaveMenuItem(item.id, app, onDismiss)
            AppMenuItem("Copy link", "link") { act { app.copyLink(item.shareLink) } }
        }
    }
}

@Composable
private fun SaveMenuItem(playlistId: String, app: AppState, onDismiss: () -> Unit) {
    val saved = app.isSaved(playlistId)
    AppMenuItem(if (saved) "Remove from Library" else "Save to Library", if (saved) "library_add_check" else "library_add") {
        app.toggleSaved(playlistId)
        onDismiss()
    }
}

@Composable
private fun TextEntryDialog(title: String, action: String, initial: String, app: AppState, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth().onFocusChanged { app.typing = it.isFocused },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()); onDismiss() }, enabled = text.isNotBlank(), modifier = Modifier.handCursor()) { Text(action) }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.handCursor()) { Text("Cancel") } },
    )
}

/** The dialogs any screen can ask for. */
@Composable
fun AppDialogs(app: AppState) {
    app.addToPlaylistFor?.let { tracks ->
        AlertDialog(
            onDismissRequest = { app.addToPlaylistFor = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Add to playlist", style = MaterialTheme.typography.titleLarge) },
            text = {
                LazyColumn(Modifier.heightIn(max = DIALOG_LIST_HEIGHT).width(DIALOG_WIDTH)) {
                    item(key = "new", contentType = "new") {
                        PlaylistChoice("New playlist", null, "add") {
                            app.addToPlaylistFor = null
                            app.newPlaylistFor = tracks
                        }
                    }
                    items(app.playlists.filter { it.isEditable }, key = { it.id }, contentType = { "playlist" }) { playlist ->
                        PlaylistChoice(playlist.title, playlist.thumbnail, null) {
                            app.addToPlaylistFor = null
                            app.addToPlaylist(playlist, tracks)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { app.addToPlaylistFor = null }, modifier = Modifier.handCursor()) { Text("Cancel") } },
        )
    }
    app.newPlaylistFor?.let { tracks ->
        TextEntryDialog("New playlist", "Create", "", app, { app.newPlaylistFor = null }) { app.createPlaylist(it, tracks.ifEmpty { null }) }
    }
    app.editing?.let { EditPlaylistDialog(it, app) }
    app.addingSongsTo?.let { AddSongsDialog(it, app) }
    app.collaborating?.let { CollaborateDialog(it, app) }
    if (app.importing) ImportPlaylistDialog(app)
}

@Composable
private fun PlaylistChoice(title: String, art: String?, symbol: String?, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(ErShape.Small).clickable(onClick = onClick).handCursor().padding(Spacing.S),
    ) {
        if (symbol != null) Icon(icon(symbol), null, modifier = Modifier.size(Size.Icon + Spacing.M)) else Art(art, Modifier.size(Size.Icon + Spacing.M), pixels = 96)
        Spacer(Modifier.width(Spacing.M))
        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private val DIALOG_WIDTH = 420.dp
private val DIALOG_LIST_HEIGHT = 380.dp
