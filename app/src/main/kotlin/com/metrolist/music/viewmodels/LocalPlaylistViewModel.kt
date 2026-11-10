/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import kotlinx.coroutines.Job
import com.metrolist.music.utils.SyncUtils
import com.metrolist.music.utils.PlaylistEditEvents
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.music.constants.AccountImageUrlKey
import com.metrolist.music.constants.HideVideoSongsKey
import com.metrolist.music.constants.PlaylistSongSortDescendingKey
import com.metrolist.music.constants.PlaylistSongSortType
import com.metrolist.music.constants.PlaylistSongSortTypeKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.PlaylistSong
import com.metrolist.music.extensions.reversed
import com.metrolist.music.extensions.toEnum
import com.metrolist.music.utils.Collaborations
import com.metrolist.music.utils.YouTubePlaylistEditor
import com.metrolist.music.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class LocalPlaylistViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val syncUtils: SyncUtils,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val playlistId = savedStateHandle.get<String>("playlistId")!!
    val playlist =
        database
            .playlist(playlistId)
            .stateIn(viewModelScope, SharingStarted.Lazily, null)

    private val _onlinePlaylist = MutableStateFlow<PlaylistItem?>(null)
    val onlinePlaylist: StateFlow<PlaylistItem?> = _onlinePlaylist

    private val _people = MutableStateFlow<YouTubePlaylistEditor.PlaylistPeople?>(null)

    /** Whether this account may add songs (owner or collaborator) and who added each song. */
    val people: StateFlow<YouTubePlaylistEditor.PlaylistPeople?> = _people

    private val _collaboration = MutableStateFlow<YouTubePlaylistEditor.Collaboration?>(null)

    /**
     * The Collaborate panel of an owned playlist, read when the playlist opens: YouTube takes a few
     * seconds to answer it, so it is ready by the time Edit > Collaborate is opened.
     */
    val collaboration: StateFlow<YouTubePlaylistEditor.Collaboration?> = _collaboration

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    /** Pull to refresh: the songs from YouTube, then its page (description, collaborators). */
    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val browseId = playlist.first { it != null }?.playlist?.browseId
                if (browseId != null) withContext(Dispatchers.IO) { syncUtils.syncPlaylistNow(browseId, playlistId) }
                refreshOnline().join()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /** The description YouTube accepted from this screen; its playlist page can lag behind it. */
    private var savedDescription: String? = null
    val playlistSongs: StateFlow<List<PlaylistSong>> =
        combine(
            database.playlistSongs(playlistId),
            context.dataStore.data
                .map {
                    Triple(
                        it[PlaylistSongSortTypeKey].toEnum(PlaylistSongSortType.CUSTOM),
                        it[PlaylistSongSortDescendingKey] ?: true,
                        it[HideVideoSongsKey] ?: false
                    )
                }.distinctUntilChanged(),
        ) { songs, (sortType, sortDescending, hideVideoSongs) ->
            val filteredSongs = if (hideVideoSongs) {
                songs.filter { !it.song.song.isVideo }
            } else {
                songs
            }
            when (sortType) {
                PlaylistSongSortType.CUSTOM -> filteredSongs
                PlaylistSongSortType.CREATE_DATE -> filteredSongs.sortedBy { it.map.id }
                PlaylistSongSortType.NAME -> {
                    val collator = Collator.getInstance(Locale.getDefault())
                    collator.strength = Collator.PRIMARY
                    filteredSongs.sortedWith(compareBy(collator) { it.song.song.title })
                }
                PlaylistSongSortType.ARTIST -> {
                    val collator = Collator.getInstance(Locale.getDefault())
                    collator.strength = Collator.PRIMARY
                    filteredSongs.sortedWith(
                        compareBy<PlaylistSong, String>(collator) { song -> song.song.artists.joinToString("") { it.name } }
                            .thenBy(collator) { it.song.album?.title.orEmpty() },
                    )
                }

                PlaylistSongSortType.PLAY_TIME -> filteredSongs.sortedBy { it.song.song.totalPlayTime }
            }.reversed(sortDescending && sortType != PlaylistSongSortType.CUSTOM)
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        viewModelScope.launch {
            val sortedSongs =
                playlistSongs.first().sortedWith(compareBy({ it.map.position }, { it.map.id }))
            database.transaction {
                sortedSongs.forEachIndexed { index, playlistSong ->
                    if (playlistSong.map.position != index) {
                        update(playlistSong.map.copy(position = index))
                    }
                }
            }
        }

        refreshOnline()

        // Opened with no songs here while YouTube has some (just synced in, a new device): fill it
        // now, with the loading indicator, instead of showing an empty playlist.
        viewModelScope.launch {
            val entity = playlist.first { it != null }?.playlist ?: return@launch
            if (entity.browseId == null || entity.remoteSongCount == 0) return@launch
            val empty = withContext(Dispatchers.IO) { database.playlistSongs(playlistId).first().isEmpty() }
            if (empty) refresh()
        }

        // An edit saved from anywhere (header, menu) shows here at once, then the page reloads.
        viewModelScope.launch {
            PlaylistEditEvents.saved.collect { edit ->
                if (edit.playlistId == playlistId) {
                    saved(edit.description)
                    refreshOnline()
                }
            }
        }
    }

    /** Reloads the YouTube copy (description etc.), e.g. right after an edit. */
    fun refreshOnline(): Job {
        val page = viewModelScope.launch {
            val localPlaylist = playlist.first { it != null }
            val browseId = localPlaylist?.playlist?.browseId
            if (browseId != null) {
                val page = withContext(Dispatchers.IO) {
                    YouTube.playlist(browseId).getOrNull()
                }
                val online = page?.playlist
                _onlinePlaylist.value = savedDescription?.let { online?.copy(description = it) } ?: online
            }
        }
        val people = refreshPeople()
        return viewModelScope.launch {
            page.join()
            people.join()
        }
    }

    /** Shows an edit YouTube accepted right away, without waiting for its playlist page to catch up. */
    fun saved(description: String) {
        savedDescription = description
        _onlinePlaylist.value = _onlinePlaylist.value?.copy(description = description)
    }

    fun collaborationChanged(collaboration: YouTubePlaylistEditor.Collaboration?) {
        _collaboration.value = collaboration
    }

    /** Re-reads who may add songs and who added what, e.g. after songs were added. */
    fun refreshPeople(): Job =
        viewModelScope.launch(Dispatchers.IO) {
            val entity = playlist.first { it != null }?.playlist ?: return@launch
            val browseId = entity.browseId ?: return@launch
            // Read now so Edit opens with the description and visibility already there; it shares
            // the page request with people() below.
            if (entity.isEditable) viewModelScope.launch(Dispatchers.IO) { YouTubePlaylistEditor.details(browseId) }
            if (entity.isEditable && _collaboration.value == null) {
                // Not waited for by a pull to refresh: YouTube takes seconds to answer it.
                viewModelScope.launch(Dispatchers.IO) { YouTubePlaylistEditor.collaboration(browseId)?.let { _collaboration.value = it } }
            }
            val accountImage = context.dataStore.data.first()[AccountImageUrlKey]
            val people = YouTubePlaylistEditor.people(browseId, accountImage) ?: return@launch
            _people.value = people
            // A joined collaboration is remembered, so its invite link later just opens it.
            if (people.canAddSongs && !entity.isEditable) Collaborations.markJoined(context, browseId)
        }
}
