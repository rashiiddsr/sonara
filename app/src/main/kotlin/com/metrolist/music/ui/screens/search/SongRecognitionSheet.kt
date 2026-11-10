/**
 * Sonara (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.recognition.SongRecognition
import com.metrolist.music.recognition.SongRecognizer
import com.metrolist.music.ui.component.PrimaryButton
import com.metrolist.music.ui.component.SecondaryButton
import com.metrolist.music.ui.theme.ErShape
import com.metrolist.music.ui.theme.Motion
import com.metrolist.music.ui.theme.Size
import com.metrolist.music.ui.theme.Spacing
import com.metrolist.shazamkit.models.RecognitionResult
import kotlinx.coroutines.launch

/**
 * "What's playing?": listens through the microphone and names the song playing nearby (Shazam),
 * then plays it from YouTube Music or searches for it. It knows recordings, not tunes: a song
 * sung or hummed by the user is not found. Needs the microphone permission, asked before it opens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongRecognitionSheet(
    onSearch: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val playerConnection = LocalPlayerConnection.current
    val scope = rememberCoroutineScope()
    var attempt by remember { mutableIntStateOf(0) }
    var level by remember { mutableFloatStateOf(0f) }
    var outcome by remember { mutableStateOf<SongRecognition?>(null) }
    var opening by remember { mutableStateOf(false) }
    var startedSong by remember { mutableStateOf(false) }

    ListeningEffects(onLeave = onDismiss, shouldResume = { !startedSong })

    LaunchedEffect(attempt) {
        outcome = null
        outcome = SongRecognizer.recognize(onLevel = { level = it })
        level = 0f
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = ErShape.SheetTop,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        AnimatedContent(
            targetState = outcome,
            transitionSpec = { fadeIn(Motion.medium()) togetherWith fadeOut(Motion.fast()) },
            contentKey = { it?.javaClass },
            label = "recognition",
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.XL, vertical = Spacing.L),
        ) { state ->
            when (state) {
                is SongRecognition.Match ->
                    MatchContent(
                        result = state.result,
                        opening = opening,
                        onPlay = {
                            val query = state.result.query()
                            opening = true
                            scope.launch {
                                // Shazam names the recording; YouTube Music's own song for it is
                                // found by a song search, which gives the audio track, not a video.
                                val song =
                                    YouTube
                                        .search(query, YouTube.SearchFilter.FILTER_SONG)
                                        .getOrNull()
                                        ?.items
                                        ?.filterIsInstance<SongItem>()
                                        ?.firstOrNull()
                                opening = false
                                if (song != null && playerConnection != null) {
                                    startedSong = true
                                    playerConnection.playQueue(YouTubeQueue.radio(song.toMediaMetadata()))
                                    onDismiss()
                                } else {
                                    onSearch(query)
                                }
                            }
                        },
                        onSearch = { onSearch(state.result.query()) },
                    )

                else ->
                    ListeningContent(
                        outcome = state,
                        level = level,
                        onRetry = { if (outcome != null) attempt++ },
                    )
            }
        }
    }
}

@Composable
private fun ListeningContent(
    outcome: SongRecognition?,
    level: Float,
    onRetry: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(
                when (outcome) {
                    SongRecognition.NoMatch -> R.string.er_recognize_no_match
                    SongRecognition.Failed -> R.string.er_recognize_failed
                    else -> R.string.er_recognize_listening
                },
            ),
            style = MaterialTheme.typography.headlineSmall,
            color = if (outcome == null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().heightIn(min = Size.IconButton * 2),
        )
        if (outcome == null) {
            Text(
                text = stringResource(R.string.er_recognize_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(Spacing.XL))
        PulseButton(
            icon = R.drawable.graphic_eq,
            contentDescription = stringResource(R.string.er_recognize_song),
            active = outcome == null,
            level = level,
            onClick = onRetry,
        )
        Spacer(Modifier.height(Spacing.XXL))
    }
}

@Composable
private fun MatchContent(
    result: RecognitionResult,
    opening: Boolean,
    onPlay: () -> Unit,
    onSearch: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        AsyncImage(
            model = result.coverArtHqUrl ?: result.coverArtUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(Size.CardThumb)
                .clip(ErShape.Medium),
        )
        Spacer(Modifier.height(Spacing.L))
        Text(
            text = result.title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = result.artist,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(Spacing.XL))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            PrimaryButton(
                text = stringResource(R.string.play),
                icon = R.drawable.play,
                enabled = !opening,
                onClick = onPlay,
            )
            SecondaryButton(
                text = stringResource(R.string.search),
                icon = R.drawable.search,
                onClick = onSearch,
            )
        }
        Spacer(Modifier.height(Spacing.XL))
    }
}

private fun RecognitionResult.query(): String = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" ")
