/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import com.metrolist.music.ui.theme.WindowLayout
import androidx.compose.ui.graphics.Brush
import com.metrolist.music.ui.theme.ErShape
import androidx.compose.ui.draw.clip
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import com.metrolist.music.ui.screens.Screens
import com.metrolist.music.ui.theme.ErMusicTheme
import com.metrolist.music.ui.theme.Motion
import com.metrolist.music.ui.theme.Size
import com.metrolist.music.ui.theme.Spacing

private const val NAV_PILL_ALPHA = 0.16f
private const val NAV_FADE_START = 0.12f
private const val NAV_FADE_MIDDLE_ALPHA = 0.9f
private const val NAV_FADE_SOLID = 0.3f
private const val NAV_FADE_END_ALPHA = 1f

@Stable
fun isRouteSelected(
    currentRoute: String?,
    screenRoute: String,
): Boolean {
    if (currentRoute == null) return false
    if (currentRoute == screenRoute || currentRoute.startsWith("$screenRoute/")) return true
    return screenRoute == Screens.Search.route && currentRoute.startsWith("search/")
}

@Composable
private fun NavItem(
    screen: Screens,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val content by animateColorAsState(if (selected) colors.onSurface else colors.onSurfaceVariant, Motion.fast(), label = "navContent")
    val icon by animateColorAsState(if (selected) colors.primary else colors.onSurfaceVariant, Motion.fast(), label = "navIcon")
    val pill by animateColorAsState(
        if (selected) colors.primary.copy(alpha = NAV_PILL_ALPHA) else colors.primary.copy(alpha = 0f),
        Motion.fast(),
        label = "navPill",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .testTag("nav_${screen.route}")
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            ),
    ) {
        // The current tab sits in a teal pill, its icon filled and teal.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(width = Size.NavPillWidth, height = Size.NavPillHeight)
                .clip(ErShape.Pill)
                .background(pill),
        ) {
            Icon(
                painter = painterResource(if (selected) screen.iconIdActive else screen.iconIdInactive),
                contentDescription = null,
                tint = icon,
                modifier = Modifier.size(Size.Icon),
            )
        }
        Spacer(Modifier.height(Spacing.XXS))
        Text(
            text = stringResource(screen.titleId),
            style = MaterialTheme.typography.labelSmall,
            color = content,
        )
    }
}

@Composable
fun AppNavigationBar(
    navigationItems: List<Screens>,
    currentRoute: String?,
    onItemClick: (Screens, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
) {
    // See-through, like Spotify: the page scrolls on under the bar and fades into it, so the
    // bar never cuts the content off with a hard edge.
    val base = if (pureBlack) Color.Black else MaterialTheme.colorScheme.background
    val fade = remember(base) {
        // Clear only along the top edge; under the icons and labels it is nearly solid, so text
        // scrolling past never fights with the tab names.
        Brush.verticalGradient(
            0f to base.copy(alpha = 0f),
            NAV_FADE_START to base.copy(alpha = NAV_FADE_MIDDLE_ALPHA),
            NAV_FADE_SOLID to base.copy(alpha = NAV_FADE_END_ALPHA),
            1f to base.copy(alpha = NAV_FADE_END_ALPHA),
        )
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(fade),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(Size.NavBar),
        ) {
            navigationItems.forEach { screen ->
                val selected = isRouteSelected(currentRoute, screen.route)
                NavItem(
                    screen = screen,
                    selected = selected,
                    onClick = { onItemClick(screen, selected) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
fun AppNavigationRail(
    navigationItems: List<Screens>,
    currentRoute: String?,
    onItemClick: (Screens, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.L, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxHeight()
            .width(WindowLayout.RailWidth)
            // Clear over the backdrop, like the pages beside it.
            .then(if (pureBlack) Modifier.background(Color.Black) else Modifier)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        navigationItems.forEach { screen ->
            val selected = isRouteSelected(currentRoute, screen.route)
            NavItem(screen = screen, selected = selected, onClick = { onItemClick(screen, selected) })
        }
    }
}

@Preview(name = "Nav light")
@Composable
private fun NavPreviewLight() {
    ErMusicTheme(darkTheme = false) {
        AppNavigationBar(Screens.MainScreens, Screens.Home.route, { _, _ -> })
    }
}

@Preview(name = "Nav dark")
@Composable
private fun NavPreviewDark() {
    ErMusicTheme(darkTheme = true) {
        AppNavigationBar(Screens.MainScreens, Screens.Library.route, { _, _ -> })
    }
}
