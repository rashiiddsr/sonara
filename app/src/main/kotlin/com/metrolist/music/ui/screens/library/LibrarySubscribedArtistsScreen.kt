/**
 * Sonara (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalSyncUtils
import com.metrolist.music.R
import com.metrolist.music.ui.component.ListItem
import com.metrolist.music.ui.theme.Size
import kotlinx.coroutines.flow.map

/**
 * Library > Artists, as in YouTube Music: the artists the account subscribes to, newest first,
 * read from YouTube each time the tab opens. Podcast channels stay under Podcasts.
 */
@Composable
fun LibrarySubscribedArtistsScreen(
    navController: NavController,
    header: @Composable () -> Unit,
) {
    val database = LocalDatabase.current
    val syncUtils = LocalSyncUtils.current
    val artists by remember(database) {
        database.artistsBookmarkedByCreateDateAsc().map { list ->
            list.filterNot { it.artist.isPodcastChannel || it.artist.isLocal }.reversed()
        }
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    LaunchedEffect(Unit) { syncUtils.syncArtistsSubscriptions() }

    LazyColumn(
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "header", contentType = CONTENT_TYPE_HEADER) { header() }
        items(artists, key = { it.id }, contentType = { CONTENT_TYPE_ARTIST }) { artist ->
            ListItem(
                title = artist.artist.name,
                subtitle = stringResource(R.string.er_type_artist),
                thumbnailContent = {
                    AsyncImage(
                        model = artist.artist.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(Size.ListThumb)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    )
                },
                modifier = Modifier.fillMaxWidth().clickable { navController.navigate("artist/${artist.id}") },
            )
        }
    }
}

private const val CONTENT_TYPE_HEADER = "header"
private const val CONTENT_TYPE_ARTIST = "artist"
