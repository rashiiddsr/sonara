/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.palette.graphics.Palette
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.metrolist.music.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * What a share card shows: [kind] above the [title] (Song, Album, Playlist, Artist, Radio), the
 * [subtitle] under it, the cover ([round] for an artist), and the [link] sent with the picture.
 */
data class ShareItem(
    val kind: String,
    val title: String,
    val subtitle: String?,
    val coverUrl: String?,
    val link: String,
    val round: Boolean = false,
)

/**
 * Sharing from Sonara: a picture made for stories and chats (the cover large on the deep Sonara
 * background lit by the cover's own colour, what it is, its title, and the Sonara mark), sent
 * together with the link. Instagram, WhatsApp and the like show the picture; the link and a line
 * naming Sonara go with it as text.
 */
object ShareCard {
    // Sharing outlives the menu that started it, so it runs on a scope of its own.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun share(context: Context, item: ShareItem) {
        val app = context.applicationContext
        scope.launch {
            val uri =
                runCatching {
                    val card = render(app, item)
                    withContext(Dispatchers.IO) { save(app, card) }
                }.getOrNull()
            val text = buildString {
                append(item.title)
                item.subtitle?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                append('\n').append(item.link)
                append("\n\n").append(app.getString(R.string.er_shared_from))
            }
            val send =
                Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_TEXT, text)
                    putExtra(Intent.EXTRA_TITLE, item.title)
                    if (uri != null) {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        clipData = ClipData.newRawUri(item.title, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } else {
                        // The picture couldn't be made (no cover, say): the link alone still goes.
                        type = "text/plain"
                    }
                }
            context.startActivity(
                Intent.createChooser(send, app.getString(R.string.share)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /** The card, story-sized (9:16), drawn at [WIDTH] x [HEIGHT]. */
    suspend fun render(context: Context, item: ShareItem): Bitmap =
        withContext(Dispatchers.Default) {
            val cover = item.coverUrl?.let { loadCover(context, it) }
            val accent = cover?.let { bitmap ->
                val palette = Palette.from(bitmap).generate()
                palette.getVibrantColor(palette.getDominantColor(DEFAULT_ACCENT))
            } ?: DEFAULT_ACCENT

            val bitmap = createBitmap(WIDTH, HEIGHT)
            val canvas = Canvas(bitmap)
            val w = WIDTH.toFloat()
            val h = HEIGHT.toFloat()

            // The deep Sonara background, lit from above by the cover's colour.
            canvas.drawColor(DEEP)
            canvas.drawRect(0f, 0f, w, h, Paint().apply {
                shader = RadialGradient(w / 2f, h * 0.30f, w * 1.05f, intArrayOf(withAlpha(accent, 0.70f), withAlpha(accent, 0.22f), withAlpha(accent, 0f)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            })
            canvas.drawRect(0f, h * 0.55f, w, h, Paint().apply {
                shader = LinearGradient(0f, h * 0.55f, 0f, h, withAlpha(DEEP, 0f), DEEP, Shader.TileMode.CLAMP)
            })

            // Sonar rings spreading from the cover.
            val coverSize = w * COVER_SHARE
            val coverTop = h * COVER_TOP
            val cx = w / 2f
            val cy = coverTop + coverSize / 2f
            val ring = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeWidth = 3f
            }
            for (i in 1..RINGS) {
                ring.color = withAlpha(lighten(accent), 0.16f * (1f - i.toFloat() / (RINGS + 1)))
                canvas.drawCircle(cx, cy, coverSize / 2f + i * RING_STEP, ring)
            }

            // The cover, with a soft shadow in its own colour.
            val left = cx - coverSize / 2f
            val rect = RectF(left, coverTop, left + coverSize, coverTop + coverSize)
            val radius = if (item.round) coverSize / 2f else COVER_RADIUS
            canvas.drawRoundRect(rect, radius, radius, Paint().apply {
                isAntiAlias = true
                color = withAlpha(darken(accent), 0.9f)
                setShadowLayer(SHADOW_RADIUS, 0f, SHADOW_Y, withAlpha(0xFF000000.toInt(), 0.55f))
            })
            if (cover != null) {
                val scale = maxOf(coverSize / cover.width, coverSize / cover.height)
                val shader = BitmapShader(cover, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                    setLocalMatrix(Matrix().apply {
                        setScale(scale, scale)
                        postTranslate(left + (coverSize - cover.width * scale) / 2f, coverTop + (coverSize - cover.height * scale) / 2f)
                    })
                }
                canvas.drawRoundRect(rect, radius, radius, Paint().apply {
                    isAntiAlias = true
                    this.shader = shader
                })
            }

            // What it is, its title and the line under it, centred.
            val textWidth = (w - MARGIN * 2).toInt()
            var y = rect.bottom + KIND_GAP
            val kindPaint = TextPaint().apply {
                isAntiAlias = true
                color = lighten(accent)
                textSize = KIND_SIZE
                letterSpacing = 0.18f
                appFont(context, 700, Typeface.DEFAULT_BOLD)
            }
            y += drawCentered(canvas, item.kind.uppercase(), kindPaint, textWidth, y, 1)
            y += TITLE_GAP
            val titlePaint = TextPaint().apply {
                isAntiAlias = true
                color = 0xFFFFFFFF.toInt()
                textSize = TITLE_SIZE
                appFont(context, 800, Typeface.DEFAULT_BOLD)
            }
            y += drawCentered(canvas, item.title, titlePaint, textWidth, y, 2)
            item.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
                y += SUBTITLE_GAP
                val subtitlePaint = TextPaint().apply {
                    isAntiAlias = true
                    color = 0xB3FFFFFF.toInt()
                    textSize = SUBTITLE_SIZE
                    appFont(context, 500, Typeface.DEFAULT)
                }
                drawCentered(canvas, subtitle, subtitlePaint, textWidth, y, 2)
            }

            // The Sonara mark at the foot, and where to listen.
            val logoSize = LOGO_SIZE
            val brandPaint = TextPaint().apply {
                isAntiAlias = true
                color = 0xFFFFFFFF.toInt()
                textSize = BRAND_SIZE
                appFont(context, 800, Typeface.DEFAULT_BOLD)
            }
            val brand = context.getString(R.string.er_lyrics_signature)
            val brandWidth = logoSize + BRAND_GAP + brandPaint.measureText(brand)
            val footY = h - FOOT_MARGIN - logoSize
            val brandLeft = cx - brandWidth / 2f
            ContextCompat.getDrawable(context, R.drawable.app_logo)?.apply {
                setBounds(brandLeft.toInt(), footY.toInt(), (brandLeft + logoSize).toInt(), (footY + logoSize).toInt())
                draw(canvas)
            }
            val baseline = footY + logoSize / 2f - (brandPaint.descent() + brandPaint.ascent()) / 2f
            canvas.drawText(brand, brandLeft + logoSize + BRAND_GAP, baseline, brandPaint)
            val listenPaint = TextPaint().apply {
                isAntiAlias = true
                color = 0x99FFFFFF.toInt()
                textSize = LISTEN_SIZE
                textAlign = Paint.Align.CENTER
                appFont(context, 500, Typeface.DEFAULT)
            }
            canvas.drawText(context.getString(R.string.er_share_listen), cx, footY - LISTEN_GAP, listenPaint)

            bitmap
        }

    private suspend fun loadCover(context: Context, url: String): Bitmap? =
        runCatching {
            val request = ImageRequest.Builder(context).data(url).size(COVER_PIXELS).allowHardware(false).build()
            ImageLoader(context).execute(request).image?.toBitmap()
        }.getOrNull()

    /** Draws [text] centred at [top]; returns the height it took. */
    private fun drawCentered(canvas: Canvas, text: String, paint: TextPaint, width: Int, top: Float, maxLines: Int): Float {
        val layout =
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setMaxLines(maxLines)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setLineSpacing(0f, LINE_SPACING)
                .build()
        canvas.save()
        canvas.translate((canvas.width - width) / 2f, top)
        layout.draw(canvas)
        canvas.restore()
        return layout.height.toFloat()
    }

    /** Kept in the app's cache (not the gallery); the last card replaces the one before. */
    private fun save(context: Context, bitmap: Bitmap): android.net.Uri {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        dir.listFiles().orEmpty().forEach(File::delete)
        val file = File(dir, "sonara-${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", file)
    }

    private fun TextPaint.appFont(context: Context, weight: Int, fallback: Typeface) {
        val res = when {
            weight >= 800 -> R.font.jakarta_extrabold
            weight >= 700 -> R.font.jakarta_bold
            else -> R.font.jakarta_medium
        }
        typeface = runCatching { ResourcesCompat.getFont(context, res) }.getOrNull() ?: fallback
    }

    private fun withAlpha(color: Int, alpha: Float): Int = ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24) or (color and 0x00FFFFFF)

    private fun lighten(color: Int): Int {
        fun mix(channel: Int) = channel + (255 - channel) / 2
        return (0xFF shl 24) or (mix(color shr 16 and 0xFF) shl 16) or (mix(color shr 8 and 0xFF) shl 8) or mix(color and 0xFF)
    }

    private fun darken(color: Int): Int {
        fun half(channel: Int) = channel / 2
        return (0xFF shl 24) or (half(color shr 16 and 0xFF) shl 16) or (half(color shr 8 and 0xFF) shl 8) or half(color and 0xFF)
    }

    const val WIDTH = 1080
    const val HEIGHT = 1920
    private const val DEEP = 0xFF0A0B0D.toInt()
    private const val DEFAULT_ACCENT = 0xFF2EE6C5.toInt()
    private const val COVER_PIXELS = 1024
    private const val COVER_SHARE = 0.68f
    private const val COVER_TOP = 0.17f
    private const val COVER_RADIUS = 44f
    private const val SHADOW_RADIUS = 60f
    private const val SHADOW_Y = 24f
    private const val RINGS = 6
    private const val RING_STEP = 64f
    private const val MARGIN = 96f
    private const val KIND_GAP = 96f
    private const val KIND_SIZE = 34f
    private const val TITLE_GAP = 20f
    private const val TITLE_SIZE = 78f
    private const val SUBTITLE_GAP = 16f
    private const val SUBTITLE_SIZE = 44f
    private const val LINE_SPACING = 1.08f
    private const val LOGO_SIZE = 72f
    private const val BRAND_GAP = 20f
    private const val BRAND_SIZE = 52f
    private const val FOOT_MARGIN = 120f
    private const val LISTEN_SIZE = 32f
    private const val LISTEN_GAP = 36f
}
