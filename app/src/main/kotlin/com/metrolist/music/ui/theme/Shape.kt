/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** The three corner radii used across the app: 10 (thumbnails) / 16 (cards) / 22dp (large art). */
object Radius {
    val Small = 10.dp
    val Medium = 16.dp
    val Large = 22.dp
}

object ErShape {
    val Small = RoundedCornerShape(Radius.Small)
    val Medium = RoundedCornerShape(Radius.Medium)
    val Large = RoundedCornerShape(Radius.Large)
    val SheetTop = RoundedCornerShape(topStart = Radius.Large, topEnd = Radius.Large)
    val Pill = RoundedCornerShape(percent = 50)
}

val ErShapes =
    Shapes(
        extraSmall = ErShape.Small,
        small = ErShape.Small,
        medium = ErShape.Medium,
        large = ErShape.Large,
        extraLarge = ErShape.Large,
    )
