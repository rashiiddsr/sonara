/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.search

import com.metrolist.music.ui.theme.rememberWindowLayout
import com.metrolist.music.ui.theme.WindowLayout
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import com.metrolist.music.ui.screens.rememberItemActions
import com.metrolist.music.ui.screens.itemClicks
import com.metrolist.music.ui.component.YouTubeGridItem
import com.metrolist.music.ui.component.SectionHeader
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.shadow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.LocalNavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.ui.component.ErrorState
import com.metrolist.music.ui.component.SkeletonBlock
import com.metrolist.music.ui.component.shimmer.ShimmerHost
import com.metrolist.music.ui.theme.ErShape
import com.metrolist.music.ui.theme.Size
import com.metrolist.music.ui.theme.Spacing
import com.metrolist.music.viewmodels.MoodAndGenresViewModel

private const val CARD_ASPECT = 1.7f

/**
 * Search before the field is focused: a search pill, recommendation shelves (new releases and
 * charts), then "Browse all" genre/mood cards in each category's own color with a cover from it.
 */
@Composable
fun SearchDiscover(
    onFocusSearch: () -> Unit,
    onVoiceSearch: (() -> Unit)?,
    onRecognize: (() -> Unit)?,
    viewModel: MoodAndGenresViewModel = hiltViewModel(),
) {
    val navController = LocalNavController.current
    val sections by viewModel.moodAndGenres.collectAsStateWithLifecycle()
    val shelves by viewModel.shelves.collectAsStateWithLifecycle()
    val actions = rememberItemActions()
    val insets = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)

    // Larger cards on a tablet: a phone's size would line up too many of them, too small.
    val cardMin = if (rememberWindowLayout().wide) WindowLayout.TabletGridCard else Size.CardThumb + Spacing.L
    LazyVerticalGrid(
        columns = GridCells.Adaptive(cardMin),
        horizontalArrangement = Arrangement.spacedBy(Spacing.S),
        verticalArrangement = Arrangement.spacedBy(Spacing.S),
        contentPadding = insets.asPaddingValues(),
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
            .padding(horizontal = Spacing.Gutter)
            .testTag("search_discover"),
    ) {
        item(key = "title", span = { GridItemSpan(maxLineSpan) }, contentType = "title") {
            Text(
                text = stringResource(R.string.er_browse),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = Spacing.L, bottom = Spacing.M),
            )
        }
        item(key = "field", span = { GridItemSpan(maxLineSpan) }, contentType = "field") {
            // A white field: on the dark theme it is the bright spot of the page, on the light
            // theme a white card raised on the aqua page; the text is dark on both.
            val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
            val fieldColor = if (dark) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerHigh
            val fieldContent = if (dark) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Size.IconButton)
                    .shadow(if (dark) 0.dp else Spacing.XS, ErShape.Small, ambientColor = MaterialTheme.colorScheme.primary, spotColor = MaterialTheme.colorScheme.primary)
                    .clip(ErShape.Small)
                    .background(fieldColor)
                    .clickable(onClick = onFocusSearch)
                    .padding(start = Spacing.M),
            ) {
                Icon(
                    painterResource(R.drawable.search),
                    contentDescription = null,
                    tint = fieldContent,
                )
                Spacer(Modifier.width(Spacing.M))
                Text(
                    text = stringResource(R.string.er_search_hint),
                    style = MaterialTheme.typography.titleSmall,
                    color = fieldContent.copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f),
                )
                if (onVoiceSearch != null) {
                    IconButton(onClick = onVoiceSearch) {
                        Icon(
                            painterResource(R.drawable.mic),
                            contentDescription = stringResource(R.string.er_voice_search),
                            tint = fieldContent,
                        )
                    }
                }
                // Name the song playing nearby (Shazam); the mic beside it searches by spoken words.
                if (onRecognize != null) {
                    IconButton(onClick = onRecognize) {
                        Icon(
                            painterResource(R.drawable.graphic_eq),
                            contentDescription = stringResource(R.string.er_recognize_song),
                            tint = fieldContent,
                        )
                    }
                }
            }
        }

        shelves.forEachIndexed { index, shelf ->
            item(key = "shelf_$index", span = { GridItemSpan(maxLineSpan) }, contentType = "shelf") {
                Column {
                    SectionHeader(
                        title = shelf.title.ifEmpty { stringResource(R.string.er_new_releases) },
                        modifier = Modifier.padding(top = Spacing.L),
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                        items(shelf.items, key = { "${it::class.simpleName}_${it.id}" }, contentType = { it::class.simpleName }) { item ->
                            YouTubeGridItem(
                                item = item,
                                modifier = Modifier.width(Size.CardThumb).itemClicks(actions, item),
                            )
                        }
                    }
                }
            }
        }

        val loaded = sections
        when {
            loaded == null ->
                item(key = "loading", span = { GridItemSpan(maxLineSpan) }, contentType = "loading") {
                    ShimmerHost(showGradient = false) {
                        repeat(3) {
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), modifier = Modifier.padding(top = Spacing.S)) {
                                SkeletonBlock(Size.CardThumb + Spacing.L, (Size.CardThumb + Spacing.L) / CARD_ASPECT)
                                SkeletonBlock(Size.CardThumb + Spacing.L, (Size.CardThumb + Spacing.L) / CARD_ASPECT)
                            }
                        }
                    }
                }

            loaded.isEmpty() ->
                item(key = "error", span = { GridItemSpan(maxLineSpan) }, contentType = "error") {
                    ErrorState(onRetry = viewModel::reload)
                }

            else ->
                loaded.forEachIndexed { index, section ->
                    item(key = "header_$index", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                        Text(
                            text = section.title,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = Spacing.XL, bottom = Spacing.XS),
                        )
                    }
                    items(section.items, key = { "${index}_${it.endpoint.browseId}_${it.endpoint.params}" }, contentType = { "genre" }) { genre ->
                        val coverKey = "${genre.endpoint.browseId}_${genre.endpoint.params}"
                        LaunchedEffect(coverKey) { viewModel.loadCover(coverKey, genre.endpoint.browseId, genre.endpoint.params) }
                        GenreCard(
                            title = genre.title,
                            color = Color(genre.stripeColor),
                            cover = viewModel.covers[coverKey],
                            onClick = {
                                navController.navigate("youtube_browse/${genre.endpoint.browseId}?params=${genre.endpoint.params}")
                            },
                        )
                    }
                }
        }
    }
}

@Composable
private fun GenreCard(
    title: String,
    color: Color,
    cover: String?,
    onClick: () -> Unit,
) {
    val raw = color.copy(alpha = 1f).compositeOver(Color.Black)
    // Light colors (pastel yellow, mint) get pulled down until white text has contrast.
    val base = lerp(raw, Color.Black, ((raw.luminance() - 0.18f) * 1.6f).coerceIn(0f, 0.55f))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height((Size.CardThumb + Spacing.L) / CARD_ASPECT)
            .clip(ErShape.Small)
            .background(Brush.linearGradient(listOf(base, base.copy(alpha = 0.7f).compositeOver(Color.Black))))
            .clickable(onClick = onClick),
    ) {
        // Tilted cover tucked into the corner, the way Spotify's browse cards show what is inside.
        if (!cover.isNullOrEmpty()) {
            AsyncImage(
                model = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = Spacing.M, y = Spacing.XS)
                    .size(Size.ListThumb + Spacing.L)
                    .graphicsLayer { rotationZ = 22f }
                    .shadow(Spacing.S, ErShape.Small)
                    .clip(ErShape.Small),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(Spacing.M)
                .fillMaxWidth(0.72f),
        )
    }
}
