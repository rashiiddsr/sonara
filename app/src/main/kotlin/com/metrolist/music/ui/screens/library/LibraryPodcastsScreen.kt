/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.library

import com.metrolist.music.ui.theme.Spacing
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.metrolist.music.utils.joinByBullet
import com.metrolist.music.ui.component.AutoPlaylistArt
import com.metrolist.music.ui.component.AutoArt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.metrolist.music.ui.theme.ErShape
import com.metrolist.music.ui.theme.Size
import com.metrolist.music.ui.utils.podcastRoute

/**
 * Library > Podcasts, as in YouTube Music: "New episodes" and "Episodes for later" first, then
 * the shows saved on the account. Episodes stay here and are no longer counted as songs (Liked
 * music leaves them out).
 */
@Composable
fun LibraryPodcastsScreen(
    navController: NavController,
    header: @Composable () -> Unit,
) {
    val database = LocalDatabase.current
    val syncUtils = LocalSyncUtils.current
    val podcasts by remember(database) { database.subscribedPodcasts() }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    // The saved shows and episodes come from the account; read them each time the tab opens.
    LaunchedEffect(Unit) {
        syncUtils.syncPodcastSubscriptions()
        syncUtils.syncEpisodesForLater()
    }

    LazyColumn(
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "header", contentType = CONTENT_TYPE_HEADER) { header() }
        item(key = "new_episodes", contentType = CONTENT_TYPE_SHORTCUT) {
            Shortcut(AutoArt.NEW_EPISODES, stringResource(R.string.new_episodes), stringResource(R.string.auto_playlist)) {
                navController.navigate("online_playlist/RDPN")
            }
        }
        item(key = "episodes_for_later", contentType = CONTENT_TYPE_SHORTCUT) {
            Shortcut(AutoArt.EPISODES_FOR_LATER, stringResource(R.string.episodes_for_later), stringResource(R.string.er_queued_episodes)) {
                navController.navigate("online_playlist/SE")
            }
        }
        if (podcasts.isEmpty()) {
            item(key = "empty", contentType = CONTENT_TYPE_EMPTY) {
                Text(
                    text = stringResource(R.string.er_no_podcasts_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.Gutter, vertical = Spacing.L),
                )
            }
        }
        items(podcasts, key = { it.id }, contentType = { CONTENT_TYPE_PODCAST }) { podcast ->
            ListItem(
                title = podcast.title,
                subtitle = joinByBullet(stringResource(R.string.er_type_podcast), podcast.author),
                thumbnailContent = {
                    AsyncImage(
                        model = podcast.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(Size.ListThumb).clip(ErShape.Small),
                    )
                },
                modifier = Modifier.fillMaxWidth().clickable { navController.navigate(podcastRoute(podcast.id)) },
            )
        }
    }
}

@Composable
private fun Shortcut(
    art: AutoArt,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    ListItem(
        title = title,
        subtitle = subtitle,
        thumbnailContent = { AutoPlaylistArt(art, Size.ListThumb, ErShape.Small) },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}

private const val CONTENT_TYPE_HEADER = "header"
private const val CONTENT_TYPE_SHORTCUT = "shortcut"
private const val CONTENT_TYPE_PODCAST = "podcast"
private const val CONTENT_TYPE_EMPTY = "empty"
