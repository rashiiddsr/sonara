/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ermusic.desktop.ui

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.ermusic.desktop.playback.Player
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Test

/** The real result row and its real menu, opened with the mouse, for each kind of item. */
@OptIn(ExperimentalTestApi::class)
class RowMenuTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val app = AppState(Player(scope), scope) {}
    private val artist = Artist("Someone", "UC1")
    private val song = SongItem(id = "s1", title = "A song", artists = listOf(artist), duration = 200, thumbnail = "")
    private val album = AlbumItem(browseId = "MPRE1", playlistId = "OLAK1", title = "An album", artists = listOf(artist), thumbnail = "")
    private val person = ArtistItem(id = "UC1", title = "An artist", thumbnail = null, shuffleEndpoint = null, radioEndpoint = null)
    private val list = PlaylistItem(id = "PL1", title = "A playlist", author = artist, songCountText = "3 songs", thumbnail = null, playEndpoint = null, shuffleEndpoint = null, radioEndpoint = null)

    private fun ComposeUiTest.show(items: () -> List<YTItem>) =
        setContent { MaterialTheme { LazyColumn(Modifier.size(600.dp, 500.dp)) { items(items(), key = { it.id }, contentType = { it::class }) { ItemRow(it, app) } } } }

    private fun ComposeUiTest.openMenu(title: String, hoverMs: Long) {
        onNodeWithText(title).performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(100)
        onNodeWithContentDescription("More").performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(hoverMs)
        onNodeWithContentDescription("More").performMouseInput { click(center) }
        repeat(30) { mainClock.advanceTimeBy(16); waitForIdle() }
        onNodeWithText("Copy link").assertExists()
    }

    private fun check(item: YTItem, hoverMs: Long) = runComposeUiTest { show { listOf(item) }; waitForIdle(); openMenu(item.title, hoverMs) }

    @Test fun songRightAway() = check(song, 30)
    @Test fun songWithTooltip() = check(song, 800)
    @Test fun albumRightAway() = check(album, 30)
    @Test fun artistRightAway() = check(person, 30)
    @Test fun playlistRightAway() = check(list, 30)

    @Test fun listChangesWithTheMenuOpen() =
        runComposeUiTest {
            var items by mutableStateOf(listOf<YTItem>(song, album))
            show { items }
            waitForIdle()
            openMenu(song.title, 800)
            items = listOf(person, list)
            repeat(30) { mainClock.advanceTimeBy(16); waitForIdle() }
        }
}
