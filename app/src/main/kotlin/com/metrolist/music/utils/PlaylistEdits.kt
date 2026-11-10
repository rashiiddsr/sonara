/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import com.metrolist.music.ui.component.RemovedCover
import android.content.Context
import android.net.Uri
import com.metrolist.innertube.YouTube
import com.metrolist.music.R
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.PlaylistEntity
import com.metrolist.music.ui.component.ErToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.time.LocalDateTime

/**
 * Saves title, description, visibility and an optional cover change on YouTube first; SONARA
 * only shows what YouTube accepted, and the toast says so. Nothing is changed locally ahead of
 * the account: a refused cover stays the old one. Returns true when everything was accepted
 * (the editor then closes); on false it stays open with the user's input.
 *
 * A playlist that exists only in SONARA (no browseId) is saved locally.
 */
suspend fun savePlaylistEdits(
    context: Context,
    database: MusicDatabase,
    playlist: PlaylistEntity,
    title: String,
    description: String,
    privacy: PlaylistPrivacy,
    newCover: Uri?,
): Boolean {
    val removeCover = newCover == RemovedCover
    val result = withContext(Dispatchers.IO) {
        val browseId = playlist.browseId
        if (browseId == null) {
            val localCover = newCover?.takeUnless { removeCover }?.let { copyCover(context, playlist.id, it) }
            val thumbnail = if (removeCover) null else localCover ?: playlist.thumbnailUrl
            database.query { update(playlist.copy(name = title, thumbnailUrl = thumbnail, lastUpdateTime = LocalDateTime.now())) }
            return@withContext SaveResult.SAVED
        }
        // Only what changed is sent: a new cover alone does not resend the title and description.
        val known = YouTubePlaylistEditor.cachedDetails(browseId)
        val sameDetails = known != null && known.description == description && known.privacy == privacy
        if (title != playlist.name || !sameDetails) {
            val edited = YouTubePlaylistEditor.edit(browseId, title, description, privacy)
            // InnerTubeX's rename only covers the title; enough when nothing else changed.
            val renamed = !edited && sameDetails && YouTube.renamePlaylist(browseId, title).isSuccess
            if (!edited && !renamed) return@withContext SaveResult.FAILED
        }
        var thumbnail = playlist.thumbnailUrl
        val coverOk = when {
            removeCover -> YouTubePlaylistEditor.removeCover(browseId).also { if (it) thumbnail = null }
            newCover != null -> {
                val bytes = runCatching { context.contentResolver.openInputStream(newCover)?.use { it.readBytes() } }.getOrNull()
                // Own upload first (live YouTube Music client, account session); InnerTubeX as backup.
                val uploaded = bytes != null && (
                    YouTubeWebSession.uploadPlaylistCover(browseId, bytes) ||
                        YouTube.uploadCustomThumbnailLink(browseId, bytes).isSuccess
                    )
                if (uploaded) {
                    // The same image YouTube now has; kept so it shows before the next sync.
                    thumbnail = copyCover(context, playlist.id, newCover) ?: thumbnail
                } else {
                    Timber.tag("SonaraWeb").w("cover not accepted by YouTube (${bytes?.size} bytes)")
                }
                uploaded
            }
            else -> true
        }
        database.query { update(playlist.copy(name = title, thumbnailUrl = thumbnail, lastUpdateTime = LocalDateTime.now())) }
        val customCover = when {
            removeCover && coverOk -> false
            newCover != null && coverOk -> true
            else -> known?.customCover ?: false
        }
        YouTubePlaylistEditor.rememberDetails(browseId, PlaylistDetails(description, privacy, customCover))
        if (coverOk) SaveResult.SAVED else SaveResult.COVER_FAILED
    }
    // The playlist's page, wherever the edit was opened from (its header, its menu, the library),
    // reloads to show what YouTube now has.
    if (result != SaveResult.FAILED) PlaylistEditEvents.saved.tryEmit(SavedPlaylistEdit(playlist.id, description))
    withContext(Dispatchers.Main) {
        ErToast.show(
            context,
            when (result) {
                SaveResult.SAVED -> R.string.er_playlist_saved
                SaveResult.COVER_FAILED -> R.string.er_playlist_cover_failed
                SaveResult.FAILED -> R.string.er_playlist_save_failed
            },
        )
    }
    return result == SaveResult.SAVED
}

private enum class SaveResult { SAVED, COVER_FAILED, FAILED }

/**
 * Deletes a playlist on YouTube first, then in SONARA, like every other account change: a
 * playlist YouTube refused to delete stays (it would only come back with the next sync), and
 * the toast says which. One that exists only in SONARA is deleted here at once.
 */
fun deletePlaylistEverywhere(
    context: Context,
    database: MusicDatabase,
    playlist: PlaylistEntity,
) {
    appScope.launch {
        val deleted = playlist.browseId?.let { YouTube.deletePlaylist(it).isSuccess } ?: true
        Timber.tag("SonaraWeb").d("delete playlist ${playlist.browseId}: $deleted")
        if (deleted) {
            database.withTransaction {
                if (playlist.bookmarkedAt != null) update(playlist.toggleLike())
                delete(playlist)
            }
        }
        withContext(Dispatchers.Main) {
            ErToast.show(context, if (deleted) R.string.er_playlist_deleted else R.string.er_playlist_delete_failed)
        }
    }
}

/** A playlist edit YouTube accepted ([playlistId] is SONARA's own id). */
data class SavedPlaylistEdit(
    val playlistId: String,
    val description: String,
)

object PlaylistEditEvents {
    val saved = MutableSharedFlow<SavedPlaylistEdit>(extraBufferCapacity = 8)
}

/** Copies the cropped cover into app storage (the crop output lives in the cache). */
private fun copyCover(
    context: Context,
    playlistId: String,
    source: Uri,
): String? =
    runCatching {
        val dir = File(context.filesDir, "playlist_covers").apply { mkdirs() }
        val file = File(dir, "${playlistId}_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(source)?.use { input -> file.outputStream().use { input.copyTo(it) } }
        Uri.fromFile(file).toString()
    }.getOrNull()
