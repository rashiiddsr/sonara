/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.library

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import com.metrolist.music.LocalAudioActivity
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.ui.component.EmptyPlaceholder
import com.metrolist.music.ui.component.ListItem
import com.metrolist.music.ui.theme.ErShape
import com.metrolist.music.ui.theme.Size
import com.metrolist.music.ui.theme.Spacing
import com.metrolist.music.utils.joinByBullet
import com.metrolist.music.utils.makeTimeString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** A music file on the phone, as MediaStore lists it. */
@Immutable
data class DeviceTrack(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String?,
    val folder: String?,
    val durationMs: Long,
    val albumId: Long,
)

/**
 * Library > On device: the music files on the phone (MediaStore, music only, not ringtones or
 * voice notes). They stay apart from the YouTube library: a tap opens SONARA's local player,
 * which also steps through the other files in the same folder. Nothing is added to the account.
 */
@Composable
fun LibraryDeviceScreen(header: @Composable () -> Unit) {
    val context = LocalContext.current
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED)
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var tracks by remember { mutableStateOf<List<DeviceTrack>?>(null) }

    LaunchedEffect(granted) {
        if (granted) tracks = withContext(Dispatchers.IO) { deviceTracks(context) }
    }

    LazyColumn(
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "header", contentType = CONTENT_TYPE_HEADER) { header() }
        val list = tracks
        when {
            !granted -> item(key = "permission", contentType = CONTENT_TYPE_EMPTY) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(Spacing.XL),
                ) {
                    Icon(painterResource(R.drawable.storage), contentDescription = null, modifier = Modifier.size(Size.IconButton))
                    Spacer(Modifier.height(Spacing.M))
                    Text(
                        stringResource(R.string.er_device_permission),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(Spacing.L))
                    Button(onClick = { request.launch(permission) }) { Text(stringResource(R.string.er_device_allow)) }
                }
            }
            list == null -> item(key = "loading", contentType = CONTENT_TYPE_EMPTY) {
                Box(Modifier.fillMaxWidth().padding(Spacing.XL), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            list.isEmpty() -> item(key = "empty", contentType = CONTENT_TYPE_EMPTY) {
                EmptyPlaceholder(icon = R.drawable.music_note, text = stringResource(R.string.er_device_empty))
            }
            else -> items(list, key = { it.id }, contentType = { CONTENT_TYPE_TRACK }) { track ->
                ListItem(
                    title = track.title,
                    subtitle = joinByBullet(track.artist, track.folder, makeTimeString(track.durationMs)),
                    thumbnailContent = {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(Size.ListThumb)
                                .clip(ErShape.Small)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        ) {
                            Icon(painterResource(R.drawable.music_note), contentDescription = null)
                            // The album art MediaStore keeps, when the file has one; covers the note.
                            AsyncImage(
                                model = ContentUris.withAppendedId(ALBUM_ART, track.albumId),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.matchParentSize(),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().clickable {
                        context.startActivity(
                            Intent(context, LocalAudioActivity::class.java)
                                .setAction(Intent.ACTION_VIEW)
                                .setDataAndType(track.uri, "audio/*"),
                        )
                    },
                )
            }
        }
    }
}

/** Music files on the phone, by title. */
private fun deviceTracks(context: Context): List<DeviceTrack> {
    val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.ALBUM_ID,
        @Suppress("DEPRECATION") MediaStore.Audio.Media.DATA,
    )
    val tracks = mutableListOf<DeviceTrack>()
    runCatching {
        context.contentResolver.query(
            collection,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE",
        )?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val title = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val duration = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val album = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            @Suppress("DEPRECATION")
            val data = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            while (cursor.moveToNext()) {
                val trackId = cursor.getLong(id)
                tracks += DeviceTrack(
                    id = trackId,
                    uri = ContentUris.withAppendedId(collection, trackId),
                    title = cursor.getString(title).orEmpty(),
                    artist = cursor.getString(artist)?.takeUnless { it == MediaStore.UNKNOWN_STRING },
                    folder = cursor.getString(data)?.let { File(it).parentFile?.name },
                    durationMs = cursor.getLong(duration),
                    albumId = cursor.getLong(album),
                )
            }
        }
    }
    return tracks
}

private val ALBUM_ART: Uri = Uri.parse("content://media/external/audio/albumart")

private const val CONTENT_TYPE_HEADER = "header"
private const val CONTENT_TYPE_EMPTY = "empty"
private const val CONTENT_TYPE_TRACK = "device_track"
