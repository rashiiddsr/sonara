/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import kotlinx.coroutines.launch
import com.metrolist.innertube.models.EpisodeItem
import com.metrolist.innertube.models.PodcastItem
import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/**
 * Songs, albums, artists, playlists, podcasts and episodes opened from search results, newest first, so the
 * search page can show them again like Spotify's recent searches. Kept in DataStore as a small
 * JSON list rather than a Room table, to leave the schema alone.
 */
object SearchRecents {
    private val Key = stringPreferencesKey("searchRecentItems")
    private const val MAX = 20

    fun flow(context: Context): Flow<List<YTItem>> =
        context.dataStore.data
            .map { it[Key].orEmpty() }
            .distinctUntilChanged()
            .map(::decode)

    /**
     * Remembers [item]. Runs on [appScope]: opening an album, artist or podcast leaves the search
     * screen at once, and a write tied to that screen was cancelled with it, so only songs (which
     * play without leaving) were kept.
     */
    fun add(context: Context, item: YTItem) {
        val entry = encode(item) ?: return
        appScope.launch {
            context.safeDataStoreEdit { prefs ->
                val kept = decodeRaw(prefs[Key].orEmpty()).filter { it.optString("id") != item.id }
                prefs[Key] = JSONArray(listOf(entry) + kept.take(MAX - 1)).toString()
            }
        }
    }

    suspend fun remove(context: Context, id: String) {
        context.safeDataStoreEdit { prefs ->
            prefs[Key] = JSONArray(decodeRaw(prefs[Key].orEmpty()).filter { it.optString("id") != id }).toString()
        }
    }

    suspend fun clear(context: Context) {
        context.safeDataStoreEdit { it.remove(Key) }
    }

    private fun encode(item: YTItem): JSONObject? {
        val o = JSONObject().put("id", item.id).put("title", item.title).put("thumb", item.thumbnail ?: "")
        return when (item) {
            is SongItem -> o.put("type", "song").put("artists", artistsJson(item.artists)).put("explicit", item.explicit)
                .put("albumId", item.album?.id ?: "").put("albumName", item.album?.name ?: "")
            is AlbumItem -> o.put("type", "album").put("playlistId", item.playlistId)
                .put("artists", artistsJson(item.artists.orEmpty())).put("year", item.year ?: 0).put("explicit", item.explicit)
            is ArtistItem -> o.put("type", "artist")
            is PlaylistItem -> o.put("type", if (item.isPodcast) "podcast" else "playlist")
                .put("author", item.author?.name ?: "").put("authorId", item.author?.id ?: "").put("count", item.songCountText ?: "")
            is PodcastItem -> o.put("type", "podcast")
                .put("author", item.author?.name ?: "").put("authorId", item.author?.id ?: "").put("count", item.episodeCountText ?: "")
            is EpisodeItem -> o.put("type", "episode").put("author", item.author?.name ?: "").put("authorId", item.author?.id ?: "")
                .put("podcastId", item.podcast?.id ?: "").put("podcastName", item.podcast?.name ?: "").put("duration", item.duration ?: 0)
            else -> null
        }
    }

    private fun artistsJson(artists: List<Artist>) =
        JSONArray(artists.map { JSONObject().put("name", it.name).put("id", it.id ?: "") })

    private fun artists(o: JSONObject): List<Artist> {
        val a = o.optJSONArray("artists") ?: return emptyList()
        return (0 until a.length()).map { i ->
            val x = a.getJSONObject(i)
            Artist(name = x.optString("name"), id = x.optString("id").ifEmpty { null })
        }
    }

    private fun decodeRaw(raw: String): List<JSONObject> =
        if (raw.isEmpty()) {
            emptyList()
        } else {
            runCatching { JSONArray(raw).let { a -> (0 until a.length()).map(a::getJSONObject) } }.getOrDefault(emptyList())
        }

    private fun decode(raw: String): List<YTItem> =
        decodeRaw(raw).mapNotNull { o ->
            val id = o.optString("id")
            val title = o.optString("title")
            val thumb = o.optString("thumb")
            runCatching {
                when (o.optString("type")) {
                    "song" -> SongItem(
                        id = id,
                        title = title,
                        artists = artists(o),
                        album = o.optString("albumId").takeIf { it.isNotEmpty() }
                            ?.let { com.metrolist.innertube.models.Album(name = o.optString("albumName"), id = it) },
                        thumbnail = thumb,
                        explicit = o.optBoolean("explicit"),
                    )
                    "album" -> AlbumItem(
                        browseId = id,
                        playlistId = o.optString("playlistId"),
                        title = title,
                        artists = artists(o),
                        year = o.optInt("year").takeIf { it > 0 },
                        thumbnail = thumb,
                        explicit = o.optBoolean("explicit"),
                    )
                    "artist" -> ArtistItem(
                        id = id,
                        title = title,
                        thumbnail = thumb.ifEmpty { null },
                        shuffleEndpoint = null,
                        radioEndpoint = null,
                    )
                    "podcast" -> PodcastItem(
                        id = id,
                        title = title,
                        author = o.optString("author").takeIf { it.isNotEmpty() }
                            ?.let { Artist(name = it, id = o.optString("authorId").ifEmpty { null }) },
                        episodeCountText = o.optString("count").ifEmpty { null },
                        thumbnail = thumb.ifEmpty { null },
                        playEndpoint = null,
                        shuffleEndpoint = null,
                    )
                    "episode" -> EpisodeItem(
                        id = id,
                        title = title,
                        author = o.optString("author").takeIf { it.isNotEmpty() }
                            ?.let { Artist(name = it, id = o.optString("authorId").ifEmpty { null }) },
                        podcast = o.optString("podcastId").takeIf { it.isNotEmpty() }
                            ?.let { com.metrolist.innertube.models.Album(name = o.optString("podcastName"), id = it) },
                        duration = o.optInt("duration").takeIf { it > 0 },
                        thumbnail = thumb,
                    )
                    "playlist" -> PlaylistItem(
                        id = id,
                        title = title,
                        author = o.optString("author").takeIf { it.isNotEmpty() }
                            ?.let { Artist(name = it, id = o.optString("authorId").ifEmpty { null }) },
                        songCountText = o.optString("count").ifEmpty { null },
                        thumbnail = thumb.ifEmpty { null },
                        playEndpoint = null,
                        shuffleEndpoint = null,
                        radioEndpoint = null,
                    )
                    else -> null
                }
            }.getOrNull()
        }
}
