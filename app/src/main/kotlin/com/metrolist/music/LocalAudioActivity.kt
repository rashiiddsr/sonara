/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music

import com.metrolist.music.ui.theme.rememberUseDarkTheme
import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.metrolist.music.ui.theme.ErMusicTheme
import com.metrolist.music.ui.theme.ErShape
import com.metrolist.music.ui.theme.Size
import com.metrolist.music.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PROGRESS_TICK_MS = 500L
private val Speeds = listOf(1f, 1.25f, 1.5f, 2f, 0.75f)
private const val SCRIM_ALPHA = 0.5f

/**
 * Plays an audio file opened from a file manager or another app, as a small popup over that app,
 * like the local-file player in Spotify or YouTube Music. It is media only and kept apart from
 * the account: its own player, no sign-in, nothing written to the library, history or playlists.
 * Opening it pauses the main player through audio focus.
 *
 * Previous / next walk the other audio files in the same folder (by name), which needs read
 * access to audio; that is asked the first time they are tapped.
 */
class LocalAudioActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer
    private var title by mutableStateOf("")
    private var artist by mutableStateOf("")
    private var artwork by mutableStateOf<Bitmap?>(null)
    private var playing by mutableStateOf(false)
    private var duration by mutableLongStateOf(0L)
    private var speedIndex by mutableIntStateOf(0)
    private var repeatOne by mutableStateOf(false)

    private var openedUri: Uri? = null
    private var folderLoaded = false

    /** Skip to run once folder access is granted: +1 next, -1 previous. */
    private var pendingSkip = 0

    private val audioPermission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    private val askAudio =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) loadFolder(then = pendingSkip)
            pendingSkip = 0
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        player =
            ExoPlayer
                .Builder(this)
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true,
                ).setHandleAudioBecomingNoisy(true)
                .build()
        player.addListener(
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    playing = isPlaying
                }

                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) duration = player.duration.coerceAtLeast(0L)
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    duration = 0L
                    mediaItem?.localConfiguration?.uri?.let(::readTags)
                }
            },
        )
        open(intent)
        setContent {
            ErMusicTheme(darkTheme = rememberUseDarkTheme()) {
                LocalAudioPopup()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        open(intent)
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }

    private fun open(intent: Intent) {
        val uri = intent.data ?: run {
            finish()
            return
        }
        openedUri = uri
        folderLoaded = false
        readTags(uri)
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.play()
        if (hasAudioAccess()) loadFolder(then = 0)
    }

    private fun hasAudioAccess() =
        ContextCompat.checkSelfPermission(this, audioPermission) == PackageManager.PERMISSION_GRANTED

    private fun skip(direction: Int) {
        when {
            folderLoaded -> step(direction)
            hasAudioAccess() -> loadFolder(then = direction)
            else -> {
                pendingSkip = direction
                askAudio.launch(audioPermission)
            }
        }
    }

    private fun step(direction: Int) {
        if (direction > 0) player.seekToNextMediaItem() else player.seekToPrevious()
    }

    /** Puts the folder's other audio files around the playing one, keeping playback going. */
    private fun loadFolder(then: Int) {
        val uri = openedUri ?: return
        lifecycleScope.launch {
            val folder = withContext(Dispatchers.IO) { runCatching { folderOf(uri) }.getOrNull() }
            if (folder != null && openedUri == uri && !folderLoaded) {
                val (siblings, index) = folder
                player.addMediaItems(0, siblings.take(index).map(MediaItem::fromUri))
                player.addMediaItems(siblings.drop(index + 1).map(MediaItem::fromUri))
                folderLoaded = true
            }
            if (then != 0) step(then)
        }
    }

    /** The audio files in the same folder as [uri], sorted by name, and where [uri] sits. */
    private fun folderOf(uri: Uri): Pair<List<Uri>, Int>? {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val id =
            if (uri.authority == MediaStore.AUTHORITY) {
                ContentUris.parseId(uri)
            } else {
                // A file manager's own link: find the same file in the media index by name and size.
                val (name, size) =
                    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                        ?.use { c -> if (c.moveToFirst()) c.getString(0) to c.getLong(1) else null } ?: return null
                contentResolver.query(
                    collection,
                    arrayOf(MediaStore.Audio.Media._ID),
                    "${MediaStore.Audio.Media.DISPLAY_NAME} = ? AND ${MediaStore.Audio.Media.SIZE} = ?",
                    arrayOf(name, size.toString()),
                    null,
                )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null } ?: return null
            }
        @Suppress("DEPRECATION")
        val folderColumn = MediaStore.Audio.Media.DATA
        val path =
            contentResolver.query(ContentUris.withAppendedId(collection, id), arrayOf(folderColumn), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: return null
        val folder = path.substringBeforeLast('/') + "/"
        val ids = mutableListOf<Long>()
        contentResolver.query(
            collection,
            arrayOf(MediaStore.Audio.Media._ID, folderColumn),
            "$folderColumn LIKE ?",
            arrayOf("$folder%"),
            "${MediaStore.Audio.Media.DISPLAY_NAME} COLLATE NOCASE",
        )?.use { c ->
            while (c.moveToNext()) {
                // Only this folder, not its subfolders.
                if (c.getString(1)?.substringBeforeLast('/') + "/" == folder) ids += c.getLong(0)
            }
        }
        val index = ids.indexOf(id)
        if (index < 0 || ids.size < 2) return null
        return ids.map { ContentUris.withAppendedId(collection, it) } to index
    }

    private fun readTags(uri: Uri) {
        lifecycleScope.launch {
            val (t, a, art) = withContext(Dispatchers.IO) {
                val retriever = MediaMetadataRetriever()
                val tags = runCatching {
                    retriever.setDataSource(this@LocalAudioActivity, uri)
                    Triple(
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).orEmpty(),
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty(),
                        retriever.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size) },
                    )
                }.getOrNull() ?: Triple("", "", null)
                retriever.release()
                if (tags.first.isBlank()) tags.copy(first = displayName(uri) ?: uri.lastPathSegment.orEmpty()) else tags
            }
            title = t
            artist = a
            artwork = art
        }
    }

    private fun displayName(uri: Uri): String? =
        runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.substringBeforeLast('.') else null
            }
        }.getOrNull()

    @Composable
    private fun LocalAudioPopup() {
        var position by remember { mutableLongStateOf(0L) }
        var dragging by remember { mutableFloatStateOf(-1f) }
        LaunchedEffect(Unit) {
            while (true) {
                position = player.currentPosition
                delay(PROGRESS_TICK_MS)
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = SCRIM_ALPHA))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = ::finish),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                shape = ErShape.SheetTop,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    // Taps on the card stay on the card.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            ) {
                Column(
                    Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = Spacing.XL, vertical = Spacing.L),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Cover()
                        Column(
                            Modifier
                                .weight(1f)
                                .padding(horizontal = Spacing.M),
                        ) {
                            Text(
                                title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                artist.ifBlank { stringResource(R.string.er_local_file) },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = ::finish) {
                            Icon(painterResource(R.drawable.close), contentDescription = stringResource(R.string.er_close))
                        }
                    }
                    Slider(
                        value = if (dragging >= 0f) dragging else if (duration > 0) position.toFloat() / duration else 0f,
                        onValueChange = { dragging = it },
                        onValueChangeFinished = {
                            if (duration > 0) player.seekTo((dragging * duration).toLong())
                            position = player.currentPosition
                            dragging = -1f
                        },
                        modifier = Modifier.padding(top = Spacing.M),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            formatTime(position),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            formatTime(duration),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.S),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = {
                                speedIndex = (speedIndex + 1) % Speeds.size
                                player.setPlaybackSpeed(Speeds[speedIndex])
                            },
                            modifier = Modifier.width(Size.IconButton + Spacing.L),
                        ) { Text(speedLabel(Speeds[speedIndex])) }
                        IconButton(onClick = { skip(-1) }) {
                            Icon(painterResource(R.drawable.skip_previous), contentDescription = null)
                        }
                        FilledIconButton(
                            onClick = {
                                when {
                                    player.isPlaying -> player.pause()
                                    player.playbackState == Player.STATE_ENDED -> player.seekTo(0L).also { player.play() }
                                    else -> player.play()
                                }
                            },
                            shape = CircleShape,
                            modifier = Modifier.size(Size.PlayButton),
                        ) {
                            Icon(
                                painterResource(if (playing) R.drawable.pause else R.drawable.play),
                                contentDescription = null,
                                modifier = Modifier.size(Size.Icon + Spacing.S),
                            )
                        }
                        IconButton(onClick = { skip(1) }) {
                            Icon(painterResource(R.drawable.skip_next), contentDescription = null)
                        }
                        IconButton(
                            onClick = {
                                repeatOne = !repeatOne
                                player.repeatMode = if (repeatOne) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                            },
                            modifier = Modifier.width(Size.IconButton + Spacing.L),
                        ) {
                            Icon(
                                painterResource(if (repeatOne) R.drawable.repeat_one_on else R.drawable.repeat_one),
                                contentDescription = null,
                                tint = if (repeatOne) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Cover() {
        Box(
            Modifier
                .size(Size.ListRow)
                .clip(ErShape.Small)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            val art = artwork
            if (art != null) {
                Image(
                    art.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    painterResource(R.drawable.music_note),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    private fun speedLabel(speed: Float) = if (speed % 1f == 0f) "${speed.toInt()}×" else "$speed×"

    private fun formatTime(ms: Long): String {
        val s = ms / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }
}
