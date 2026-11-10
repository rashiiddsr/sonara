/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ermusic.desktop.data

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.ermusic.desktop.AppFiles
import com.ermusic.desktop.playback.SongCache
import com.ermusic.desktop.playback.Track
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Properties

/** A setting that survives restarts. */
class Pref<T>(private val key: String, default: T, private val parse: (String) -> T) {
    private val state = mutableStateOf(Settings.stored(key)?.let(parse) ?: default)

    var value: T
        get() = state.value
        set(new) {
            state.value = new
            Settings.store(key, new.toString())
        }
}

object Settings {
    private val file = File(AppFiles.root, "settings.properties")
    private val values = Properties().apply { if (file.exists()) file.inputStream().use(::load) }

    fun stored(key: String): String? = values.getProperty(key)

    @Synchronized
    fun store(key: String, value: String) {
        values.setProperty(key, value)
        file.outputStream().use { values.store(it, null) }
    }

    val translator = Pref("translator", false, String::toBoolean)
    val translateLanguage = Pref("translate_language", "en") { it }
    val normalize = Pref("normalize", true, String::toBoolean)
    val highQuality = Pref("high_quality", true, String::toBoolean)
    val closeToTray = Pref("close_to_tray", false, String::toBoolean)
    val volume = Pref("volume", 1f, String::toFloat)
    val crossfadeSeconds = Pref("crossfade_seconds", 0, String::toInt)
    val autoplay = Pref("autoplay", true, String::toBoolean)
    val hideExplicit = Pref("hide_explicit", false, String::toBoolean)
    val hideVideos = Pref("hide_videos", false, String::toBoolean)
    val theme = Pref("theme", "dark") { it }
    val openNowPlaying = Pref("open_now_playing", true, String::toBoolean)
    val mono = Pref("mono", false, String::toBoolean)

    /** Megabytes of songs kept to play again at once (and offline); downloads don't count. */
    val songCacheMb = Pref("song_cache_mb", 1024, String::toInt)
    val eqEnabled = Pref("eq_enabled", false, String::toBoolean)
    val eqGains = Pref("eq_gains", "0,0,0,0,0,0,0,0,0,0") { it }
}

private val json = Json { ignoreUnknownKeys = true }

private fun <T> readList(file: File, read: (String) -> List<T>): List<T> =
    runCatching { read(file.readText()) }.getOrDefault(emptyList())

/** What was played on this computer, newest first. */
object History {
    private const val LIMIT = 300
    private val serializer = ListSerializer(Track.serializer())
    private val file = File(AppFiles.root, "history.json")
    val tracks = mutableStateListOf<Track>().apply { addAll(readList(file) { json.decodeFromString(serializer, it) }) }

    fun add(track: Track) {
        tracks.removeAll { it.id == track.id }
        tracks.add(0, track)
        while (tracks.size > LIMIT) tracks.removeLast()
        file.writeText(json.encodeToString(serializer, tracks.toList()))
    }
}

/** Songs the user pinned to the top of Home. */
object SpeedDial {
    private const val LIMIT = 8
    private val serializer = ListSerializer(Track.serializer())
    private val file = File(AppFiles.root, "speed_dial.json")
    val tracks = mutableStateListOf<Track>().apply { addAll(readList(file) { json.decodeFromString(serializer, it) }) }

    private fun save() = file.writeText(json.encodeToString(serializer, tracks.toList()))

    fun has(id: String) = tracks.any { it.id == id }

    fun toggle(track: Track) {
        if (!tracks.removeAll { it.id == track.id }) {
            tracks.add(0, track)
            while (tracks.size > LIMIT) tracks.removeLast()
        }
        save()
    }
}

/** The last things searched for. */
object RecentSearches {
    private const val LIMIT = 10
    private val serializer = ListSerializer(String.serializer())
    private val file = File(AppFiles.root, "searches.json")
    val queries = mutableStateListOf<String>().apply { addAll(readList(file) { json.decodeFromString(serializer, it) }) }

    private fun save() = file.writeText(json.encodeToString(serializer, queries.toList()))

    fun add(query: String) {
        queries.removeAll { it.equals(query, ignoreCase = true) }
        queries.add(0, query)
        while (queries.size > LIMIT) queries.removeLast()
        save()
    }

    fun remove(query: String) {
        queries.remove(query)
        save()
    }
}

/** Songs kept for offline use, outside the cache that trims itself. */
object Downloads {
    private val serializer = ListSerializer(Track.serializer())
    private val dir = File(AppFiles.root, "downloads").apply { mkdirs() }
    private val list = File(AppFiles.root, "downloads.json")
    val tracks = mutableStateListOf<Track>().apply { addAll(readList(list) { json.decodeFromString(serializer, it) }) }

    private fun save() = list.writeText(json.encodeToString(serializer, tracks.toList()))

    fun fileFor(id: String) = File(dir, "$id.audio")

    fun has(id: String) = tracks.any { it.id == id }

    suspend fun add(track: Track) {
        val song = SongCache.get(track.id)
        if (song.file != fileFor(track.id)) song.file.copyTo(fileFor(track.id), overwrite = true)
        if (!has(track.id)) tracks.add(0, track)
        save()
    }

    fun remove(id: String) {
        tracks.removeAll { it.id == id }
        fileFor(id).delete()
        save()
    }
}
