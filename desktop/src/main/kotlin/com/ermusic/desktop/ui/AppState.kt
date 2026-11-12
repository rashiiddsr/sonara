/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ermusic.desktop.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ermusic.desktop.data.Downloads
import com.ermusic.desktop.playback.Credit
import com.ermusic.desktop.playback.Player
import com.ermusic.desktop.playback.Track
import com.ermusic.desktop.playback.toTrack
import com.ermusic.desktop.window.WindowChrome
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AccountInfo
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.BrowseEndpoint
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.EpisodeItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.PodcastItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.pages.HomePage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.awt.Toolkit
import java.util.concurrent.ConcurrentHashMap
import java.awt.datatransfer.StringSelection

enum class SidePanel { NowPlaying, Queue }

/** Home's content, kept while the user visits other pages so coming back is instant. */
class HomeModel {
    var chip by mutableStateOf<HomePage.Chip?>(null)
    var chips by mutableStateOf<List<HomePage.Chip>>(emptyList())
    val sections = mutableStateListOf<HomePage.Section>()
    var continuation by mutableStateOf<String?>(null)
    var failed by mutableStateOf(false)
    var loading by mutableStateOf(true)
    var loadedChip: HomePage.Chip? = null
    var loaded = false
    val list = LazyListState()
}

sealed interface Route {
    data object Home : Route
    data object Browse : Route
    data object Library : Route
    data object Settings : Route
    data class Search(val query: String) : Route
    data class Album(val id: String) : Route
    data class Playlist(val id: String) : Route
    data class Artist(val id: String) : Route
    data class Category(val browseId: String, val params: String?, val title: String) : Route
    data class Radio(val title: String, val art: String?, val endpoint: WatchEndpoint) : Route
    data object Downloads : Route
    data class Episodes(val list: EpisodeList) : Route
    data object History : Route
    data class ShelfAll(val title: String, val items: List<YTItem>) : Route
    data class ArtistItems(val title: String, val endpoint: BrowseEndpoint) : Route
}

const val LIKED_PLAYLIST_ID = "LM"
private const val TOAST_MS = 2_500L
private const val PLAYLIST_LIMIT = 2000
private const val LIKED_SONGS_LIMIT = 1500
private const val SYNC_INTERVAL_MS = 60_000L
private const val COVER_REQUESTS_AT_ONCE = 3
private const val HISTORY_READ_ATTEMPTS = 3
private const val HISTORY_READ_DELAY_MS = 7_000L

/** What the whole app shares: where the user is, the account's library, and what to do with an item. */
class AppState(val player: Player, val scope: CoroutineScope, val onSignOut: () -> Unit) {
    private val history = mutableStateListOf<Route>(Route.Home)
    private var historyIndex by mutableIntStateOf(0)

    val route: Route get() = history[historyIndex]
    val canGoBack: Boolean get() = historyIndex > 0
    val canGoForward: Boolean get() = historyIndex < history.lastIndex

    var toast by mutableStateOf<String?>(null)
        private set
    private var toastJob: Job? = null

    /** A text field has focus: plain keys type instead of controlling playback. */
    var typing by mutableStateOf(false)
    var currentId by mutableStateOf<String?>(null)
    var searchFocusRequests by mutableIntStateOf(0)
    var nowPlayingOpen by mutableStateOf(false)
    var sidePanel by mutableStateOf<SidePanel?>(null)

    /** The user closed the panel: playing the next song doesn't bring it back. */
    var sidePanelDismissed by mutableStateOf(false)
    var miniPlayer by mutableStateOf(false)
    var fullscreen by mutableStateOf(false)
        private set
    var editing by mutableStateOf<PlaylistItem?>(null)
    var addingSongsTo by mutableStateOf<PlaylistItem?>(null)
    var collaborating by mutableStateOf<PlaylistItem?>(null)
    var importing by mutableStateOf(false)
    var newPlaylistFor by mutableStateOf<List<Track>?>(null)
    var addToPlaylistFor by mutableStateOf<List<Track>?>(null)
    var renaming by mutableStateOf<PlaylistItem?>(null)

    var profile by mutableStateOf<AccountInfo?>(null)
        private set
    var playlists by mutableStateOf<List<PlaylistItem>>(emptyList())
        private set
    val home = HomeModel()

