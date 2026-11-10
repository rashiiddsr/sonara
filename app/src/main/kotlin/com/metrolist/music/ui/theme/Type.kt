/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import com.metrolist.music.R
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * One font family for everything: Plus Jakarta Sans (SIL OFL 1.1, by Tokotype of Jakarta; licence
 * in assets/licenses), bundled as static weights cut from its variable font (a variable font set
 * to a weight came out in its thinnest instance on the phone). Hierarchy comes from size and weight.
 */
val TextFont =
    FontFamily(
        Font(R.font.jakarta_regular, FontWeight.Normal),
        Font(R.font.jakarta_medium, FontWeight.Medium),
        Font(R.font.jakarta_semibold, FontWeight.SemiBold),
        Font(R.font.jakarta_bold, FontWeight.Bold),
        Font(R.font.jakarta_extrabold, FontWeight.ExtraBold),
    )

private fun display(
    size: Int,
    line: Int,
    tracking: Double = 0.0,
) = TextStyle(
    fontFamily = TextFont,
    fontWeight = FontWeight.ExtraBold,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.em,
)

private fun text(
    size: Int,
    line: Int,
    weight: FontWeight,
    tracking: Double = 0.0,
) = TextStyle(
    fontFamily = TextFont,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.em,
)

val ErTypography =
    Typography(
        displayLarge = display(40, 48, -0.02),
        displayMedium = display(34, 42, -0.02),
        displaySmall = display(30, 38, -0.015),
        headlineLarge = display(28, 36, -0.015),
        headlineMedium = display(24, 32, -0.01),
        headlineSmall = display(22, 28, -0.01),
        titleLarge = text(20, 28, FontWeight.ExtraBold, -0.01),
        titleMedium = text(16, 24, FontWeight.Bold),
        titleSmall = text(14, 20, FontWeight.Bold),
        bodyLarge = text(16, 24, FontWeight.Normal),
        bodyMedium = text(14, 20, FontWeight.Normal),
        bodySmall = text(12, 16, FontWeight.Normal, 0.01),
        labelLarge = text(14, 20, FontWeight.SemiBold),
        labelMedium = text(12, 16, FontWeight.SemiBold, 0.01),
        labelSmall = text(11, 16, FontWeight.SemiBold, 0.02),
    )
