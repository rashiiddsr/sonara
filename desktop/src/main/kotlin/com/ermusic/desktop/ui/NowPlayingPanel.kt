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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import com.ermusic.desktop.playback.Credit
import com.ermusic.desktop.playback.PlayerState
import com.ermusic.desktop.playback.Track
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.MediaInfo
import com.metrolist.innertube.pages.ArtistPage

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(ErShape.Medium).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(Spacing.L), verticalArrangement = Arrangement.spacedBy(Spacing.S)) { content() }
}

@Composable
private fun CardTitle(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        action?.let { label ->
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickable(onClick = onAction).handCursor())
        }
    }
}

/** What's playing, down the right side: the cover, about the artist, the credits, what's next. */
@Composable
fun NowPlayingPanel(app: AppState, state: PlayerState) {
    val track = state.current ?: return
    val colors = MaterialTheme.colorScheme
    var menuAt by remember { mutableStateOf<IntOffset?>(null) }
    val artist by produceState<ArtistPage?>(null, track.artistId) {
        value = null
        value = track.artistId?.let { YouTube.artist(it).getOrNull() }
    }
    val info by produceState<MediaInfo?>(null, track.id) {
        value = null
        value = YouTube.getMediaInfo(track.id).getOrNull()
    }

    Column(Modifier.width(Size.Queue).fillMaxHeight().panel()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = Spacing.XS, end = Spacing.XS, top = Spacing.XS)) {
            SymbolButton("side_panel", "Hide panel", tint = colors.onSurfaceVariant, iconSize = Size.IconSmall) {
                app.sidePanel = null
                app.sidePanelDismissed = true
            }
            Text(track.albumName ?: "Now playing", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Box {
                SymbolButton("more_vert", "More", tint = colors.onSurfaceVariant, iconSize = Size.IconSmall) { menuAt = IntOffset.Zero }
                TrackMenu(track, app, menuAt) { menuAt = null }
            }
            SymbolButton("fullscreen", "Full screen", tint = colors.onSurfaceVariant, iconSize = Size.IconSmall, onClick = app::toggleFullscreen)
        }
        ScrollingColumn(Modifier.weight(1f).fillMaxWidth()) {
            item(key = "song", contentType = "song") {
                Column(Modifier.padding(horizontal = Spacing.L)) {
                    Art(track.artUrl, Modifier.fillMaxWidth().aspectRatio(1f), ErShape.Medium, pixels = 700)
                    Spacer(Modifier.height(Spacing.M))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(track.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            CreditsText(track.allCredits, app, MaterialTheme.typography.bodyMedium, colors.onSurfaceVariant, maxLines = 2)
                        }
                        SymbolButton("share", "Copy link", tint = colors.onSurfaceVariant, iconSize = Size.IconSmall) { app.copyLink(track.shareLink) }
                        val liked = track.id in app.likedIds
                        SymbolButton(if (liked) "favorite" else "favorite_border", "Like", tint = if (liked) colors.primary else colors.onSurfaceVariant, iconSize = Size.IconSmall) { app.toggleLike(track) }
                    }
                    Spacer(Modifier.height(Spacing.M))
                }
            }
            item(key = "next", contentType = "card") {
                state.next?.let { next ->
                    Column(Modifier.padding(start = Spacing.L, end = Spacing.L, bottom = Spacing.M)) {
                        Card {
                            CardTitle("Next in queue", "Open queue") { app.sidePanel = SidePanel.Queue }
                            NextRow(next, app, onClick = { app.player.next() })
                        }
                    }
                }
            }
            item(key = "artist", contentType = "card") {
                artist?.let { page ->
                    Column(Modifier.padding(start = Spacing.L, end = Spacing.L, bottom = Spacing.M)) {
                        Card {
                            CardTitle("About the artist")
                            Art(page.artist.thumbnail, Modifier.fillMaxWidth().aspectRatio(ARTIST_IMAGE_RATIO).clickable { app.go(Route.Artist(page.artist.id)) }.handCursor(), ErShape.Small, pixels = 700)
                            Text(page.artist.title, style = MaterialTheme.typography.titleMedium)
                            (page.monthlyListenerCount ?: page.subscriberCountText)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
                            page.description?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
            }
            item(key = "credits", contentType = "card") {
                Column(Modifier.padding(start = Spacing.L, end = Spacing.L, bottom = Spacing.L)) {
                    Card {
                        CardTitle("Credits")
                        track.allCredits.forEachIndexed { index, credit -> CreditRow(credit, if (index == 0) "Main artist" else "Artist", app) }
                        track.albumName?.let { album ->
                            Column(Modifier.then(track.albumId?.let { id -> Modifier.clickable { app.go(Route.Album(id)) }.handCursor() } ?: Modifier)) {
                                Text(album, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("Album", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                            }
                        }
                        info?.viewCount?.let { views -> Text("%,d plays".format(views), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
                        info?.uploadDate?.takeIf { it.isNotBlank() }?.let { date -> Text("Released $date", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}

private const val ARTIST_IMAGE_RATIO = 1.5f

/** One artist of the song, with their own page and Follow button. */
@Composable
private fun CreditRow(credit: Credit, role: String, app: AppState) {
    val page by produceState<ArtistPage?>(null, credit.id) {
        value = credit.id?.let { YouTube.artist(it).getOrNull() }
    }
    var following by remember(credit.id, page) { mutableStateOf(page?.isSubscribed == true) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            CreditsText(listOf(credit), app, MaterialTheme.typography.titleSmall, MaterialTheme.colorScheme.onSurface)
            Text(role, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        page?.artist?.channelId?.let { channel ->
            OutlinedButton(onClick = { app.follow(channel, !following) { following = !following } }, modifier = Modifier.handCursor()) { Text(if (following) "Following" else "Follow") }
        }
    }
}

@Composable
private fun NextRow(next: Track, app: AppState, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clip(ErShape.Small).clickable(onClick = onClick).handCursor()) {
        Art(next.artUrl, Modifier.size(Size.ListThumb), pixels = 120)
        Spacer(Modifier.width(Spacing.M))
        Column(Modifier.weight(1f)) {
            Text(next.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            CreditsText(next.allCredits, app, MaterialTheme.typography.bodySmall, MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