    /** Browse tile covers by category; empty means there was none to find. */
    val categoryCovers = mutableStateMapOf<String, String>()
    private val coverRequests = ConcurrentHashMap.newKeySet<String>()
    private val coverPermits = Semaphore(COVER_REQUESTS_AT_ONCE)
    var likedIds by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Playlists and albums in the account's library, by playlist id. */
    var savedIds by mutableStateOf<Set<String>>(emptySet())
        private set
    var accountHistory by mutableStateOf<List<SongItem>>(emptyList())
        private set

    /** Bumped when the account's history changed. */
    var historyVersion by mutableIntStateOf(0)
        private set
    private var lastSyncAt = 0L

    init {
        player.onPlayReported = {
            scope.launch(Dispatchers.IO) {
                repeat(HISTORY_READ_ATTEMPTS) {
                    delay(HISTORY_READ_DELAY_MS)
                    refreshHistory()
                }
            }
        }
    }

    /** Bumped when a playlist changed on YouTube, so open pages load it again. */
    var libraryVersion by mutableIntStateOf(0)
        private set

    fun go(route: Route) {
        if (route == this.route) return
        nowPlayingOpen = false
        while (history.lastIndex > historyIndex) history.removeLast()
        history.add(route)
        historyIndex = history.lastIndex
    }

    fun back() {
        if (canGoBack) historyIndex--
    }

    fun forward() {
        if (canGoForward) historyIndex++
    }

    fun showToast(message: String) {
        toast = message
        toastJob?.cancel()
        toastJob =
            scope.launch {
                delay(TOAST_MS)
                toast = null
            }
    }

    fun toggleFullscreen() = changeFullscreen(!fullscreen)

    fun changeFullscreen(on: Boolean) {
        fullscreen = on
        WindowChrome.setFullscreen(on)
    }

    fun search(query: String) {
        val text = query.trim()
        if (text.isNotEmpty()) {
            com.ermusic.desktop.data.RecentSearches.add(text)
            go(Route.Search(text))
        }
    }

    fun loadAccount() {
        scope.launch(Dispatchers.IO) {
            if (YouTube.visitorData == null) YouTube.visitorData = YouTube.visitorData().getOrNull()
            if (YouTube.cookie == null) return@launch
            profile = YouTube.accountInfo().getOrNull()
            syncFromAccount(force = true)
        }
    }

    /** Brings in what changed on the account elsewhere, such as on the phone. */
    fun syncFromAccount(force: Boolean = false) {
        if (YouTube.cookie == null) return
        val now = System.currentTimeMillis()
        if (!force && now - lastSyncAt < SYNC_INTERVAL_MS) return
        lastSyncAt = now
        scope.launch(Dispatchers.IO) {
            val before = playlists to likedIds to savedIds
            refreshPlaylists()
            refreshLiked()
            refreshHistory()
            if (before != (playlists to likedIds to savedIds)) libraryVersion++
        }
    }

    private suspend fun refreshHistory() {
        val page = YouTube.musicHistory().getOrNull() ?: return
        val songs = page.sections.orEmpty().flatMap { it.songs }
        if (songs.map { it.id } != accountHistory.map { it.id }) {
            accountHistory = songs
            historyVersion++
        }
    }

    fun removeFromHistory(token: String) {
        confirmed("Removed from history", "Couldn't remove it", { YouTube.feedback(listOf(token)) }) {
            refreshHistory()
            historyVersion++
        }
    }

    fun isSaved(playlistId: String) = playlistId in savedIds

    fun toggleSaved(playlistId: String) {
        val save = !isSaved(playlistId)
        confirmed(if (save) "Saved to Library" else "Removed from Library", "Couldn't update your Library", { YouTube.likePlaylist(playlistId, save) }) {
            savedIds = if (save) savedIds + playlistId else savedIds - playlistId
            refreshPlaylists()
            libraryVersion++
        }
    }

    private suspend fun refreshPlaylists() {
        YouTube.library("FEmusic_liked_playlists").onSuccess { page ->
            playlists = page.items.filterIsInstance<PlaylistItem>().filter { it.id != LIKED_PLAYLIST_ID && it.id != "SE" }
        }
        val albums = YouTube.library("FEmusic_liked_albums").getOrNull()?.items.orEmpty().filterIsInstance<AlbumItem>()
        savedIds = playlists.mapTo(HashSet()) { it.id } + albums.map { it.playlistId }
    }

