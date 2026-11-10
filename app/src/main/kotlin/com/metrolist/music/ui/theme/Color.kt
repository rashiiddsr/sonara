/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * SONARA is named after sonar: a pulse sent out that comes back as an echo. Its mark is a play
 * button sending out echoes, in sonar teal. Two themes: Sonar Night (neutral near-black) and
 * Sonar Day (a bright aqua, never plain white), both with one teal accent.
 */
val BrandStart = Color(0xFF2EE6C5)
val BrandEnd = Color(0xFF1FB2E6)
/** Accent on the dark theme (bright teal, dark text on it). */
val Ember = Color(0xFF2EE6C5)
/** Accent on the light theme (deep blue-teal, white text on it). */
val EmberDeep = Color(0xFF007C85)

/**
 * Covers of the automatic playlists in Library, gradients as in YouTube Music (Liked music pink to
 * violet, New episodes peach to lilac, ...), so they read apart from the playlists with artwork.
 */
object AutoPlaylistGradients {
    val Liked = listOf(Color(0xFFF26DB5), Color(0xFF9F6CF2))
    val Downloaded = listOf(Color(0xFF1FB89C), Color(0xFF3A7BD5))
    val NewEpisodes = listOf(Color(0xFFF7A072), Color(0xFFB98BF2))
    val EpisodesForLater = listOf(Color(0xFFC9A5F5), Color(0xFF8C9DF2))
    val Top = listOf(Color(0xFFF5C451), Color(0xFFF2766B))
    val Other = listOf(Color(0xFF6C7A99), Color(0xFF3F4A66))
}

@Immutable
data class ErPalette(
    val background: Color,
    val surfaceLow: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val surfaceHighest: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
    val accent: Color,
    val onAccent: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
    val error: Color,
    val onError: Color,
)

/** Sonar Night: neutral near-black, no colour cast, so the covers and the teal carry it. */
val DarkPalette =
    ErPalette(
        background = Color(0xFF0A0B0D),
        surfaceLow = Color(0xFF101215),
        surface = Color(0xFF131518),
        surfaceHigh = Color(0xFF1B1E22),
        surfaceHighest = Color(0xFF24282D),
        onSurface = Color(0xFFF3F5F7),
        onSurfaceVariant = Color(0xFF9AA1AC),
        outline = Color(0xFF4A5059),
        outlineVariant = Color(0xFF2A2E34),
        accent = Ember,
        onAccent = Color(0xFF04201B),
        accentContainer = Color(0xFF0E3A33),
        onAccentContainer = Color(0xFFB8F5E8),
        error = Color(0xFFFF6B6B),
        onError = Color(0xFF2B0606),
    )

/**
 * Sonar Day: a bright aqua page (never plain white) with white surfaces raised on it, like paper
 * on glass: chips, cards, the mini player and the search field are white, the page is tinted.
 * Ink text, and the teal a shade deeper and bluer so white text reads on it.
 */
val LightPalette =
    ErPalette(
        background = Color(0xFFE7F3F2),
        surfaceLow = Color(0xFFF1F8F7),
        surface = Color(0xFFF7FBFB),
        surfaceHigh = Color(0xFFF9FDFC),
        surfaceHighest = Color(0xFFD9E9E7),
        onSurface = Color(0xFF0A1B1A),
        onSurfaceVariant = Color(0xFF4E6765),
        outline = Color(0xFF8AA19F),
        outlineVariant = Color(0xFFCFE1DF),
        accent = EmberDeep,
        onAccent = Color(0xFFFFFFFF),
        accentContainer = Color(0xFFC9F1EC),
        onAccentContainer = Color(0xFF00363B),
        error = Color(0xFFC62828),
        onError = Color(0xFFFFFFFF),
    )

fun ErPalette.toColorScheme(
    dark: Boolean,
    pureBlack: Boolean = false,
): ColorScheme {
    val bg = if (dark && pureBlack) Color.Black else background
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentContainer,
        onPrimaryContainer = onAccentContainer,
        inversePrimary = if (dark) EmberDeep else Ember,
        secondary = onSurfaceVariant,
        onSecondary = background,
        secondaryContainer = surfaceHigh,
        onSecondaryContainer = onSurface,
        tertiary = accent,
        onTertiary = onAccent,
        tertiaryContainer = accentContainer,
        onTertiaryContainer = onAccentContainer,
        background = bg,
        onBackground = onSurface,
        surface = bg,
        onSurface = onSurface,
        surfaceVariant = surfaceHigh,
        onSurfaceVariant = onSurfaceVariant,
        surfaceTint = Color.Transparent,
        inverseSurface = onSurface,
        inverseOnSurface = background,
        error = error,
        onError = onError,
        errorContainer = error.copy(alpha = 0.16f),
        onErrorContainer = error,
        outline = outline,
        outlineVariant = outlineVariant,
        scrim = Color.Black,
        surfaceBright = surfaceHighest,
        surfaceDim = bg,
        surfaceContainerLowest = bg,
        surfaceContainerLow = if (dark && pureBlack) Color.Black else surfaceLow,
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceHigh,
        surfaceContainerHighest = surfaceHighest,
    )
}
