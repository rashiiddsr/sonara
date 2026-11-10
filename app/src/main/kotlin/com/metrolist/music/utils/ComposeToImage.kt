/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import androidx.core.content.res.ResourcesCompat
import kotlin.math.sin
import android.text.style.ForegroundColorSpan
import android.text.Spanned
import android.text.SpannableStringBuilder
import android.graphics.RadialGradient
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip
import androidx.core.graphics.withTranslation
import androidx.palette.graphics.Palette
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.metrolist.music.R
import com.metrolist.music.ui.component.LyricsBackgroundStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.roundToInt

object ComposeToImage {
    suspend fun createLyricsImage(
        context: Context,
        coverArtUrl: String?,
        songTitle: String,
        artistName: String,
        lyrics: String,
        width: Int,
        height: Int,
        backgroundColor: Int? = null,
        backgroundStyle: LyricsBackgroundStyle = LyricsBackgroundStyle.SOLID,
        textColor: Int? = null,
        secondaryTextColor: Int? = null,
        lyricsAlignment: Layout.Alignment = Layout.Alignment.ALIGN_CENTER,
        /**
         * SONARA's own card, after its mark (a pulse and its echo): the deep blue-black of the
         * app lit by the cover's colour, sonar rings spreading from the cover, the lines fading
         * one after another like an echo, and a pulse beside the SONARA name. Ignores the
         * colour and style arguments.
         */
        sonar: Boolean = false,
    ): Bitmap =
        withContext(Dispatchers.Default) {
            // Use fixed high resolution as requested (2160x2160)
            // This ensures consistent high-quality output regardless of the device screen
            val imageWidth = 2160
            val imageHeight = 2160

            val bitmap = createBitmap(imageWidth, imageHeight)
            val canvas = Canvas(bitmap)
            val backgroundRect = RectF(0f, 0f, imageWidth.toFloat(), imageHeight.toFloat())

            // Base scale on width relative to the reference design (340dp)
            // 2160 / 340 ≈ 6.35
            val scale = imageWidth / 340f
            val cornerRadius = 20f * scale
            val exportPath =
                Path().apply {
                    addRoundRect(backgroundRect, cornerRadius, cornerRadius, Path.Direction.CW)
                }
            canvas.clipPath(exportPath)

            val defaultBackgroundColor = 0xFF121212.toInt()
            val defaultTextColor = 0xFFFFFFFF.toInt()
            val defaultSecondaryTextColor = 0xB3FFFFFF.toInt()

            val bgColor = backgroundColor ?: defaultBackgroundColor
            val mainTextColor = textColor ?: defaultTextColor
            val secondaryTxtColor = secondaryTextColor ?: defaultSecondaryTextColor

            // Pre-load cover art if needed for Blur/Gradient or just for the header
            var coverArtBitmap: Bitmap? = null
            if (coverArtUrl != null) {
                try {
                    val imageLoader = ImageLoader(context)
                    val request =
                        ImageRequest
                            .Builder(context)
                            .data(coverArtUrl)
                            .size(1024)
                            .allowHardware(false)
                            .build()
                    val result = imageLoader.execute(request)
                    coverArtBitmap = result.image?.toBitmap()
                } catch (_: Exception) {
                }
            }

            // Draw Background
            val backgroundPaint =
                Paint().apply {
                    isAntiAlias = true
                }

            val sonarAccent = coverArtBitmap?.let { cover ->
                val palette = Palette.from(cover).generate()
                palette.getVibrantColor(palette.getDominantColor(SONAR_DEFAULT_ACCENT))
            } ?: SONAR_DEFAULT_ACCENT
            val sonarCenterX = (28f + 32f) * scale
            val sonarCenterY = (28f + 32f) * scale

            if (sonar) {
                backgroundPaint.color = SONAR_DEEP
                canvas.drawRect(backgroundRect, backgroundPaint)
                val glow = Paint().apply {
                    isAntiAlias = true
                    shader = RadialGradient(
                        sonarCenterX,
                        sonarCenterY,
                        imageWidth * 1.15f,
                        intArrayOf(withAlpha(sonarAccent, 0.62f), withAlpha(sonarAccent, 0.22f), withAlpha(sonarAccent, 0f)),
                        floatArrayOf(0f, 0.45f, 1f),
                        Shader.TileMode.CLAMP,
                    )
                }
                canvas.drawRect(backgroundRect, glow)
                // Sonar rings spreading from the cover, fading as they go.
                val ring = Paint().apply {
                    isAntiAlias = true
                    style = Paint.Style.STROKE
                    strokeWidth = 1.2f * scale
                }
                for (i in 0 until SONAR_RINGS) {
                    ring.color = withAlpha(0xFFFFFFFF.toInt(), 0.16f * (1f - i.toFloat() / SONAR_RINGS))
                    canvas.drawCircle(sonarCenterX, sonarCenterY, (48f + i * 34f) * scale, ring)
                }
            } else when (backgroundStyle) {
                LyricsBackgroundStyle.SOLID -> {
                    backgroundPaint.color = bgColor
                    canvas.drawRect(backgroundRect, backgroundPaint)
                }

                LyricsBackgroundStyle.BLUR -> {
                    // Draw black base
                    backgroundPaint.color = 0xFF000000.toInt()
                    canvas.drawRect(backgroundRect, backgroundPaint)

                    if (coverArtBitmap != null) {
                        try {
                            // Create a scaled down version for blurring (performance)
                            val scaledBitmap = Bitmap.createScaledBitmap(coverArtBitmap, imageWidth / 10, imageHeight / 10, true)
                            val blurredBitmap = fastBlur(scaledBitmap, 1f, 20) // Radius 20 on small image is large blur

                            if (blurredBitmap != null) {
                                val blurRect = RectF(0f, 0f, imageWidth.toFloat(), imageHeight.toFloat())
                                canvas.drawBitmap(blurredBitmap, null, blurRect, null)

                                // Dark overlay for readability
                                val overlayPaint =
                                    Paint().apply {
                                        color = 0x73000000 // 45% black: white lyrics stay readable on light covers
                                    }
                                canvas.drawRect(blurRect, overlayPaint)
                            }
                        } catch (e: Exception) {
                            // Fallback to solid
                            backgroundPaint.color = bgColor
                            canvas.drawRect(backgroundRect, backgroundPaint)
                        }
                    } else {
                        backgroundPaint.color = bgColor
                        canvas.drawRect(backgroundRect, backgroundPaint)
                    }
                }

                LyricsBackgroundStyle.GRADIENT -> {
                    if (coverArtBitmap != null) {
                        val palette = Palette.from(coverArtBitmap).generate()
                        val vibrant = palette.getVibrantColor(bgColor)
                        val darkVibrant = palette.getDarkVibrantColor(bgColor)

                        val gradient =
                            LinearGradient(
                                0f,
                                0f,
                                imageWidth.toFloat(),
                                imageHeight.toFloat(),
                                intArrayOf(vibrant, darkVibrant),
                                null,
                                Shader.TileMode.CLAMP,
                            )
                        backgroundPaint.shader = gradient
                        canvas.drawRect(backgroundRect, backgroundPaint)
                    } else {
                        backgroundPaint.color = bgColor
                        canvas.drawRect(backgroundRect, backgroundPaint)
                    }
                }
            }

            // Draw inner border
            val borderPaint =
                Paint().apply {
                    color = mainTextColor
                    alpha = (255 * 0.09).toInt()
                    style = Paint.Style.STROKE
                    strokeWidth = 1f * scale
                    isAntiAlias = true
                }
            canvas.drawRoundRect(backgroundRect, cornerRadius, cornerRadius, borderPaint)


            val padding = 28f * scale

            // --- Header Section ---
            val coverArtSize = 64f * scale
            val headerBottomPadding = 12f * scale

            val coverCornerRadius = 3f * scale
            coverArtBitmap?.let {
                val rect = RectF(padding, padding, padding + coverArtSize, padding + coverArtSize)
                val path =
                    Path().apply {
                        addRoundRect(rect, coverCornerRadius, coverCornerRadius, Path.Direction.CW)
                    }

                // Draw border for cover art
                val coverBorderPaint =
                    Paint().apply {
                        color = mainTextColor
                        alpha = (255 * 0.16).toInt()
                        style = Paint.Style.STROKE
                        strokeWidth = 1f * scale
                        isAntiAlias = true
                    }

                canvas.withClip(path) {
                    drawBitmap(it, null, rect, null)
                }
                canvas.drawRoundRect(rect, coverCornerRadius, coverCornerRadius, coverBorderPaint)
            }

            val textStartX = padding + coverArtSize + (16f * scale)
            val textMaxWidth = imageWidth - textStartX - padding

            val titlePaint =
                TextPaint().apply {
                    color = mainTextColor
                    textSize = 20f * scale
                    useAppFont(context, 800, Typeface.DEFAULT_BOLD)
                    isAntiAlias = true
                }

            val artistPaint =
                TextPaint().apply {
                    color = secondaryTxtColor
                    textSize = 16f * scale
                    useAppFont(context, 500, Typeface.DEFAULT)
                    isAntiAlias = true
                }

            // A long title wraps to a second line before it is cut; "(feat. ...)" is left out, the
            // guests are on the artist line. A long artist line gets smaller (to 80%) before it is cut.
            val shownTitle = withoutFeaturing(songTitle)
            val titleLayout =
                StaticLayout.Builder
                    .obtain(shownTitle, 0, shownTitle.length, titlePaint, textMaxWidth.toInt())
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setMaxLines(TITLE_MAX_LINES)
                    .setEllipsize(android.text.TextUtils.TruncateAt.END)
                    .build()

            val artistFullSize = artistPaint.textSize
            while (artistPaint.measureText(artistName) > textMaxWidth && artistPaint.textSize > artistFullSize * ARTIST_MIN_SCALE) {
                artistPaint.textSize -= 0.5f * scale
            }
            val artistLayout =
                StaticLayout.Builder
                    .obtain(artistName, 0, artistName.length, artistPaint, textMaxWidth.toInt())
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setMaxLines(1)
                    .setEllipsize(android.text.TextUtils.TruncateAt.END)
                    .build()

            // Text centred on the cover; when it is taller than the cover (two-line title) it
            // starts at the top and the header grows, pushing the lyrics down.
            val headerTextHeight = titleLayout.height + artistLayout.height + (2f * scale) // +2dp padding between title and artist
            val headerCenterY = padding + coverArtSize / 2f
            val titleY = maxOf(padding, headerCenterY - headerTextHeight / 2f)
            val headerHeight = maxOf(coverArtSize, headerTextHeight)

            canvas.save()
            canvas.translate(textStartX, titleY)
            titleLayout.draw(canvas)
            canvas.translate(0f, titleLayout.height.toFloat() + (2f * scale))
            artistLayout.draw(canvas)
            canvas.restore()

            // --- Footer Section ---
            val logoBoxSize = 22f * scale
            val footerY = imageHeight - padding - logoBoxSize

            ContextCompat.getDrawable(context, R.drawable.app_logo)?.apply {
                setBounds(
                    padding.roundToInt(),
                    footerY.roundToInt(),
                    (padding + logoBoxSize).roundToInt(),
                    (footerY + logoBoxSize).roundToInt(),
                )
                draw(canvas)
            }

            val appName = context.getString(R.string.er_lyrics_signature)
            val appNamePaint =
                TextPaint().apply {
                    color = secondaryTxtColor
                    textSize = 12f * scale
                    useAppFont(context, 700, Typeface.create("sans-serif-medium", Typeface.NORMAL))
                    isAntiAlias = true
                }

            val appNameX = padding + logoBoxSize + (8f * scale)
            // Center text vertically relative to logo box
            val appNameY = footerY + logoBoxSize / 2f - (appNamePaint.descent() + appNamePaint.ascent()) / 2f
            canvas.drawText(appName, appNameX, appNameY, appNamePaint)

            if (sonar) {
                // A pulse at the right of the footer, in the cover's colour.
                val bar = Paint().apply {
                    isAntiAlias = true
                    color = lighten(sonarAccent)
                    strokeCap = Paint.Cap.ROUND
                    strokeWidth = 2.4f * scale
                }
                val step = 5f * scale
                val right = imageWidth - padding
                val middle = footerY + logoBoxSize / 2f
                for (i in 0 until SONAR_BARS) {
                    val height = logoBoxSize * (0.25f + 0.75f * abs(sin(i * 0.62f + 0.4f)) * (1f - i.toFloat() / (SONAR_BARS * 1.6f)))
                    val x = right - i * step
                    canvas.drawLine(x, middle - height / 2f, x, middle + height / 2f, bar)
                }
            }

            // --- Lyrics Section ---
            // Calculate available space
            val lyricsTop = padding + headerHeight + headerBottomPadding
            val lyricsBottom = footerY - (12f * scale) // Add some padding above footer
            val lyricsHeight = lyricsBottom - lyricsTop
            val lyricsWidth = imageWidth - (padding * 2)

            val lyricsPaint =
                TextPaint().apply {
                    color = mainTextColor
                    useAppFont(context, 800, Typeface.create(Typeface.DEFAULT, Typeface.BOLD))
                    isAntiAlias = true
                    letterSpacing = 0.005f
                }

            // Adaptive font size calculation
            // Start with a large size (e.g. 50sp equivalent) and scale down until it fits
            // The SONARA card starts smaller, so a short quote reads as a line of lyrics (about five
            // words a line) instead of two or three huge words per line.
            var lyricsTextSize = (if (sonar) SONAR_MAX_LYRICS_SIZE else 50f) * scale
            val minLyricsSize = 13f * scale
            var lyricsLayout: StaticLayout

            while (lyricsTextSize > minLyricsSize) {
                lyricsPaint.textSize = lyricsTextSize
                lyricsLayout =
                    StaticLayout.Builder
                        .obtain(lyrics, 0, lyrics.length, lyricsPaint, lyricsWidth.toInt())
                        .setAlignment(lyricsAlignment)
                        .setLineSpacing(0f, 1.2f)
                        .setIncludePad(false)
                        .build()

                if (lyricsLayout.height <= lyricsHeight) {
                    break
                }

                lyricsTextSize -= 1f * scale // Decrease by ~1sp equivalent steps
            }

            // One final rebuild with the determined size. The SONARA card fades each line a
            // little more than the one before, like an echo.
            lyricsPaint.textSize = lyricsTextSize
            val lyricsText: CharSequence = if (sonar) echoLines(lyrics, mainTextColor) else lyrics
            lyricsLayout =
                StaticLayout.Builder
                    .obtain(lyricsText, 0, lyricsText.length, lyricsPaint, lyricsWidth.toInt())
                    .setAlignment(lyricsAlignment)
                    .setLineSpacing(0f, 1.2f)
                    .setIncludePad(false)
                    .build()

            // Center vertically in the available space
            val lyricsContentHeight = lyricsLayout.height
            val lyricsY =
                if (lyricsContentHeight < lyricsHeight) {
                    lyricsTop + (lyricsHeight - lyricsContentHeight) / 2f
                } else {
                    lyricsTop
                }

            canvas.withTranslation(padding, lyricsY) {
                lyricsLayout.draw(this)
            }

            return@withContext bitmap
        }

