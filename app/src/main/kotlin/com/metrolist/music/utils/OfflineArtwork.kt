/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.content.Context
import coil3.intercept.Interceptor
import coil3.request.ImageResult
import com.metrolist.music.ui.utils.resize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.security.MessageDigest

/**
 * Covers of downloaded songs, kept as files in app storage. The image cache could be cleared (by
 * Android, or Settings > Clear cache) and only held the one size fetched, while the player and
 * lists ask for other sizes, so covers went missing offline. One large copy per image serves
 * every size, through [Interceptor].
 */
object OfflineArtwork {
    private const val SIZE = 1080
    private val http by lazy { OkHttpClient() }

    private fun dir(context: Context) = File(context.filesDir, "offline_artwork").apply { mkdirs() }

    /** The same picture in any size (`...=w120-h120`, `...?sqp=`) maps to one file. */
    private fun fileFor(context: Context, url: String): File {
        val base = url.substringBefore('=').substringBefore('?')
        val name = MessageDigest.getInstance("SHA-1").digest(base.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir(context), "$name.jpg")
    }

    fun has(context: Context, url: String): Boolean = fileFor(context, url).exists()

    /** Saves [url]'s picture (large) unless it is there already. */
    suspend fun save(context: Context, url: String) = withContext(Dispatchers.IO) {
        if (!url.startsWith("http")) return@withContext
        val target = fileFor(context, url)
        if (target.exists()) return@withContext
        runCatching {
            val request = Request.Builder().url(url.resize(SIZE, SIZE)).build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use
                val temp = File(target.parentFile, target.name + ".part")
                temp.outputStream().use { out -> response.body.byteStream().copyTo(out) }
                temp.renameTo(target)
            }
        }.onFailure { Timber.tag("SonaraWeb").w(it, "offline artwork failed") }
    }

    /** Serves a downloaded song's cover from its file, in whatever size it is asked for. */
    class Interceptor(private val context: Context) : coil3.intercept.Interceptor {
        override suspend fun intercept(chain: coil3.intercept.Interceptor.Chain): ImageResult {
            val url = chain.request.data as? String
            if (url != null && url.startsWith("http")) {
                val file = fileFor(context, url)
                if (file.exists()) {
                    return chain.withRequest(chain.request.newBuilder().data(file).build()).proceed()
                }
            }
            return chain.proceed()
        }
    }
}
