/**
 * Sonara (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.theme

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalWindowInfo

/**
 * The page behind every screen. Sonar Day: the aqua deepens to teal at the top and a blue-teal
 * light falls from the top corner, so the page has depth instead of being one flat tint. Sonar
 * Night: the near-black with a faint teal glow at the top. Screens are transparent over it.
 *
 * It is laid out against the whole window, wherever the element sits: a top bar, a sticky header
 * or a page that starts below a search field all paint their own slice of the same gradient, so
 * an opaque layer still matches the page around it with no seam.
 */
@Composable
fun Modifier.appBackdrop(pureBlack: Boolean = false): Modifier {
    val paint = rememberBackdropPaint(pureBlack)
    val window = LocalWindowInfo.current.containerSize
    var origin by remember { mutableStateOf(Offset.Zero) }
    return this
        .onGloballyPositioned { origin = it.positionInWindow() }
        .drawBehind { drawBackdrop(paint, window.width.toFloat(), window.height.toFloat(), origin) }
}

/** The backdrop's top slice, for a top bar at the top of the window. */
@Composable
fun Modifier.topBarBackdrop(pureBlack: Boolean = false): Modifier = appBackdrop(pureBlack)

/** Top bar colours for bars drawn over [topBarBackdrop]: clear, scrolled or not. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun backdropTopBarColors(): TopAppBarColors =
    TopAppBarDefaults.topAppBarColors(
        containerColor = Color.Transparent,
        scrolledContainerColor = Color.Transparent,
    )

@Immutable
private class BackdropPaint(
    val flat: Color?,
    val wash: List<Pair<Float, Color>>,
    val light: Color,
)

@Composable
private fun rememberBackdropPaint(pureBlack: Boolean): BackdropPaint {
    val base = MaterialTheme.colorScheme.background
    return remember(base, pureBlack) {
        val dark = base.luminance() < 0.5f
        when {
            pureBlack -> BackdropPaint(Color.Black, emptyList(), Color.Transparent)
            dark -> BackdropPaint(base, emptyList(), NightGlow)
            else -> BackdropPaint(null, listOf(0f to DayTop, DAY_MIDDLE_STOP to DayMiddle, 1f to base), DayLight)
        }
    }
}

private fun DrawScope.drawBackdrop(
    paint: BackdropPaint,
    windowWidth: Float,
    windowHeight: Float,
    origin: Offset,
) {
    val top = origin.y
    val height = if (windowHeight > 0f) windowHeight else size.height
    val width = if (windowWidth > 0f) windowWidth else size.width
    if (paint.flat != null) {
        drawRect(paint.flat)
    } else {
        drawRect(Brush.verticalGradient(*paint.wash.toTypedArray(), startY = -top, endY = height - top))
    }
    if (paint.light != Color.Transparent) {
        drawRect(
            Brush.radialGradient(
                0f to paint.light,
                1f to Color.Transparent,
                center = Offset(width * LIGHT_X - origin.x, -top),
                radius = width * LIGHT_RADIUS,
            ),
        )
    }
}

private const val DAY_MIDDLE_STOP = 0.38f
private const val LIGHT_X = 0.85f
private const val LIGHT_RADIUS = 1.1f
private val DayTop = Color(0xFFC6EAE4)
private val DayMiddle = Color(0xFFDDF0ED)
private val DayLight = Color(0x99B9DDF0)
private val NightGlow = Color(0x1F2EE6C5)
