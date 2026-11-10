/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.pages.SearchSummary
import com.metrolist.innertube.pages.SearchSummaryPage
import java.text.Normalizer

/**
 * Tidies YouTube's own search order a little before it is shown; nothing is added or dropped.
 * The web client SONARA searches with often puts the music video as the top result and lists
 * songs by popularity, so the song someone typed exactly could sit third. Two rules:
 *
 * 1. A video as the top result gives way to the same song (same title, a shared artist) when it
 *    is in the results; the video stays in the results after it.
 * 2. In the songs section, songs whose title is the query (or "title artist") come first, in
 *    YouTube's order among themselves.
 *
 * Nothing matches: YouTube's order is kept.
 */
object SearchRanking {
    fun rerank(
        page: SearchSummaryPage,
        query: String,
    ): SearchSummaryPage {
        if (page.summaries.isEmpty()) return page
        val summaries = page.summaries.toMutableList()

        // Rule 2: exact titles first in each section made of songs.
        summaries.indices.forEach { i ->
            val section = summaries[i]
            if (i > 0 && section.isSongSection()) summaries[i] = section.copy(items = exactFirst(section.items, query))
        }

        // Rule 1: the song instead of its video at the top.
        val top = summaries.first()
        val head = top.items.firstOrNull() as? SongItem
        if (summaries.size > 1 && head != null && head.isVideoSong) {
            val song = summaries.asSequence()
                .flatMap { it.items.asSequence() }
                .filterIsInstance<SongItem>()
                .firstOrNull { !it.isVideoSong && it.id != head.id && sameSong(it, head) }
            if (song != null) {
                summaries[0] = top.copy(items = listOf(song) + top.items.filter { it.id != song.id })
            }
        }
        return SearchSummaryPage(summaries)
    }

    /** Rule 2 for a plain list (the Songs filter). */
    fun exactFirst(
        items: List<YTItem>,
        query: String,
    ): List<YTItem> {
        val (exact, rest) = items.partition { it is SongItem && !it.isVideoSong && matchesQuery(it, query) }
        return if (exact.isEmpty()) items else exact + rest
    }

    /** The title is the query, or the query is the title followed or preceded by an artist. */
    internal fun matchesQuery(
        song: SongItem,
        query: String,
    ): Boolean {
        val q = normalize(query)
        val title = normalize(song.title)
        if (q.isEmpty() || title.isEmpty()) return false
        if (q == title) return true
        return song.artists.any { artist ->
            val name = normalize(artist.name)
            name.isNotEmpty() && (q == "$title $name" || q == "$name $title")
        }
    }

    private fun sameSong(
        a: SongItem,
        b: SongItem,
    ): Boolean {
        if (normalize(a.title) != normalize(b.title)) return false
        val ids = b.artists.mapNotNull { it.id }.toSet()
        val names = b.artists.map { normalize(it.name) }.toSet()
        return a.artists.any { (it.id != null && it.id in ids) || normalize(it.name) in names }
    }

    private fun SearchSummary.isSongSection(): Boolean {
        val songs = items.count { it is SongItem && !it.isVideoSong }
        return songs > 0 && songs * 2 >= items.size
    }

    /**
     * Lower case, accents off, bracketed parts ("(Official Video)", "[Lyrics]") and the usual
     * "official ... video" words off, punctuation to spaces.
     */
    internal fun normalize(text: String): String {
        val noBrackets = text.replace(BRACKETS, " ")
        val plain = Normalizer.normalize(noBrackets, Normalizer.Form.NFD).replace(ACCENTS, "").lowercase()
        return plain.replace(NOT_WORD, " ")
            .split(' ')
            .filter { it.isNotBlank() && it !in NOISE }
            .joinToString(" ")
    }

    private val BRACKETS = Regex("""[(\[{][^)\]}]*[)\]}]""")
    private val ACCENTS = Regex("""\p{Mn}+""")
    private val NOT_WORD = Regex("""[^\p{L}\p{N}]+""")
    private val NOISE = setOf("official", "video", "music", "audio", "lyrics", "lyric", "mv", "hd", "4k")
}
