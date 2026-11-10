/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.metrolist.music.LocalSyncUtils
import com.metrolist.music.R
import com.metrolist.music.constants.InnerTubeCookieKey
import com.metrolist.music.db.entities.PlaylistEntity
import com.metrolist.music.extensions.isSyncEnabled
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    initialTextFieldValue: String? = null,
    allowSyncing: Boolean = true,
    onPlaylistCreated: ((String) -> Unit)? = null,
    /** "Import playlist": the new playlist gets a checked source playlist's songs. */
    importing: Boolean = false,
) {
    val syncUtils = LocalSyncUtils.current
    val coroutineScope = rememberCoroutineScope()
    // Signed-in accounts always create the playlist on YouTube too; local-only is the fallback.
    val syncedPlaylist = allowSyncing
    val context = LocalContext.current

    val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    val isSignedIn = innerTubeCookie.isNotEmpty()

    val notLoggedInYoutubeStr = stringResource(R.string.not_logged_in_youtube)
    val syncDisabledStr = stringResource(R.string.sync_disabled)
    val playlistCreatedLocallyStr = stringResource(R.string.playlist_created_locally)

    // Same editor as YouTube Music's "New playlist": title, description, visibility.
    PlaylistEditorSheet(
        initialTitle = initialTextFieldValue.orEmpty(),
        onDismiss = onDismiss,
        onDone = { playlistName, description, privacy, _ ->
            val playlistId = syncUtils.createPlaylist(
                playlist = PlaylistEntity(
                    name = playlistName,
                    bookmarkedAt = LocalDateTime.now(),
                    isEditable = true,
                ),
                syncWithYouTube = syncedPlaylist,
                description = description,
                privacy = privacy,
            )
            withContext(Dispatchers.Main) {
                ErToast.show(context, if (playlistId != null) R.string.er_playlist_created else R.string.er_playlist_create_failed)
                playlistId?.let { onPlaylistCreated?.invoke(it) }
            }
            playlistId != null
        },
        // Import copies another playlist's songs on YouTube; only for a signed-in account.
        onImport = if (!importing || !isSignedIn) null else { playlistName, description, privacy, source ->
            val playlistId = syncUtils.createPlaylist(
                playlist = PlaylistEntity(
                    name = playlistName,
                    bookmarkedAt = LocalDateTime.now(),
                    isEditable = true,
                ),
                syncWithYouTube = true,
                description = description,
                privacy = privacy,
                sourcePlaylistId = source.playlistId,
            )
            withContext(Dispatchers.Main) {
                ErToast.show(context, if (playlistId != null) R.string.er_playlist_imported else R.string.er_playlist_create_failed)
                playlistId?.let { onPlaylistCreated?.invoke(it) }
            }
            playlistId != null
        },
    )
}
