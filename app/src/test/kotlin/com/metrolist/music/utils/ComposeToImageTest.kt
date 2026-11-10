package com.metrolist.music.utils

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.text.Layout
import androidx.test.core.app.ApplicationProvider
import com.metrolist.music.R
import com.metrolist.music.ui.component.LyricsBackgroundStyle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComposeToImageTest {
    @Test
    fun drawsTheSonaraCardWithLongTitlesAndArtists() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Sonara", context.getString(R.string.er_lyrics_signature))

        val coverFile = File(context.cacheDir, "lyrics-sonar-cover.png")
        val cover = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        cover.eraseColor(Color.rgb(200, 60, 90))
        coverFile.outputStream().use { assertTrue(cover.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        cover.recycle()

        val cases = listOf(
            Triple("Memori Baik", "Sheila On 7", "Kau ingatkan aku\nAkan memori baik\nYang pernah kita lewati bersama"),
            Triple(
                "A Very Long Song Title That Needs Two Lines To Be Read (feat. Somebody Else)",
                "Artist Number One, Artist Number Two & 3 more",
                "Short line",
            ),
        )
        cases.forEachIndexed { index, (title, artist, lyrics) ->
            val image = ComposeToImage.createLyricsImage(
                context = context,
                coverArtUrl = coverFile.toURI().toString(),
                songTitle = title,
                artistName = artist,
                lyrics = lyrics,
                width = 340,
                height = 340,
                lyricsAlignment = Layout.Alignment.ALIGN_NORMAL,
                sonar = true,
            )
            try {
                assertEquals(2160, image.width)
                assertEquals(2160, image.height)
                for ((x, y) in listOf(0 to 0, 2159 to 0, 0 to 2159, 2159 to 2159)) {
                    assertEquals(0, Color.alpha(image.getPixel(x, y)))
                }
                assertEquals(255, Color.alpha(image.getPixel(image.width / 2, image.height / 2)))
                System.getenv("SONARA_LYRICS_PREVIEW_DIR")?.let { directory ->
                    val file = File(directory, "lyrics-sonar-$index.png")
                    file.parentFile?.mkdirs()
                    file.outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                }
            } finally {
                image.recycle()
            }
        }
    }

    @Test
    fun leavesFeaturingOutOfTheCardTitle() {
        assertEquals("Song", ComposeToImage.withoutFeaturing("Song (feat. Someone)"))
        assertEquals("Song", ComposeToImage.withoutFeaturing("Song [ft. A & B]"))
        assertEquals("Song", ComposeToImage.withoutFeaturing("Song feat. Someone"))
        assertEquals("Featuring", ComposeToImage.withoutFeaturing("Featuring"))
        assertEquals("Without You", ComposeToImage.withoutFeaturing("Without You"))
    }

    @Test
    fun sharesBrandedPngWithArtworkThroughBothStoragePaths() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val coverFile = File(context.cacheDir, "lyrics-test-cover.png")
        val cover = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        cover.eraseColor(Color.rgb(40, 82, 120))
        coverFile.outputStream().use { assertTrue(cover.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        cover.recycle()

        val alignments = listOf(Layout.Alignment.ALIGN_NORMAL, Layout.Alignment.ALIGN_CENTER, Layout.Alignment.ALIGN_OPPOSITE)
        for ((style, alignment) in LyricsBackgroundStyle.entries.zip(alignments)) {
            val image = ComposeToImage.createLyricsImage(
                context = context,
                coverArtUrl = coverFile.toURI().toString(),
                songTitle = "Benang Nada",
                artistName = "Utaloom",
                lyrics = "Musik yang menemani harimu\nنسج الألحان\n音楽を紡ぐ",
                width = 340,
                height = 340,
                backgroundStyle = style,
                lyricsAlignment = alignment,
            )
            try {
                val scale = image.width / 340f
                assertEquals(Color.rgb(40, 82, 120), image.getPixel((60 * scale).toInt(), (60 * scale).toInt()))
                val uri = ComposeToImage.saveBitmapAsFile(context, image, "lyrics-test-${style.name}")
                context.contentResolver.openInputStream(uri).use { stream ->
                    assertNotNull(stream)
                    val decoded = BitmapFactory.decodeStream(stream)
                    assertNotNull(decoded)
                    assertEquals(image.width, decoded.width)
                    assertEquals(image.height, decoded.height)
                    assertEquals(image.getPixel((12 * scale).toInt(), image.height / 2), decoded.getPixel((12 * scale).toInt(), image.height / 2))
                    decoded.recycle()
                }
            } finally {
                image.recycle()
            }
        }
    }
}
