/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette

object PlayerColorExtractor {
    fun extractGradientColors(
        palette: Palette,
        fallbackColor: Int,
    ): List<Color> {
        val primaryColor = Color(palette.rankedColors(1, fallbackColor).first())
        return listOf(
            primaryColor,
            primaryColor.copy(
                red = primaryColor.red * 0.6f,
                green = primaryColor.green * 0.6f,
                blue = primaryColor.blue * 0.6f,
            ),
            Color.Black,
        )
    }

    /**
     * Accent color for player controls: the highest-scoring artwork color, with its lightness
     * clamped so it stays visible on both the dark scrim and as a button fill.
     */
    fun extractAccentColor(
        palette: Palette,
        fallbackColor: Int,
    ): Color {
        val argb = palette.rankedColors(1, fallbackColor).first()
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(argb, hsl)
        hsl[1] = hsl[1].coerceAtLeast(0.35f)
        hsl[2] = hsl[2].coerceIn(0.5f, 0.72f)
        return Color(ColorUtils.HSLToColor(hsl))
    }
}