    private const val TITLE_MAX_LINES = 2
    private const val ARTIST_MIN_SCALE = 0.8f
    private val FEATURING = Regex("""\s*[(\[](feat\.?|ft\.?|featuring|with)\s[^)\]]*[)\]]|\s+(feat\.?|ft\.?|featuring)\s.+$""", RegexOption.IGNORE_CASE)

    /** "Song (feat. Someone)" -> "Song"; kept as it is when nothing would be left. */
    internal fun withoutFeaturing(title: String): String = title.replace(FEATURING, "").trim().ifEmpty { title }

    private const val SONAR_DEEP = 0xFF0A0B0D.toInt()
    private const val SONAR_DEFAULT_ACCENT = 0xFF2EE6C5.toInt()

    /** The app's font (Plus Jakarta Sans) at [weight] on the card; [fallback] if it cannot be loaded. */
    private fun TextPaint.useAppFont(
        context: Context,
        weight: Int,
        fallback: Typeface,
    ) {
        val res = when {
            weight >= 800 -> R.font.jakarta_extrabold
            weight >= 700 -> R.font.jakarta_bold
            weight >= 600 -> R.font.jakarta_semibold
            weight >= 500 -> R.font.jakarta_medium
            else -> R.font.jakarta_regular
        }
        typeface = runCatching { ResourcesCompat.getFont(context, res) }.getOrNull() ?: fallback
    }
    private const val SONAR_RINGS = 9
    private const val SONAR_BARS = 18

