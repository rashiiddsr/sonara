/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import com.metrolist.music.LocalDatabase
import com.metrolist.innertube.models.WatchEndpoint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.R
import com.metrolist.music.ui.theme.ErShape
import com.metrolist.music.ui.theme.Spacing
import kotlinx.coroutines.delay

private const val SEARCH_DEBOUNCE_MS = 350L
private const val SUGGESTION_COUNT = 25

/**
 * "Add a song" for a playlist: suggestions first, or search YouTube Music; songs already in the
 * playlist show a check.
 * Tapping + adds, tapping the check takes the song out again, so a song is never added twice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSongsSheet(
    inPlaylist: Set<String>,
    onAdd: (SongItem) -> Unit,
    onRemove: (songId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val results = remember { mutableStateListOf<SongItem>() }
    val suggestions = remember { mutableStateListOf<SongItem>() }
    val added = remember { mutableStateListOf<String>().apply { addAll(inPlaylist) } }
    val database = LocalDatabase.current

    // Before anything is typed: a radio from the playlist's songs (or the last song played),
    // like YouTube Music's "Suggestions" under a playlist.
    LaunchedEffect(Unit) {
        val seed = inPlaylist.shuffled().firstOrNull()
            ?: withContext(Dispatchers.IO) { database.latestEvent().first()?.song?.id }
        val songs = withContext(Dispatchers.IO) {
            if (seed != null) {
                YouTube.next(WatchEndpoint(videoId = seed, playlistId = "RDAMVM$seed")).getOrNull()
                    ?.items.orEmpty()
            } else {
                YouTube.home().getOrNull()?.sections.orEmpty().flatMap { it.items }.filterIsInstance<SongItem>()
            }
        }
        suggestions.addAll(songs.filter { it.id !in inPlaylist }.distinctBy { it.id }.take(SUGGESTION_COUNT))
    }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            results.clear()
            loading = false
            return@LaunchedEffect
        }
        delay(SEARCH_DEBOUNCE_MS)
        loading = true
        val songs = YouTube.search(query.trim(), YouTube.SearchFilter.FILTER_SONG).getOrNull()?.items?.filterIsInstance<SongItem>()
        results.clear()
        songs?.let(results::addAll)
        loading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = ErShape.SheetTop,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Text(
                stringResource(R.string.er_add_songs),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = Spacing.XL),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.er_search_songs)) },
                leadingIcon = { Icon(painterResource(R.drawable.search), contentDescription = null) },
                singleLine = true,
                shape = ErShape.Pill,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {}),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.L, vertical = Spacing.M),
            )
            val shown = if (query.isBlank()) suggestions else results
            if ((loading && results.isEmpty()) || (query.isBlank() && suggestions.isEmpty())) {
                Box(Modifier.fillMaxWidth().padding(Spacing.XL), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            LazyColumn {
                if (query.isBlank() && suggestions.isNotEmpty()) {
                    item(key = "suggestions_title", contentType = "title") {
                        Text(
                            stringResource(R.string.er_suggestions),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.XL, vertical = Spacing.S),
                        )
                    }
                }
                items(shown, key = { it.id }, contentType = { "song" }) { song ->
                    val isAdded = song.id in added
                    YouTubeListItem(
                        item = song,
                        isSwipeable = false,
                        trailingContent = {
                            IconButton(
                                onClick = {
                                    if (isAdded) {
                                        added -= song.id
                                        onRemove(song.id)
                                    } else {
                                        added += song.id
                                        onAdd(song)
                                    }
                                },
                            ) {
                                Icon(
                                    painterResource(if (isAdded) R.drawable.check else R.drawable.add),
                                    contentDescription = stringResource(if (isAdded) R.string.er_added else R.string.er_add_songs),
                                    tint = if (isAdded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}
