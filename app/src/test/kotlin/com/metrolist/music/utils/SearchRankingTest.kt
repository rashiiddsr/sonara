/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.pages.SearchSummary
import com.metrolist.innertube.pages.SearchSummaryPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRankingTest {
    private fun song(
        id: String,
        title: String,
        artist: String,
        video: Boolean = false,
    ) = SongItem(
        id = id,
        title = title,
        artists = listOf(Artist(name = artist, id = "UC_$artist")),
        thumbnail = "",
        musicVideoType = if (video) "MUSIC_VIDEO_TYPE_OMV" else "MUSIC_VIDEO_TYPE_ATV",
    )

    @Test
    fun `the song takes the top result from its video`() {
        val video = song("v1", "Memori Baik (Official Music Video)", "Sheila On 7", video = true)
        val songVersion = song("s1", "Memori Baik", "Sheila On 7")
        val page = SearchSummaryPage(
            listOf(
                SearchSummary("Top result", listOf(video)),
                SearchSummary("Songs", listOf(song("s0", "Lain", "X"), songVersion)),
            ),
        )

        val top = SearchRanking.rerank(page, "memori baik").summaries.first().items

        assertEquals(listOf("s1", "v1"), top.map { it.id })
    }

    @Test
    fun `a video stays on top when no song of it is in the results`() {
        val video = song("v1", "Memori Baik", "Sheila On 7", video = true)
        val page = SearchSummaryPage(
            listOf(
                SearchSummary("Top result", listOf(video)),
                SearchSummary("Songs", listOf(song("s0", "Memori Baik", "Someone Else"))),
            ),
        )

        assertEquals("v1", SearchRanking.rerank(page, "memori baik").summaries.first().items.first().id)
    }

    @Test
    fun `exact titles come first in the songs section, others keep their order`() {
        val page = SearchSummaryPage(
            listOf(
                SearchSummary("Top result", listOf(song("a", "Artist page", "Z"))),
                SearchSummary(
                    "Songs",
                    listOf(song("1", "Hati Hati", "A"), song("2", "Dan", "B"), song("3", "Hati", "C"), song("4", "Dan", "D")),
                ),
            ),
        )

        val songs = SearchRanking.rerank(page, "Dan").summaries[1].items

        assertEquals(listOf("2", "4", "1", "3"), songs.map { it.id })
    }

    @Test
    fun `title followed by the artist counts as exact`() {
        assertTrue(SearchRanking.matchesQuery(song("1", "Memori Baik", "Sheila On 7"), "memori baik sheila on 7"))
        assertTrue(SearchRanking.matchesQuery(song("1", "Memori Baik", "Sheila On 7"), "Sheila on 7 - Memori Baik"))
        assertFalse(SearchRanking.matchesQuery(song("1", "Memori Baik", "Sheila On 7"), "memori"))
    }

    @Test
    fun `brackets, accents and video words are ignored`() {
        assertEquals("cafe", SearchRanking.normalize("Café (Official Video) [HD]"))
        assertEquals("memori baik", SearchRanking.normalize("MEMORI BAIK - Official Lyric Video"))
    }

    @Test
    fun `no match keeps the page as it was`() {
        val page = SearchSummaryPage(
            listOf(
                SearchSummary("Top result", listOf(song("a", "One", "X"))),
                SearchSummary("Songs", listOf(song("1", "Two", "Y"), song("2", "Three", "Z"))),
            ),
        )

        assertEquals(page, SearchRanking.rerank(page, "nothing like it"))
    }
}
