/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ermusic.desktop.playback

import com.ermusic.desktop.AppFiles
import com.ermusic.desktop.data.Downloads
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

/** A song on disk; [loudnessDb] is how far YouTube says it is above the reference level. */
data class LocalSong(val file: File, val loudnessDb: Double?)

/**
 * Songs are fetched whole before they play. YouTube refuses the open-ended range requests a seek
 * makes on a streamed URL; from a local file seeking always works, a paused song cannot outlive
 * its URL, and playing it again starts at once.
 */
object SongCache {
    private const val CHUNK_BYTES = 1_048_576L
    private const val MAX_CACHE_BYTES = 1_073_741_824L
    private const val PARTIAL_CONTENT = 206

    private val dir = File(AppFiles.root, "songs").apply { mkdirs() }
    private val http = OkHttpClient()
    private val locks = ConcurrentHashMap<String, Mutex>()

    private fun loudnessFile(videoId: String) = File(dir, "$videoId.loud")

    private fun stored(file: File, videoId: String): LocalSong =
        LocalSong(file, runCatching { loudnessFile(videoId).readText().toDouble() }.getOrNull())

    suspend fun get(videoId: String): LocalSong =
        locks.getOrPut(videoId) { Mutex() }.withLock {
            withContext(Dispatchers.IO) {
                Downloads.fileFor(videoId).takeIf(File::exists)?.let { return@withContext stored(it, videoId) }
                val file = File(dir, "$videoId.audio")
                if (file.exists()) {
                    file.setLastModified(System.currentTimeMillis())
                    return@withContext stored(file, videoId)
                }
                val stream = StreamResolver.resolve(videoId)
                val part = File(dir, "$videoId.part")
                part.delete()
                download(stream, part)
                if (!part.renameTo(file)) throw IOException("Could not store $videoId")
                stream.loudnessDb?.let { loudnessFile(videoId).writeText(it.toString()) }
                trim(keep = file)
                LocalSong(file, stream.loudnessDb)
            }
        }

    /** Empties the cache; downloads stay. */
    fun clear() {
        dir.listFiles().orEmpty().forEach(File::delete)
    }

    fun sizeBytes(): Long = dir.listFiles().orEmpty().sumOf(File::length)

    private suspend fun download(stream: ResolvedStream, target: File) {
        RandomAccessFile(target, "rw").use { out ->
            var offset = 0L
            var total = Long.MAX_VALUE
            while (offset < total) {
                coroutineContext.ensureActive()
                val request =
                    Request.Builder()
                        .url(stream.url)
                        .apply { stream.headers.forEach { (name, value) -> header(name, value) } }
                        .header("Range", "bytes=$offset-${offset + CHUNK_BYTES - 1}")
                        .build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    if (response.code == PARTIAL_CONTENT) {
                        total = response.header("Content-Range")?.substringAfter('/')?.toLongOrNull() ?: total
                    }
                    out.seek(offset)
                    val bytes = response.body.bytes()
                    if (bytes.isEmpty()) throw IOException("Empty response")
                    out.write(bytes)
                    offset += bytes.size
                    if (response.code != PARTIAL_CONTENT || total == Long.MAX_VALUE) total = offset
                }
            }
        }
    }

    private fun trim(keep: File) {
        val files = dir.listFiles { file -> file.extension == "audio" && file != keep }.orEmpty().sortedBy(File::lastModified)
        var size = files.sumOf(File::length) + keep.length()
        for (file in files) {
            if (size <= MAX_CACHE_BYTES) break
            size -= file.length()
            file.delete()
        }
    }
}
