/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ermusic.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metrolist.music.utils.PlaylistDetails
import com.metrolist.music.utils.PlaylistPrivacy
import com.metrolist.music.utils.YouTubePlaylistEditor
import com.metrolist.music.utils.YouTubePlaylistEditor.CollaborationChange
import com.metrolist.music.utils.YouTubePlaylistEditor.ImportError
import com.metrolist.music.utils.YouTubeWebSession
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

private val DialogWidth = 460.dp
private val ResultsHeight = 360.dp
private const val COVER_SIDE = 1000
private const val SEARCH_DEBOUNCE_MS = 350L

/** The picked image, cropped to a centred square, as JPEG bytes. */
private fun coverBytes(file: File): ByteArray? =
    runCatching {
        val source = ImageIO.read(file) ?: return null
        val side = minOf(source.width, source.height)
        val square = BufferedImage(COVER_SIDE, COVER_SIDE, BufferedImage.TYPE_INT_RGB)
        val canvas = square.createGraphics()
        canvas.drawImage(source, 0, 0, COVER_SIDE, COVER_SIDE, (source.width - side) / 2, (source.height - side) / 2, (source.width + side) / 2, (source.height + side) / 2, null)
        canvas.dispose()
        ByteArrayOutputStream().also { ImageIO.write(square, "jpeg", it) }.toByteArray()
    }.getOrNull()

private fun pickImage(): File? {
    val dialog = FileDialog(null as Frame?, "Choose a cover image", FileDialog.LOAD)
    dialog.file = "*.jpg;*.jpeg;*.png"
    dialog.isVisible = true
    return dialog.file?.let { File(dialog.directory, it) }
}

/**
 * Name, description, visibility and cover. Nothing changes here until YouTube has accepted it: a
 * refused save says so and keeps the dialog open.
 */
@Composable
fun EditPlaylistDialog(playlist: PlaylistItem, app: AppState) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(playlist.title) }
    var description by remember { mutableStateOf("") }
    var privacy by remember { mutableStateOf(PlaylistPrivacy.PRIVATE) }
    var details by remember { mutableStateOf<PlaylistDetails?>(null) }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(playlist.id) {
        YouTubePlaylistEditor.details(playlist.id)?.let {
            details = it
            description = it.description
            privacy = it.privacy
        }
    }

    fun coverAction(failure: String, call: suspend () -> Boolean) {
        busy = true
        problem = null
        scope.launch {
            val done = withContext(Dispatchers.IO) { call() }
            busy = false
            if (done) {
                app.playlistChanged()
                details = withContext(Dispatchers.IO) { YouTubePlaylistEditor.details(playlist.id) } ?: details
                app.showToast("Cover updated")
            } else {
                problem = failure
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) app.editing = null },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text("Edit playlist", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.width(DialogWidth), verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Art(playlist.thumbnail, Modifier.size(EDIT_ART), ErShape.Medium, pixels = 300)
                    Spacer(Modifier.width(Spacing.L))
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        OutlinedButton(enabled = !busy, modifier = Modifier.handCursor(), onClick = {
                            val bytes = pickImage()?.let(::coverBytes)
                            if (bytes != null) coverAction("YouTube didn't accept that image.") { YouTubeWebSession.uploadPlaylistCover(playlist.id, bytes) }
                        }) {
                            Icon(icon("insert_photo"), null, modifier = Modifier.size(Size.IconSmall))
                            Spacer(Modifier.width(Spacing.XS))
                            Text("Change cover")
                        }
                        if (details?.customCover == true) {
                            TextButton(enabled = !busy, modifier = Modifier.handCursor(), onClick = { coverAction("Couldn't remove the cover.") { YouTubePlaylistEditor.removeCover(playlist.id) } }) { Text("Remove cover") }
                        }
                    }
                }
                OutlinedTextField(title, { title = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth().onFocusChanged { app.typing = it.isFocused })
                OutlinedTextField(description, { description = it }, label = { Text("Description") }, minLines = 3, maxLines = 5, modifier = Modifier.fillMaxWidth().onFocusChanged { app.typing = it.isFocused })
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    PlaylistPrivacy.entries.forEach { option ->
                        Text(
                            option.name.lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelLarge,
                            color = if (option == privacy) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier =
                                Modifier
                                    .clip(ErShape.Pill)
                                    .background(if (option == privacy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest)
                                    .clickable { privacy = option }
                                    .handCursor()
                                    .padding(horizontal = Spacing.L, vertical = Spacing.S),
                        )
                    }
                }
                problem?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && title.isNotBlank() && details != null, modifier = Modifier.handCursor(), onClick = {
                busy = true
                problem = null
                scope.launch {
                    val saved = YouTubePlaylistEditor.edit(playlist.id, title.trim(), description, privacy)
                    busy = false
                    if (saved) {
                        app.editing = null
                        app.playlistChanged()
                        app.showToast("Saved")
                    } else {
                        problem = "YouTube didn't accept the changes."
                    }
                }
            }) { Text(if (busy) "Saving…" else "Save") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = { app.editing = null }, modifier = Modifier.handCursor()) { Text("Cancel") } },
    )
}