    /** Largest lyrics size on the SONARA card, in the card's dp (it shrinks further to fit). */
    private const val SONAR_MAX_LYRICS_SIZE = 24f

    private fun withAlpha(
        color: Int,
        alpha: Float,
    ): Int = ((alpha.coerceIn(0f, 1f) * 255).roundToInt() shl 24) or (color and 0x00FFFFFF)

    /** Mixed halfway to white, so the pulse reads on the dark footer. */
    private fun lighten(color: Int): Int {
        fun mix(channel: Int) = (channel + (255 - channel) / 2)
        return (0xFF shl 24) or (mix(color shr 16 and 0xFF) shl 16) or (mix(color shr 8 and 0xFF) shl 8) or mix(color and 0xFF)
    }

    /** Each line a little fainter than the one before (down to 55%). */
    private fun echoLines(
        lyrics: String,
        color: Int,
    ): CharSequence {
        val text = SpannableStringBuilder(lyrics)
        var start = 0
        lyrics.split('\n').forEachIndexed { index, line ->
            val end = start + line.length
            val alpha = (1f - index * 0.15f).coerceAtLeast(0.55f)
            if (end > start) text.setSpan(ForegroundColorSpan(withAlpha(color, alpha)), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            start = end + 1
        }
        return text
    }

    // Stack Blur v1.0 from http://www.quasimondo.com/StackBlurForCanvas/StackBlurDemo.html
    // Java Author: Mario Klingemann <mario at quasimondo.com>
    // http://incubator.quasimondo.com
    //
    // created Feburary 29, 2004
    // Android port : Yahel Bouaziz <yahel at kayenko.com>
    // http://www.kayenko.com
    // ported to Kotlin and adapted
    private fun fastBlur(
        sentBitmap: Bitmap,
        scale: Float,
        radius: Int,
    ): Bitmap? {
        val width = (sentBitmap.width * scale).roundToInt()
        val height = (sentBitmap.height * scale).roundToInt()

        if (width <= 0 || height <= 0) return null

        val bitmap = Bitmap.createScaledBitmap(sentBitmap, width, height, false)
        val w = bitmap.width
        val h = bitmap.height
        val pix = IntArray(w * h)
        bitmap.getPixels(pix, 0, w, 0, 0, w, h)
        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = radius + radius + 1
        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)
        var rsum: Int
        var gsum: Int
        var bsum: Int
        var x: Int
        var y: Int
        var i: Int
        var p: Int
        var yp: Int
        var yi: Int
        var yw: Int
        val vmin = IntArray(Math.max(w, h))
        var divsum = div + 1 shr 1
        divsum *= divsum
        val dv = IntArray(256 * divsum)
        i = 0
        while (i < 256 * divsum) {
            dv[i] = i / divsum
            i++
        }
        yw = 0
        yi = 0
        val stack = Array(div) { IntArray(3) }
        var stackpointer: Int
        var stackstart: Int
        var sir: IntArray
        var rbs: Int
        var r1 = radius + 1
        var routsum: Int
        var goutsum: Int
        var boutsum: Int
        var rinsum: Int
        var ginsum: Int
        var binsum: Int
        y = 0
        while (y < h) {
            bsum = 0
            gsum = 0
            rsum = 0
            boutsum = 0
            goutsum = 0
            routsum = 0
            binsum = 0
            ginsum = 0
            rinsum = 0
            i = -radius
            while (i <= radius) {
                p = pix[yi + Math.min(wm, Math.max(i, 0))]
                sir = stack[i + radius]
                sir[0] = p and 0xff0000 shr 16
                sir[1] = p and 0x00ff00 shr 8
                sir[2] = p and 0x0000ff
                rbs = r1 - Math.abs(i)
                rsum += sir[0] * rbs
                gsum += sir[1] * rbs
                bsum += sir[2] * rbs
                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
                i++
            }
            stackpointer = radius
            x = 0
            while (x < w) {
                r[yi] = dv[rsum]
                g[yi] = dv[gsum]
                b[yi] = dv[bsum]
                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum
                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]
                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]
                if (y == 0) {
                    vmin[x] = Math.min(x + radius + 1, wm)
                }
                p = pix[yw + vmin[x]]
                sir[0] = p and 0xff0000 shr 16
                sir[1] = p and 0x00ff00 shr 8
                sir[2] = p and 0x0000ff
                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]
                rsum += rinsum
                gsum += ginsum
                bsum += binsum
                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer % div]
                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]
                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]
                yi++
                x++
            }
            yw += w
            y++
        }
        x = 0
        while (x < w) {
            bsum = 0
            gsum = 0
            rsum = 0
            boutsum = 0
            goutsum = 0
            routsum = 0
            binsum = 0
            ginsum = 0
            rinsum = 0
            yp = -radius * w
            i = -radius
            while (i <= radius) {
                yi = Math.max(0, yp) + x
                sir = stack[i + radius]
                sir[0] = r[yi]
                sir[1] = g[yi]
                sir[2] = b[yi]
                rbs = r1 - Math.abs(i)
                rsum += sir[0] * rbs
                gsum += sir[1] * rbs
                bsum += sir[2] * rbs
                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
                if (i < hm) {
                    yp += w
                }
                i++
            }
            yi = x
            stackpointer = radius
            y = 0
            while (y < h) {
                pix[yi] = -0x1000000 or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]
                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum
                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]
                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]
                if (x == 0) {
                    vmin[y] = Math.min(y + r1, hm) * w
                }
                p = x + vmin[y]
                sir[0] = r[p]
                sir[1] = g[p]
                sir[2] = b[p]
                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]
                rsum += rinsum
                gsum += ginsum
                bsum += binsum
                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer % div]
                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]
                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]
                yi += w
                y++
            }
            x++
        }
        bitmap.setPixels(pix, 0, w, 0, 0, w, h)
        return bitmap
    }

    fun saveBitmapAsFile(
        context: Context,
        bitmap: Bitmap,
        fileName: String,
    ): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues =
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.png")
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Sonara")
                }
            val uri =
                context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues,
                ) ?: throw IllegalStateException("Failed to create new MediaStore record")

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
            uri
        } else {
            val cachePath = File(context.cacheDir, "images")
            cachePath.mkdirs()
            val imageFile = File(cachePath, "$fileName.png")
            FileOutputStream(imageFile).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.FileProvider",
                imageFile,
            )
        }
}
