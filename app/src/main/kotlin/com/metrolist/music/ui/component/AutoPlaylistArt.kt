/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.metrolist.music.R
import com.metrolist.music.ui.theme.AutoPlaylistGradients

/** The automatic playlists Library shows, each with its own gradient cover. */
enum class AutoArt(
    @DrawableRes val icon: Int,
    val colors: List<Color>,
) {
    LIKED(R.drawable.thumb_up, AutoPlaylistGradients.Liked),
    DOWNLOADED(R.drawable.offline, AutoPlaylistGradients.Downloaded),
    NEW_EPISODES(R.drawable.mic, AutoPlaylistGradients.NewEpisodes),
    EPISODES_FOR_LATER(R.drawable.history, AutoPlaylistGradients.EpisodesForLater),
    TOP(R.drawable.trending_up, AutoPlaylistGradients.Top),
    OTHER(R.drawable.queue_music, AutoPlaylistGradients.Other),
}

/** A full-colour gradient cover with a white icon, as YouTube Music draws Liked music. */
@Composable
fun AutoPlaylistArt(
    art: AutoArt,
    size: Dp,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(Brush.linearGradient(art.colors)),
    ) {
        Icon(
            painterResource(art.icon),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size / 2),
        )
    }
}

/** Fills the space it is given (grid covers). */
@Composable
fun AutoPlaylistArtFill(
    art: AutoArt,
    iconSize: Dp,
    shape: Shape,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .background(Brush.linearGradient(art.colors)),
    ) {
        Icon(painterResource(art.icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(iconSize))
    }
}