    private suspend fun refreshLiked() {
        val first = YouTube.playlist(LIKED_PLAYLIST_ID).getOrNull() ?: return
        val ids = first.songs.mapTo(HashSet()) { it.id }
        var next = first.songsContinuation
        while (next != null && ids.size < LIKED_SONGS_LIMIT) {
            val more = YouTube.playlistContinuation(next).getOrNull() ?: break
            more.songs.mapTo(ids) { it.id }
            next = more.continuation
        }
        likedIds = ids
    }

    /** Runs an account change, and tells the user how it went only once YouTube has answered. */
    private fun confirmed(done: String, failed: String, call: suspend () -> Result<*>, orElse: () -> Unit = {}, then: suspend () -> Unit = {}) {
        scope.launch(Dispatchers.IO) {
            if (call().isSuccess) {
                then()
                showToast(done)
            } else {
                orElse()
                showToast(failed)
            }
        }
    }

    fun toggleLike(track: Track) {
        val like = track.id !in likedIds
        confirmed(if (like) "Added to Liked songs" else "Removed from Liked songs", "Couldn't update Liked songs", { YouTube.likeVideo(track.id, like) }) {
            likedIds = if (like) likedIds + track.id else likedIds - track.id
            libraryVersion++
        }
    }

    fun addToPlaylist(playlist: PlaylistItem, tracks: List<Track>) {
        confirmed("Added to ${playlist.title}", "Couldn't add to ${playlist.title}", {
            runCatching { tracks.forEach { YouTube.addToPlaylist(playlist.id, it.id).getOrThrow() } }
        }) { libraryVersion++ }
    }

    fun createPlaylist(title: String, tracks: List<Track>?) {
        scope.launch(Dispatchers.IO) {
            val id = runCatching { YouTube.createPlaylist(title) }.getOrNull()
            if (id == null) {
                showToast("Couldn't create the playlist")
                return@launch
            }
            tracks?.forEach { YouTube.addToPlaylist(id, it.id) }
            refreshPlaylists()
            libraryVersion++
            showToast("Created $title")
        }
    }

    fun renamePlaylist(playlist: PlaylistItem, title: String) {
        confirmed("Renamed", "Couldn't rename the playlist", { YouTube.renamePlaylist(playlist.id, title) }) {
            refreshPlaylists()
            libraryVersion++
        }
    }

    fun deletePlaylist(playlist: PlaylistItem) {
        confirmed("Deleted ${playlist.title}", "Couldn't delete the playlist", { YouTube.deletePlaylist(playlist.id) }) {
            refreshPlaylists()
            libraryVersion++
            if (route == Route.Playlist(playlist.id)) back()
        }
    }

    fun openRadio(title: String, art: String?, endpoint: WatchEndpoint) = go(Route.Radio("$title radio", art, endpoint))

    fun openRadio(track: Track) = openRadio(track.title, track.artUrl, WatchEndpoint(videoId = track.id))

    /**
     * Puts a song before [successor] (null: at the end) once YouTube accepts it. [onMoved] then
     * updates the page's own copy, so it keeps its scroll; without it the page loads again.
     */
    fun moveInPlaylist(playlistId: String, setVideoId: String, successor: String?, onFailed: () -> Unit = {}, onMoved: (() -> Unit)? = null) {
        confirmed("Moved", "Couldn't move the song", { YouTube.moveSongPlaylist(playlistId, setVideoId, successor) }, orElse = onFailed) {
            if (onMoved != null) onMoved() else libraryVersion++
        }
    }

    /** After an edit YouTube accepted, by the editor. */
    fun playlistChanged() {
        scope.launch(Dispatchers.IO) {
            refreshPlaylists()
            libraryVersion++
        }
    }

    fun removeFromPlaylist(playlistId: String, track: Track) {
        val setVideoId = track.setVideoId ?: return
        confirmed("Removed from playlist", "Couldn't remove the song", { YouTube.removeFromPlaylist(playlistId, track.id, setVideoId) }) { libraryVersion++ }
    }

    fun follow(channelId: String, follow: Boolean, onDone: () -> Unit = {}) {
        confirmed(if (follow) "Following" else "Unfollowed", "Couldn't update", { YouTube.subscribeChannel(channelId, follow) }) { onDone() }
    }

