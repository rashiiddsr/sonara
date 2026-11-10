/**
 * Sonara (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.playlist

import com.metrolist.music.utils.ShareItem
import com.metrolist.music.utils.ShareCard
import com.metrolist.music.ui.component.DetailPane
import com.metrolist.music.ui.theme.topBarBackdrop
import com.metrolist.music.ui.theme.backdropTopBarColors
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.ui.component.ErrorState
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.LoadingState
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.PrimaryButton
import com.metrolist.music.ui.component.SecondaryButton
import com.metrolist.music.ui.component.YouTubeListItem
import com.metrolist.music.ui.menu.YouTubeSongMenu
import com.metrolist.music.ui.theme.ErShape
import com.metrolist.music.ui.theme.Size
import com.metrolist.music.ui.theme.Spacing
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.joinByBullet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Route of [RadioScreen]; every value is URL-encoded. */
fun radioRoute(
    endpoint: WatchEndpoint,
    name: String,
    thumbnailUrl: String?,
    round: Boolean = false,
): String {
    fun enc(value: String?) = android.net.Uri.encode(value.orEmpty())
    return "radio?playlistId=${enc(endpoint.playlistId)}&videoId=${enc(endpoint.videoId)}&params=${enc(endpoint.params)}" +
        "&name=${enc(name)}&thumbnail=${enc(thumbnailUrl)}&round=$round"
}

/** A song's radio: YouTube Music's mix that starts from it. */
fun songRadioRoute(song: MediaMetadata): String =
    radioRoute(
        endpoint = WatchEndpoint(videoId = song.id, playlistId = "RDAMVM${song.id}"),
        name = song.title,
        thumbnailUrl = song.thumbnailUrl,
    )

/** An artist's radio, with the artist's photo in a circle. */
fun artistRadioRoute(endpoint: WatchEndpoint, artistName: String, thumbnailUrl: String?): String =
    radioRoute(endpoint, artistName, thumbnailUrl, round = true)