private val EDIT_ART = 112.dp

/** Find songs and add them one by one; a song is marked added once YouTube has confirmed it. */
@Composable
fun AddSongsDialog(playlist: PlaylistItem, app: AppState) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SongItem>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var added by remember { mutableStateOf<Set<String>>(emptySet()) }
    var adding by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(query) {
        val text = query.trim()
        if (text.isEmpty()) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(SEARCH_DEBOUNCE_MS)
        searching = true
        results = YouTube.search(text, YouTube.SearchFilter.FILTER_SONG).getOrNull()?.items.orEmpty().filterIsInstance<SongItem>()
        searching = false
    }
    AlertDialog(
        onDismissRequest = {
            if (added.isNotEmpty()) app.playlistChanged()
            app.addingSongsTo = null
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text("Add to ${playlist.title}", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.width(DialogWidth)) {
                OutlinedTextField(query, { query = it }, label = { Text("Search songs") }, singleLine = true, modifier = Modifier.fillMaxWidth().onFocusChanged { app.typing = it.isFocused })
                Spacer(Modifier.height(Spacing.S))
                Box(Modifier.height(ResultsHeight).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (searching && results.isEmpty()) {
                        CircularProgressIndicator()
                    } else {
                        LazyColumn(Modifier.fillMaxWidth().height(ResultsHeight)) {
                            items(results, key = { it.id }, contentType = { "song" }) { song ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(Size.ListRow)) {
                                    Art(song.thumbnail, Modifier.size(Size.ListThumb), pixels = 120)
                                    Spacer(Modifier.width(Spacing.M))
                                    Column(Modifier.weight(1f)) {
                                        Text(song.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(song.subtitle(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    when (song.id) {
                                        in added -> Icon(icon("check"), "Added", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(Spacing.M))
                                        in adding -> CircularProgressIndicator(strokeWidth = Spacing.XXS, modifier = Modifier.padding(Spacing.M).size(Size.IconSmall))
                                        else ->
                                            SymbolButton("add", "Add") {
                                                adding = adding + song.id
                                                scope.launch(Dispatchers.IO) {
                                                    if (YouTubePlaylistEditor.addSong(playlist.id, song.id)) added = added + song.id else app.showToast("Couldn't add ${song.title}")
                                                    adding = adding - song.id
                                                }
                                            }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(modifier = Modifier.handCursor(), onClick = {
                if (added.isNotEmpty()) app.playlistChanged()
                app.addingSongsTo = null
            }) { Text("Done") }
        },
    )
}

/**
 * YouTube Music's Collaborate panel: on or off, whether new people may join, and the invite link.
 * Each switch moves once YouTube has accepted it; turning collaboration off asks first, since
 * collaborators lose access.
 */
@Composable
fun CollaborateDialog(playlist: PlaylistItem, app: AppState) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<YouTubePlaylistEditor.Collaboration?>(null) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var confirmOff by remember { mutableStateOf(false) }
    LaunchedEffect(playlist.id) {
        state = withContext(Dispatchers.IO) { YouTubePlaylistEditor.collaboration(playlist.id, fresh = true) }
        loading = false
    }

    fun change(kind: CollaborationChange) {
        val current = state ?: return
        busy = true
        scope.launch(Dispatchers.IO) {
            val accepted = YouTubePlaylistEditor.changeCollaboration(current, kind)
            if (accepted == null) {
                app.showToast("YouTube didn't accept that")
            } else {
                // The answer doesn't always carry the new link; the panel does.
                state = if (accepted.enabled && accepted.allowNew && accepted.inviteLink == null) YouTubePlaylistEditor.collaboration(playlist.id, fresh = true) ?: accepted else accepted
            }
            busy = false
        }
    }

    AlertDialog(
        onDismissRequest = { app.collaborating = null },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text("Collaborate", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.width(DialogWidth), verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                val current = state
                when {
                    loading -> Box(Modifier.fillMaxWidth().height(COLLAB_LOADING_HEIGHT), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    current == null -> Text("Couldn't read this playlist's collaboration.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    else -> {
                        CollabSwitch("Collaborate", "Collaborators can add songs", current.enabled, !busy) { on ->
                            if (on) change(CollaborationChange.ENABLE) else confirmOff = true
                        }
                        if (current.enabled) {
                            CollabSwitch("Allow new collaborators", "Turn off to stop new people from adding songs", current.allowNew, !busy) { on ->
                                change(if (on) CollaborationChange.ALLOW_NEW else CollaborationChange.BLOCK_NEW)
                            }
                            current.inviteLink?.let { link ->
                                OutlinedButton(onClick = { app.copyLink(link) }, modifier = Modifier.fillMaxWidth().handCursor()) {
                                    Icon(icon("link"), null, modifier = Modifier.size(Size.IconSmall))
                                    Spacer(Modifier.width(Spacing.S))
                                    Text("Copy invite link")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { app.collaborating = null }, modifier = Modifier.handCursor()) { Text("Close") } },
    )
    if (confirmOff) {
        AlertDialog(
            onDismissRequest = { confirmOff = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            title = { Text("Turn off collaboration?", style = MaterialTheme.typography.titleLarge) },
            text = { Text("Collaborators will be removed from the playlist. Songs they added will stay.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(modifier = Modifier.handCursor(), onClick = {
                    confirmOff = false
                    change(CollaborationChange.DISABLE)
                }) { Text("Turn off") }
            },
            dismissButton = { TextButton(onClick = { confirmOff = false }, modifier = Modifier.handCursor()) { Text("Cancel") } },
        )
    }
}

private val COLLAB_LOADING_HEIGHT = 96.dp

@Composable
private fun CollabSwitch(title: String, description: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f).padding(end = Spacing.M)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange, enabled = enabled, colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary), modifier = Modifier.handCursor())
    }
}

private fun importProblem(error: ImportError): String =
    when (error) {
        ImportError.NOT_A_PLAYLIST -> "This isn't a YouTube Music playlist link."
        ImportError.MIX -> "Mixes are made for each account and can't be imported."
        ImportError.NOT_FOUND -> "Playlist not found, or it is private."
        ImportError.EMPTY -> "This playlist has no songs."
        ImportError.TOO_BIG -> "More than 5,000 songs, over YouTube's limit for one playlist."
        ImportError.OFFLINE -> "Couldn't connect, try again."
    }

/** The token of a collaboration invite link, when [link] is one. */
private fun inviteToken(link: String): String? = Regex("[?&]jct=([^&]+)").find(link)?.groupValues?.get(1)

/** Copies a playlist from a link into the account, or joins one from an invite link. */
@Composable
fun ImportPlaylistDialog(app: AppState) {
    val scope = rememberCoroutineScope()
    var link by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var source by remember { mutableStateOf<YouTubePlaylistEditor.ImportSource?>(null) }
    var invite by remember { mutableStateOf<YouTubePlaylistEditor.CollaborationInvite?>(null) }

    fun check() {
        busy = true
        problem = null
        source = null
        invite = null
        val text = link.trim()
        scope.launch(Dispatchers.IO) {
            val token = inviteToken(text)
            val id = YouTubePlaylistEditor.playlistIdFromLink(text)
            if (token != null && id != null) {
                invite = YouTubePlaylistEditor.invite(id, token)
            } else {
                val result = YouTubePlaylistEditor.checkImport(text)
                source = result.source.takeIf { result.error == null }
                problem = result.error?.let(::importProblem)
            }
            busy = false
        }
    }

    fun import(found: YouTubePlaylistEditor.ImportSource) {
        busy = true
        problem = null
        scope.launch(Dispatchers.IO) {
            val id = YouTubePlaylistEditor.create(found.title, "", PlaylistPrivacy.PRIVATE, found.playlistId)
            busy = false
            if (id == null) {
                problem = "YouTube didn't accept the import."
            } else {
                app.importing = false
                app.playlistChanged()
                app.showToast("Imported ${found.title}")
                app.go(Route.Playlist(id))
            }
        }
    }

    fun join(target: YouTubePlaylistEditor.CollaborationInvite) {
        busy = true
        problem = null
        scope.launch(Dispatchers.IO) {
            val result = YouTubePlaylistEditor.joinCollaboration(target)
            busy = false
            if (result == YouTubePlaylistEditor.JoinResult.FAILED) {
                problem = "Couldn't join the playlist."
            } else {
                app.importing = false
                app.playlistChanged()
                app.showToast("You joined the playlist")
                app.go(Route.Playlist(target.playlistId))
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) app.importing = false },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text("Import or join a playlist", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.width(DialogWidth), verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                OutlinedTextField(link, {
                    link = it
                    source = null
                    invite = null
                    problem = null
                }, label = { Text("Playlist or invite link") }, singleLine = true, modifier = Modifier.fillMaxWidth().onFocusChanged { app.typing = it.isFocused })
                source?.let { found ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Art(found.thumbnail, Modifier.size(Size.ListRow), ErShape.Small, pixels = 200)
                        Spacer(Modifier.width(Spacing.M))
                        Column {
                            Text(found.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val details = listOfNotNull(found.owner?.let { "By $it" }, found.trackCount?.let { "$it songs" }).joinToString(" · ")
                            if (details.isNotEmpty()) Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                invite?.let { target ->
                    Column {
                        Text(target.title ?: "Collaborate on this playlist", style = MaterialTheme.typography.titleSmall)
                        Text(target.message ?: "You are invited to add songs to this playlist.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                problem?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            val found = source
            val target = invite
            when {
                found != null -> TextButton(enabled = !busy, modifier = Modifier.handCursor(), onClick = { import(found) }) { Text(if (busy) "Importing…" else "Import") }
                target != null -> TextButton(enabled = !busy, modifier = Modifier.handCursor(), onClick = { join(target) }) { Text(if (busy) "Joining…" else "Join") }
                else -> TextButton(enabled = !busy && link.isNotBlank(), modifier = Modifier.handCursor(), onClick = ::check) { Text(if (busy) "Checking…" else "Check link") }
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = { app.importing = false }, modifier = Modifier.handCursor()) { Text("Cancel") } },
    )
}