    fun download(tracks: List<Track>) {
        scope.launch(Dispatchers.IO) {
            showToast(if (tracks.size == 1) "Downloading…" else "Downloading ${tracks.size} songs…")
            val failed = tracks.count { track -> runCatching { Downloads.add(track) }.isFailure }
            showToast(if (failed == 0) "Downloaded" else "$failed couldn't be downloaded")
        }
    }

    fun copyLink(link: String) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(link), null)
        showToast("Link copied")
    }

    /** An artist's or a playlist owner's page; a name without an id is looked up first. */
    fun openArtist(credit: Credit) {
        if (fullscreen) changeFullscreen(false)
        miniPlayer = false
        val id = credit.id
        if (id != null) {
            go(Route.Artist(id))
            return
        }
        scope.launch(Dispatchers.IO) {
            val match =
                YouTube.search(credit.name, YouTube.SearchFilter.FILTER_ARTIST).getOrNull()?.items.orEmpty().filterIsInstance<ArtistItem>()
                    .firstOrNull { it.title.equals(credit.name, ignoreCase = true) }
            if (match != null) go(Route.Artist(match.id)) else showToast("Couldn't find ${credit.name}")
        }
    }

    /** Fetches a Browse tile's cover once: the first picture in its category, as on the phone. */
    fun loadCategoryCover(browseId: String, params: String?) {
        val key = categoryKey(browseId, params)
        if (!coverRequests.add(key)) return
        scope.launch(Dispatchers.IO) {
            coverPermits.withPermit {
                val cover = YouTube.browse(browseId, params).getOrNull()?.items?.firstNotNullOfOrNull { section -> section.items.firstNotNullOfOrNull { it.thumbnail } }
                categoryCovers[key] = cover.orEmpty()
            }
        }
    }

    fun categoryKey(browseId: String, params: String?) = browseId + "_" + params

    fun open(item: YTItem) {
        when (item) {
            is AlbumItem -> go(Route.Album(item.browseId))
            is PlaylistItem -> go(Route.Playlist(item.id))
            is PodcastItem -> go(Route.Playlist(item.id))
            is ArtistItem -> go(Route.Artist(item.id))
            is SongItem, is EpisodeItem -> activate(item)
        }
    }

    /** A click: a song plays with its radio, anything else opens. */
    fun activate(item: YTItem) {
        when (item) {
            is SongItem -> player.playRadio(item.toTrack())
            is EpisodeItem -> player.playRadio(item.asSongItem().toTrack())
            else -> open(item)
        }
    }

    /** The songs an item stands for. */
    suspend fun tracksOf(item: YTItem): List<Track> =
        when (item) {
            is SongItem -> listOf(item.toTrack())
            is EpisodeItem -> listOf(item.asSongItem().toTrack())
            is AlbumItem -> YouTube.album(item.browseId).getOrNull()?.songs.orEmpty().map { it.toTrack() }
            is PlaylistItem -> playlistSongs(item.id).map { it.toTrack() }
            is PodcastItem -> playlistSongs(item.id).map { it.toTrack() }
            is ArtistItem -> emptyList()
        }

    suspend fun playlistSongs(id: String): List<SongItem> {
        val first = YouTube.playlist(id).getOrNull() ?: return emptyList()
        var songs = first.songs
        var next = first.songsContinuation
        while (next != null && songs.size < PLAYLIST_LIMIT) {
            val more = YouTube.playlistContinuation(next).getOrNull() ?: break
            songs = songs + more.songs
            next = more.continuation
        }
        return songs.distinctBy { it.setVideoId ?: it.id }
    }

    /** Plays an album or playlist from anywhere, or an artist's radio. */
    fun playItem(item: YTItem, shuffle: Boolean = false) {
        if (item is ArtistItem) {
            (if (shuffle) item.shuffleEndpoint else item.playEndpoint ?: item.radioEndpoint)?.let(player::playEndpoint)
            return
        }
        withTracks(item) { player.play(if (shuffle) it.shuffled() else it) }
    }

    fun withTracks(item: YTItem, use: (List<Track>) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val tracks = tracksOf(item)
            if (tracks.isEmpty()) showToast("Nothing to play here") else use(tracks)
        }
    }
}