/**
 * A radio (an artist's, a song's or a playlist's) as a page, like a playlist: the songs YouTube
 * Music lines up for it, with Play and Shuffle, instead of starting to play straight away.
 * Tapping a song plays the radio from that song; it keeps going past the list, as radio does.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RadioScreen(
    navController: NavController,
    playlistId: String?,
    videoId: String?,
    params: String?,
    name: String,
    thumbnailUrl: String?,
    round: Boolean,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val context = androidx.compose.ui.platform.LocalContext.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val endpoint = remember(playlistId, videoId, params) { WatchEndpoint(videoId = videoId, playlistId = playlistId, params = params) }

    var attempt by remember { mutableIntStateOf(0) }
    val songs by produceState<Result<List<SongItem>>?>(initialValue = null, endpoint, attempt) {
        value = null
        value = withContext(Dispatchers.IO) { YouTube.next(endpoint).map { it.items } }
    }
    val title = stringResource(R.string.er_radio_title, name)

    fun play(startAt: SongItem?, shuffle: Boolean) {
        playerConnection.player.shuffleModeEnabled = shuffle
        playerConnection.playQueue(
            YouTubeQueue(
                if (startAt == null) endpoint else endpoint.copy(videoId = startAt.id),
                preloadItem = null,
            ),
        )
    }

    val loaded = songs?.getOrNull()
    // The header: the list's first item, or the left pane on a landscape tablet (DetailPane).
    val detailHeader: (@Composable () -> Unit)? =
        run {
            @Composable {
            RadioHeader(
                title = title,
                subtitle = joinByBullet(
                    stringResource(R.string.radio),
                    loaded?.let { pluralStringResource(R.plurals.n_song, it.size, it.size) },
                ),
                thumbnailUrl = thumbnailUrl,
                round = round,
                canPlay = !loaded.isNullOrEmpty(),
                onPlay = { play(null, shuffle = false) },
                onShuffle = { play(null, shuffle = true) },
                // The radio as a card with its cover and Sonara's name, and its link.
                onShare = {
                    ShareCard.share(
                        context,
                        ShareItem(
                            kind = context.getString(R.string.radio),
                            title = title,
                            subtitle = loaded?.take(SHARE_ARTISTS)?.flatMap { song -> song.artists.map { it.name } }?.distinct()?.take(SHARE_ARTISTS)?.joinToString(),
                            coverUrl = thumbnailUrl,
                            link = radioLink(endpoint),
                            round = round,
                        ),
                    )
                },
            )
        }
        }
    DetailPane(header = detailHeader) { headerInList ->
        LazyColumn(
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (headerInList) {
                item(key = "header", contentType = "header") {
                    detailHeader?.invoke()
                }
            }
            when {
                songs == null -> item(key = "loading", contentType = "loading") { LoadingState() }
                songs?.isFailure == true -> item(key = "error", contentType = "error") {
                    ErrorState(onRetry = { attempt++ })
                }
                else -> itemsIndexed(loaded.orEmpty(), key = { index, song -> "${song.id}_$index" }, contentType = { _, _ -> "song" }) { _, song ->
                    YouTubeListItem(
                        item = song,
                        isActive = mediaMetadata?.id == song.id,
                        isPlaying = isPlaying,
                        trailingContent = {
                            androidx.compose.material3.IconButton(onClick = { menuState.show { YouTubeSongMenu(song = song, onDismiss = menuState::dismiss) } }) {
                                Icon(painterResource(R.drawable.more_vert), contentDescription = stringResource(R.string.er_more))
                            }
                        },
                        modifier = Modifier.combinedClickable(
                            onClick = {
                                if (song.id == mediaMetadata?.id) playerConnection.openPlayer() else play(song, shuffle = false)
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuState.show { YouTubeSongMenu(song = song, onDismiss = menuState::dismiss) }
                            },
                        ),
                    )
                }
            }
        }
    }

    TopAppBar(
        title = {},
        colors = backdropTopBarColors(),
        modifier = Modifier.topBarBackdrop(),
        navigationIcon = {
            IconButton(onClick = navController::navigateUp, onLongClick = navController::backToMain) {
                Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
            }
        },
    )
}

/** The cover (an artist's photo in a circle) with a teal radio badge, the title, and Play / Shuffle. */
@Composable
private fun RadioHeader(
    title: String,
    subtitle: String,
    thumbnailUrl: String?,
    round: Boolean,
    canPlay: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onShare: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Gutter, vertical = Spacing.L),
    ) {
        val artShape = if (round) CircleShape else ErShape.Large
        Box {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(Size.HeaderArt)
                    .shadow(Spacing.L, artShape, clip = false)
                    .clip(artShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(Spacing.S)
                    .size(Size.IconButton)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            ) {
                Icon(
                    painterResource(R.drawable.radio),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(Size.Icon),
                )
            }
        }
        Spacer(Modifier.height(Spacing.L))
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(Spacing.XS))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.L))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.M), verticalAlignment = Alignment.CenterVertically) {
            PrimaryButton(text = stringResource(R.string.play), onClick = onPlay, icon = R.drawable.play, enabled = canPlay)
            SecondaryButton(text = stringResource(R.string.shuffle), onClick = onShuffle, icon = R.drawable.shuffle, enabled = canPlay)
            androidx.compose.material3.IconButton(onClick = onShare) {
                Icon(painterResource(R.drawable.share), contentDescription = stringResource(R.string.share))
            }
        }
    }
}

/** The radio on YouTube Music: from its song when it has one, else as a list. */
private fun radioLink(endpoint: WatchEndpoint): String {
    val list = endpoint.playlistId
    return when {
        endpoint.videoId != null && list != null -> "https://music.youtube.com/watch?v=${endpoint.videoId}&list=$list"
        endpoint.videoId != null -> "https://music.youtube.com/watch?v=${endpoint.videoId}"
        else -> "https://music.youtube.com/playlist?list=$list"
    }
}

/** How many artists name the radio under its title on the card. */
private const val SHARE_ARTISTS = 3
