/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import com.metrolist.music.LocalIsOnline
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.metrolist.music.R
import com.metrolist.music.ui.component.shimmer.ShimmerHost
import com.metrolist.music.ui.theme.ErMusicTheme
import com.metrolist.music.ui.theme.ErShape
import com.metrolist.music.ui.theme.Size
import com.metrolist.music.ui.theme.Spacing

/** Skeleton block used by every loading state. */
@Composable
fun SkeletonBlock(
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    circle: Boolean = false,
) {
    Box(
        modifier
            .size(width, height)
            .clip(if (circle) CircleShape else ErShape.Small)
            .background(MaterialTheme.colorScheme.onSurface),
    )
}

/** One skeleton row matching the 64dp list item. */
@Composable
fun SkeletonListRow(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(Size.ListRow)
            .padding(horizontal = Spacing.Gutter),
    ) {
        SkeletonBlock(Size.ListThumb, Size.ListThumb)
        Spacer(Modifier.width(Spacing.M))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            SkeletonBlock(Spacing.XXXL * 4, Spacing.M)
            SkeletonBlock(Spacing.XXXL * 2, Spacing.M)
        }
    }
}

/** Loading state: a shimmering list skeleton. */
@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    rows: Int = 8,
) {
    ShimmerHost(modifier = modifier.fillMaxWidth()) {
        Spacer(Modifier.height(Spacing.S))
        repeat(rows) { SkeletonListRow() }
    }
}

/** Loading state for a horizontal carousel section. */
@Composable
fun SkeletonCarousel(modifier: Modifier = Modifier) {
    ShimmerHost(modifier = modifier.fillMaxWidth(), showGradient = false) {
        SkeletonBlock(Spacing.XXXL * 3, Spacing.L, Modifier.padding(start = Spacing.Gutter, top = Spacing.L))
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.M),
            modifier = Modifier.padding(horizontal = Spacing.Gutter, vertical = Spacing.M),
        ) {
            repeat(3) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    SkeletonBlock(Size.CardThumb, Size.CardThumb)
                    SkeletonBlock(Size.CardThumb * 0.8f, Spacing.M)
                    SkeletonBlock(Size.CardThumb * 0.5f, Spacing.M)
                }
            }
        }
    }
}

@Composable
private fun StateMessage(
    @DrawableRes icon: Int,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.XXL, vertical = Spacing.XXXL),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(Size.PlayButton)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Size.Icon + Spacing.S),
            )
        }
        Spacer(Modifier.height(Spacing.L))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (message != null) {
            Spacer(Modifier.height(Spacing.XS))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) {
            Spacer(Modifier.height(Spacing.L))
            action()
        }
    }
}

@Composable
fun EmptyState(
    @DrawableRes icon: Int,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) = StateMessage(
    icon = icon,
    title = title,
    message = message,
    modifier = modifier,
    action = if (actionLabel != null && onAction != null) {
        { SecondaryButton(text = actionLabel, onClick = onAction) }
    } else {
        null
    },
)

@Composable
fun ErrorState(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
) {
    // Without a connection the honest message is "You're offline", not a generic error.
    val offline = !LocalIsOnline.current
    StateMessage(
        icon = if (offline) R.drawable.offline else R.drawable.error,
        title = stringResource(if (offline) R.string.er_offline_title else R.string.er_error_title),
        message = if (offline) stringResource(R.string.er_offline_message) else message ?: stringResource(R.string.er_error_message),
        modifier = modifier,
        action = { PrimaryButton(text = stringResource(R.string.retry), icon = R.drawable.refresh, onClick = onRetry) },
    )
}

@Composable
fun OfflineState(
    onOpenDownloads: () -> Unit,
    modifier: Modifier = Modifier,
) = StateMessage(
    icon = R.drawable.offline,
    title = stringResource(R.string.er_offline_title),
    message = stringResource(R.string.er_offline_message),
    modifier = modifier,
    action = {
        PrimaryButton(
            text = stringResource(R.string.er_go_to_downloads),
            icon = R.drawable.download,
            onClick = onOpenDownloads,
        )
    },
)

/** Centers a state message in the remaining space of a screen. */
@Composable
fun FullScreenState(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Preview(name = "States light")
@Composable
private fun StatesPreviewLight() {
    ErMusicTheme(darkTheme = false) { StatesPreviewContent() }
}

@Preview(name = "States dark")
@Composable
private fun StatesPreviewDark() {
    ErMusicTheme(darkTheme = true) { StatesPreviewContent() }
}

@Composable
private fun StatesPreviewContent() {
    Column(Modifier.background(MaterialTheme.colorScheme.background)) {
        LoadingState(rows = 2)
        EmptyState(icon = R.drawable.music_note, title = "Nothing here yet", message = "Songs you like show up here")
        ErrorState(onRetry = {})
        OfflineState(onOpenDownloads = {})
    }
}
