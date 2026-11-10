/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.player

import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.scale
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Size as CoilSize
import coil3.toBitmap
import com.metrolist.music.ui.theme.Ember
import com.metrolist.music.ui.theme.Motion
import com.metrolist.music.ui.theme.PlayerColorExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Blurred artwork plus the accent color extracted from it. Computed once per track. */
@Immutable
data class PlayerArtwork(
    val url: String,
    val blurred: ImageBitmap,
    val accent: Color,
)

private const val SourceSize = 96
private const val BlurRadius = 6

private object ArtworkCache {
    val entries = LruCache<String, PlayerArtwork>(12)
}

/** Loads, downsamples and blurs the artwork on a background thread. Cached per URL. */
suspend fun loadPlayerArtwork(
    context: Context,
    url: String,
): PlayerArtwork? {
    ArtworkCache.entries.get(url)?.let { return it }
    return withContext(Dispatchers.Default) {
        val result = context.imageLoader.execute(
            ImageRequest.Builder(context)
                .data(url)
                .size(CoilSize(SourceSize, SourceSize))
                .allowHardware(false)
                .build(),
        )
        val source = result.image?.toBitmap() ?: return@withContext null
        val small = if (source.width > SourceSize || source.height > SourceSize) {
            source.scale(SourceSize, SourceSize)
        } else {
            source.copy(Bitmap.Config.ARGB_8888, true)
        }
        val accent = runCatching {
            PlayerColorExtractor.extractAccentColor(Palette.from(small).maximumColorCount(16).generate(), Ember.toArgb())
        }.getOrDefault(Ember)
        val blurred = boxBlur(small, BlurRadius)
        PlayerArtwork(url, blurred.asImageBitmap(), accent).also { ArtworkCache.entries.put(url, it) }
    }
}

@Composable
fun rememberPlayerArtwork(url: String?): PlayerArtwork? {
    val context = LocalContext.current
    val artwork by produceState(initialValue = url?.let { ArtworkCache.entries.get(it) }, url) {
        value = url?.let { runCatching { loadPlayerArtwork(context, it) }.getOrNull() }
    }
    return artwork
}

/**
 * The player's background: the theme's base lit from the top by a glow in the cover's colour,
 * over a faint wash of the blurred cover. Track changes crossfade over 400ms; drawing costs one
 * scaled bitmap and two gradients per frame.
 */
@Composable
fun ArtworkBackground(
    artwork: PlayerArtwork?,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
) {
    val base = if (pureBlack) Color.Black else MaterialTheme.colorScheme.background
    val dark = base.luminance() < 0.5f
    Box(modifier.fillMaxSize().background(base)) {
        Crossfade(
            targetState = artwork,
            animationSpec = tween(Motion.ArtworkCrossfade),
            label = "artworkBackground",
        ) { art ->
            if (art != null) {
                Box(Modifier.fillMaxSize()) {
                    Image(
                        bitmap = art.blurred,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alpha = if (dark) BLUR_WASH_DARK else BLUR_WASH_LIGHT,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .drawBehind {
                                drawRect(
                                    Brush.radialGradient(
                                        0f to art.accent.copy(alpha = if (dark) GLOW_DARK else GLOW_LIGHT),
                                        0.5f to art.accent.copy(alpha = if (dark) GLOW_DARK * 0.4f else GLOW_LIGHT * 0.4f),
                                        1f to Color.Transparent,
                                        center = Offset(size.width / 2f, 0f),
                                        radius = size.maxDimension * 0.85f,
                                    ),
                                )
                            },
                    )
                }
            }
        }
        // Fades into the base towards the controls, so they always sit on the theme's colour.
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to base.copy(alpha = 0.92f))),
        )
    }
}

private const val GLOW_DARK = 0.7f
private const val GLOW_LIGHT = 0.5f
private const val BLUR_WASH_DARK = 0.22f
private const val BLUR_WASH_LIGHT = 0.18f

/** Three-pass box blur (approximates a gaussian). Runs on a ~96px bitmap, so it is cheap. */
private fun boxBlur(
    src: Bitmap,
    radius: Int,
): Bitmap {
    val w = src.width
    val h = src.height
    val pixels = IntArray(w * h)
    src.getPixels(pixels, 0, w, 0, 0, w, h)
    val tmp = IntArray(w * h)
    repeat(3) {
        blurPass(pixels, tmp, w, h, radius, horizontal = true)
        blurPass(tmp, pixels, w, h, radius, horizontal = false)
    }
    return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
}

private fun blurPass(
    input: IntArray,
    output: IntArray,
    w: Int,
    h: Int,
    r: Int,
    horizontal: Boolean,
) {
    val lines = if (horizontal) h else w
    val len = if (horizontal) w else h
    val window = r * 2 + 1
    for (line in 0 until lines) {
        var sr = 0
        var sg = 0
        var sb = 0
        fun idx(i: Int): Int {
            val c = i.coerceIn(0, len - 1)
            return if (horizontal) line * w + c else c * w + line
        }
        for (i in -r..r) {
            val p = input[idx(i)]
            sr += (p shr 16) and 0xFF
            sg += (p shr 8) and 0xFF
            sb += p and 0xFF
        }
        for (i in 0 until len) {
            output[idx(i)] = (0xFF shl 24) or ((sr / window) shl 16) or ((sg / window) shl 8) or (sb / window)
            val outP = input[idx(i - r)]
            val inP = input[idx(i + r + 1)]
            sr += ((inP shr 16) and 0xFF) - ((outP shr 16) and 0xFF)
            sg += ((inP shr 8) and 0xFF) - ((outP shr 8) and 0xFF)
            sb += (inP and 0xFF) - (outP and 0xFF)
        }
    }
}
