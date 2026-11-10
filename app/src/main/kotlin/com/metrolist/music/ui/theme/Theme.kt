/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.theme

import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.ui.screens.settings.DarkMode
import com.metrolist.music.constants.DarkModeKey
import androidx.compose.runtime.getValue
import android.graphics.Bitmap
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import com.materialkolor.score.Score

/**
 * The theme picked in Settings: Sonar Night (dark) or Sonar Day (light), following the phone
 * unless the user chose one.
 */
@Composable
fun rememberUseDarkTheme(): Boolean {
    val mode by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
    val system = isSystemInDarkTheme()
    return if (mode == DarkMode.AUTO) system else mode == DarkMode.ON
}

/** Fallback accent used when no artwork color is available. */
val DefaultThemeColor = Ember

/**
 * SONARA theme: two fixed palettes (no wallpaper colors), Plus Jakarta Sans typography and shapes.
 */
@Composable
fun ErMusicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureBlack: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        remember(darkTheme, pureBlack) {
            (if (darkTheme) DarkPalette else LightPalette).toColorScheme(darkTheme, pureBlack)
        }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = ErTypography,
        shapes = ErShapes,
        content = content,
    )
}

fun Bitmap.extractThemeColor(): Color =
    Color(
        Palette
            .from(this)
            .maximumColorCount(8)
            .generate()
            .rankedColors(1, DefaultThemeColor.toArgb())
            .first(),
    )

internal fun Palette.rankedColors(
    desiredColorCount: Int,
    fallbackColor: Int,
): List<Int> =
    Score.score(
        swatches.associate { it.rgb to it.population },
        desiredColorCount,
        fallbackColor,
        true,
    )
