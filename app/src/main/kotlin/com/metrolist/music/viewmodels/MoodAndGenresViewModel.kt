/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.pages.MoodAndGenres
import com.metrolist.music.constants.HideExplicitKey
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import com.metrolist.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

/** A row of recommendations on the search page. */
data class DiscoverShelf(
    val title: String,
    val items: List<YTItem>,
)

/**
 * Search discover: recommendation shelves (new releases and charts) plus the "Browse all"
 * mood and genre cards. Each card gets a cover from the first playlist in its category,
 * fetched lazily when the card is shown.
 */
@HiltViewModel
class MoodAndGenresViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val moodAndGenres = MutableStateFlow<List<MoodAndGenres>?>(null)
    val shelves = MutableStateFlow<List<DiscoverShelf>>(emptyList())

    /** Card cover by category key; an empty string means "looked, found nothing". */
    val covers = mutableStateMapOf<String, String>()
    private val coverRequests = mutableSetOf<String>()
    private val coverPermits = Semaphore(3)

    init {
        reload()
    }

    fun reload() {
        moodAndGenres.value = null
        viewModelScope.launch {
            YouTube
                .moodAndGenres()
                .onSuccess {
                    moodAndGenres.value = it
                }.onFailure {
                    // An empty list means "loaded but failed" so the screen can offer a retry.
                    moodAndGenres.value = emptyList()
                    reportException(it)
                }
        }
        viewModelScope.launch { loadShelves() }
    }

    private suspend fun loadShelves() {
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val result = mutableListOf<DiscoverShelf>()
        YouTube.getChartsPage().onSuccess { page ->
            page.sections
                .filter { it.items.isNotEmpty() }
                .take(3)
                .forEach { result += DiscoverShelf(it.title, it.items) }
        }.onFailure(::reportException)
        if (result.none { shelf -> shelf.items.any { it is com.metrolist.innertube.models.AlbumItem } }) {
            YouTube.newReleaseAlbums().onSuccess { albums ->
                if (albums.isNotEmpty()) result.add(0, DiscoverShelf("", albums))
            }
        }
        shelves.value = result.map { shelf ->
            if (hideExplicit) shelf.copy(items = shelf.items.filterNot { it.explicit }) else shelf
        }.filter { it.items.isNotEmpty() }
    }

    fun loadCover(
        key: String,
        browseId: String,
        params: String?,
    ) {
        if (!coverRequests.add(key)) return
        viewModelScope.launch {
            coverPermits.withPermit {
                val thumb = YouTube.browse(browseId, params).getOrNull()
                    ?.items
                    ?.firstNotNullOfOrNull { section -> section.items.firstNotNullOfOrNull { it.thumbnail } }
                covers[key] = thumb.orEmpty()
            }
        }
    }
}
