/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens

import com.metrolist.music.ui.theme.topBarBackdrop
import com.metrolist.music.ui.theme.backdropTopBarColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.SectionHeader
import com.metrolist.music.ui.component.SkeletonBlock
import com.metrolist.music.ui.component.YouTubeGridItem
import com.metrolist.music.ui.component.shimmer.ShimmerHost
import com.metrolist.music.ui.theme.Size
import com.metrolist.music.ui.theme.Spacing
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.viewmodels.YouTubeBrowseViewModel

/**
 * A mood or genre page from Search, laid out like YouTube Music: each section is a titled
 * shelf of evenly spaced cards that scrolls sideways, instead of every item packed into one grid.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeBrowseScreen(
    navController: NavController,
    viewModel: YouTubeBrowseViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val browseResult by viewModel.result.collectAsStateWithLifecycle()
    val actions = rememberItemActions()
    val cardWidth = Size.CardThumb

    LazyColumn(
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        modifier = Modifier.fillMaxSize(),
    ) {
        val result = browseResult
        if (result == null) {
            items(3, key = { "skeleton_$it" }, contentType = { "skeleton" }) {
                ShimmerHost(showGradient = false) {
                    Column(Modifier.padding(horizontal = Spacing.Gutter, vertical = Spacing.L)) {
                        SkeletonBlock(cardWidth, Spacing.L)
                        Spacer(Modifier.height(Spacing.M))
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
                            repeat(3) { SkeletonBlock(cardWidth, cardWidth) }
                        }
                    }
                }
            }
        } else {
            itemsIndexed(
                items = result.items.filter { it.items.isNotEmpty() },
                key = { index, section -> "section_${index}_${section.title}" },
                contentType = { _, _ -> "shelf" },
            ) { _, section ->
                Column(Modifier.padding(bottom = Spacing.S)) {
                    section.title?.let { SectionHeader(title = it, modifier = Modifier.padding(top = Spacing.S)) }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = Spacing.Gutter - Spacing.XS),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.XS),
                    ) {
                        items(section.items.distinctBy { it.id }, key = { it.id }, contentType = { it::class.simpleName }) { item ->
                            YouTubeGridItem(
                                item = item,
                                isActive = item.isCurrent(mediaMetadata?.id, mediaMetadata?.album?.id),
                                isPlaying = isPlaying,
                                modifier = Modifier.width(cardWidth).itemClicks(actions, item),
                            )
                        }
                    }
                }
            }
        }
    }

    TopAppBar(
        colors = backdropTopBarColors(),
        modifier = Modifier.topBarBackdrop(),
        title = { Text(browseResult?.title.orEmpty()) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(painterResource(R.drawable.arrow_back), contentDescription = stringResource(R.string.er_back))
            }
        },
    )
}
