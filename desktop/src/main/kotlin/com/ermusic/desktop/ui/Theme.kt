/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ermusic.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.draw.clip
import com.ermusic.desktop.data.Settings
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Sonar Night, the same tokens as the Android app (app/.../ui/theme).
private val Accent = Color(0xFF2EE6C5)

object Spacing {
    val XXS = 2.dp
    val XS = 4.dp
    val S = 8.dp
    val M = 12.dp
    val L = 16.dp
    val XL = 24.dp
    val XXL = 32.dp
    val XXXL = 48.dp
}

object Size {
    val ListRow = 64.dp
    val ListThumb = 48.dp
    val Card = 168.dp
    val Icon = 24.dp
    val IconSmall = 20.dp
    val IconButton = 48.dp
    val PlayButton = 72.dp
    val MiniPlayer = 72.dp
    val MiniPlayerThumb = 48.dp
    val TitleBar = 52.dp
    val TitleLogo = 26.dp

    /** Room on the left of a Mac's top bar for its close, minimize and zoom buttons. */
    val MacWindowButtons = 80.dp
    val Control = 36.dp
    val Sidebar = 264.dp
    val PlayerBar = 92.dp
    val Queue = 350.dp
    val Logo = 40.dp
}

object Layout {
    val ContentMaxWidth = 1040.dp
    val ReadingMaxWidth = 720.dp
    val SidePane = 360.dp
    val MiniPlayerMaxWidth = 640.dp
}

object ErShape {
    val Small = RoundedCornerShape(10.dp)
    val Medium = RoundedCornerShape(16.dp)
    val Large = RoundedCornerShape(22.dp)
    val Pill = RoundedCornerShape(percent = 50)
}

private val Jakarta =
    FontFamily(
        Font("font/jakarta_regular.ttf", FontWeight.Normal),
        Font("font/jakarta_medium.ttf", FontWeight.Medium),
        Font("font/jakarta_semibold.ttf", FontWeight.SemiBold),
        Font("font/jakarta_bold.ttf", FontWeight.Bold),
        Font("font/jakarta_extrabold.ttf", FontWeight.ExtraBold),
    )

private fun style(size: Int, line: Int, weight: FontWeight) =
    TextStyle(fontFamily = Jakarta, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)

private val Type =
    Typography(
        displaySmall = style(30, 38, FontWeight.ExtraBold),
        headlineLarge = style(28, 36, FontWeight.ExtraBold),
        headlineMedium = style(24, 32, FontWeight.ExtraBold),
        headlineSmall = style(20, 28, FontWeight.Bold),
        titleLarge = style(18, 26, FontWeight.Bold),
        titleMedium = style(15, 22, FontWeight.SemiBold),
        titleSmall = style(14, 20, FontWeight.SemiBold),
        bodyLarge = style(15, 22, FontWeight.Normal),
        bodyMedium = style(14, 20, FontWeight.Normal),
        bodySmall = style(12, 16, FontWeight.Normal),
        labelLarge = style(14, 20, FontWeight.SemiBold),
        labelMedium = style(12, 16, FontWeight.Medium),
        labelSmall = style(11, 14, FontWeight.SemiBold),
    )

private val DarkColors =
    darkColorScheme(
        primary = Accent,
        onPrimary = Color(0xFF04201B),
        primaryContainer = Color(0xFF0E3A33),
        onPrimaryContainer = Color(0xFFB8F5E8),
        secondary = Accent,
        onSecondary = Color(0xFF04201B),
        background = Color(0xFF000000),
        onBackground = Color(0xFFF3F5F7),
        surface = Color(0xFF131518),
        onSurface = Color(0xFFF3F5F7),
        surfaceVariant = Color(0xFF24282D),
        onSurfaceVariant = Color(0xFF9AA1AC),
        surfaceContainerLow = Color(0xFF101215),
        surfaceContainer = Color(0xFF131518),
        surfaceContainerHigh = Color(0xFF1B1E22),
        surfaceContainerHighest = Color(0xFF24282D),
        outline = Color(0xFF4A5059),
        outlineVariant = Color(0xFF2A2E34),
        error = Color(0xFFFF6B6B),
        onError = Color(0xFF2B0606),
    )

private val LightColors =
    lightColorScheme(
        primary = Color(0xFF007C85),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFC9F1EC),
        onPrimaryContainer = Color(0xFF00363B),
        secondary = Color(0xFF007C85),
        onSecondary = Color.White,
        background = Color(0xFFD9E9E7),
        onBackground = Color(0xFF0A1B1A),
        surface = Color(0xFFF7FBFB),
        onSurface = Color(0xFF0A1B1A),
        surfaceVariant = Color(0xFFD9E9E7),
        onSurfaceVariant = Color(0xFF4E6765),
        surfaceContainerLow = Color(0xFFF1F8F7),
        surfaceContainer = Color(0xFFF7FBFB),
        surfaceContainerHigh = Color(0xFFE7F3F2),
        surfaceContainerHighest = Color(0xFFF9FDFC),
        outline = Color(0xFF8AA19F),
        outlineVariant = Color(0xFFCFE1DF),
        error = Color(0xFFC62828),
        onError = Color.White,
    )

@Composable
fun SonaraTheme(forceDark: Boolean = false, content: @Composable () -> Unit) {
    val dark =
        forceDark ||
            when (Settings.theme.value) {
                "light" -> false
                "system" -> isSystemInDarkTheme()
                else -> true
            }
    val colors = if (dark) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = Type,
        shapes = Shapes(small = ErShape.Small, medium = ErShape.Medium, large = ErShape.Large),
    ) {
        // Screens sit straight on the backdrop, with no Surface to set the text colour.
        CompositionLocalProvider(LocalContentColor provides colors.onSurface, content = content)
    }
}

/** A panel of the window, with a faint glow of the accent falling from its top. */
@Composable
fun Modifier.appBackdrop(): Modifier {
    val colors = MaterialTheme.colorScheme
    return background(colors.surfaceContainerLow).background(
        Brush.radialGradient(
            listOf(colors.primary.copy(alpha = 0.10f), Color.Transparent),
            center = Offset(GLOW_CENTER_X, 0f),
            radius = GLOW_RADIUS,
        ),
    )
}

/** A panel of the window without the glow. */
@Composable
fun Modifier.panel(): Modifier = clip(ErShape.Medium).background(MaterialTheme.colorScheme.surfaceContainerLow)

private const val GLOW_CENTER_X = 700f
private const val GLOW_RADIUS = 1100f
