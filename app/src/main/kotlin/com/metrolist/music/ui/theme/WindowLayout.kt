/**
 * Sonara (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How much room the window gives, for layouts that change on tablets. Phones are always
 * [WindowWidth.Compact] and portrait (MainActivity locks them). A tablet held upright works like
 * a phone (bottom navigation, the phone player); only a tablet in landscape
 * ([tabletLandscape]) gets the rail, centred pages, the side-panel player and two-pane pages.
 * Breakpoints follow Material's window size classes (600dp and 840dp).
 */
@Immutable
data class WindowLayout(
    val width: WindowWidth,
    val landscape: Boolean,
    val widthDp: Dp,
    val heightDp: Dp,
) {
    /** Tablet-sized: the player shows lyrics and the queue in a side panel, not in sheets. */
    val wide: Boolean get() = width != WindowWidth.Compact

    /** A tablet in landscape: the layouts made for it switch on; upright it is a big phone. */
    val tabletLandscape: Boolean get() = wide && landscape

    /** Room for two panes side by side (album and playlist pages). */
    val twoPane: Boolean get() = tabletLandscape

    /** Lyrics grow with the screen: bigger on tablets, a little more in landscape. */
    val lyricsScale: Float
        get() =
            when (width) {
                WindowWidth.Compact -> 1f
                WindowWidth.Medium -> 1.2f
                WindowWidth.Expanded -> 1.3f
            }

    companion object {
        /** Smallest screen width from which a device counts as a tablet (and may rotate). */
        const val TABLET_SMALLEST_WIDTH_DP = 600

        /** Widest a page's content grows; past this it is centred on the backdrop. */
        val ContentMaxWidth = 1040.dp

        /** Widest a column of text (lyrics, settings, a track list) reads well. */
        val ReadingMaxWidth = 720.dp

        /** The cover and controls pane of a two-pane page or the player. */
        val SidePaneWidth = 360.dp

        /** The floating mini player's widest size on tablets. */
        val MiniPlayerMaxWidth = 640.dp

        /** The navigation rail beside the pages on tablets. */
        val RailWidth: Dp get() = Size.NavBar + Spacing.S
    }
}

enum class WindowWidth { Compact, Medium, Expanded }

@Composable
fun rememberWindowLayout(): WindowLayout {
    val size = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    return remember(size, density) {
        val width = with(density) { size.width.toDp() }
        val height = with(density) { size.height.toDp() }
        WindowLayout(
            width =
                when {
                    width < 600.dp -> WindowWidth.Compact
                    width < 840.dp -> WindowWidth.Medium
                    else -> WindowWidth.Expanded
                },
            landscape = width > height,
            widthDp = width,
            heightDp = height,
        )
    }
}
