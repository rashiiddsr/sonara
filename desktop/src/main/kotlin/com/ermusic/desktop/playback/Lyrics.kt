/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ermusic.desktop.playback

import android.content.Context
import com.metrolist.paxsenix.Paxsenix
import com.metrolist.music.lyrics.BetterLyricsProvider
import com.metrolist.music.lyrics.KuGouLyricsProvider
import com.metrolist.music.lyrics.LrcLibLyricsProvider
import com.metrolist.music.lyrics.LyricsPlusProvider
import com.metrolist.music.lyrics.LyricsProvider
import com.metrolist.music.lyrics.LyricsUtils
import com.metrolist.music.lyrics.PaxsenixLyricsProvider
import com.metrolist.music.lyrics.YouTubeLyricsProvider
import com.metrolist.music.lyrics.YouTubeSubtitleLyricsProvider
import com.metrolist.music.lyrics.ZemerLyricsProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

data class LyricWord(val text: String, val startMs: Long, val endMs: Long, val trailingSpace: Boolean)

/** [words] is there only when the source timed each word. */
data class LyricLine(val timeMs: Long, val text: String, val words: List<LyricWord>? = null)

/** [synced] lines carry real times; otherwise the times are only their order. */
data class Lyrics(val lines: List<LyricLine>, val synced: Boolean, val source: String)

/**
 * The Android app's lyric sources, compiled from its own files, tried at once and taken in its
 * order: the first with an answer wins, so word-by-word sources come before line-by-line ones.
 */
object LyricsRepository {
    private const val PER_SOURCE_TIMEOUT_MS = 12_000L
    private const val MS_PER_SECOND = 1000L
    private const val SECONDS_DIVISOR = 1000

    private val sources: List<LyricsProvider> =
        listOf(
            BetterLyricsProvider,
            LrcLibLyricsProvider,
            KuGouLyricsProvider,
            PaxsenixLyricsProvider,
            LyricsPlusProvider,
            ZemerLyricsProvider,
            YouTubeSubtitleLyricsProvider,
            YouTubeLyricsProvider,
        )
    private val context = Context().also { Paxsenix.init(it) }
    private val cache = ConcurrentHashMap<String, Lyrics>()
    private val timestamp = Regex("""\[\d+:\d+""")

    suspend fun fetch(track: Track): Lyrics? =
        cache[track.id] ?: withContext(Dispatchers.IO) {
            val title = LyricsUtils.cleanTitleForSearch(track.title)
            val seconds = (track.durationMs / SECONDS_DIVISOR).toInt()
            coroutineScope {
                val pending =
                    sources.map { source ->
                        source to
                            async {
                                try {
                                    withTimeoutOrNull(PER_SOURCE_TIMEOUT_MS) { source.getLyrics(context, track.id, title, track.artists, seconds, track.albumName) }
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (_: Exception) {
                                    null
                                }
                            }
                    }
                var found: Lyrics? = null
                for ((source, attempt) in pending) {
                    val text = attempt.await()?.getOrNull()
                    found = text?.let { parse(LyricsUtils.filterLyricsCreditLines(it), source.name) }
                    if (found != null) break
                }
                pending.forEach { (_, attempt) -> attempt.cancel() }
                found
            }?.also { cache[track.id] = it }
        }

    private fun parse(text: String, source: String): Lyrics? {
        if (text.isBlank()) return null
        if (timestamp.containsMatchIn(text)) {
            val lines =
                runCatching { LyricsUtils.parseLyrics(text) }.getOrNull().orEmpty()
                    .filter { !it.isBackground && it.text.isNotBlank() }
                    .map { entry ->
                        LyricLine(
                            entry.time,
                            entry.text,
                            entry.words?.takeIf { it.isNotEmpty() }?.map { LyricWord(it.text, (it.startTime * MS_PER_SECOND).toLong(), (it.endTime * MS_PER_SECOND).toLong(), it.hasTrailingSpace) },
                        )
                    }
            if (lines.isNotEmpty()) return Lyrics(lines, synced = true, source)
        }
        val plain = text.lines().map { it.trim() }.filter { it.isNotEmpty() && !timestamp.containsMatchIn(it) }
        return if (plain.isEmpty()) null else Lyrics(plain.mapIndexed { index, line -> LyricLine(index.toLong(), line) }, synced = false, source)
    }
}
