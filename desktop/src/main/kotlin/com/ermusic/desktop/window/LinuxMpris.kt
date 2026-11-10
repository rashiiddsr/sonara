/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

@file:Suppress("FunctionName")

package com.ermusic.desktop.window

import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.errors.UnknownProperty
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.messages.DBusSignal
import org.freedesktop.dbus.types.Variant
import java.io.File

/** MPRIS's root interface: who the player is. */
@DBusInterfaceName("org.mpris.MediaPlayer2")
interface MediaPlayer2 : DBusInterface {
    fun Raise()

    fun Quit()
}

/** MPRIS's player interface: what the desktop's media controls and the media keys call. */
@DBusInterfaceName("org.mpris.MediaPlayer2.Player")
interface MediaPlayer2Player : DBusInterface {
    fun Next()

    fun Previous()

    fun Pause()

    fun PlayPause()

    fun Stop()

    fun Play()

    fun Seek(offset: Long)

    fun SetPosition(trackId: DBusPath, position: Long)

    fun OpenUri(uri: String)

    /** Sent when the song jumped, so the controls don't have to keep asking where it is. */
    class Seeked(path: String, val position: Long) : DBusSignal(path, position)
}

/** What the controls can ask of the app. */
class MprisActions(
    val playPause: () -> Unit,
    val play: () -> Unit,
    val pause: () -> Unit,
    val next: () -> Unit,
    val previous: () -> Unit,
    val seek: (Long) -> Unit,
    val raise: () -> Unit,
)

/**
 * Sonara on Linux's session bus as an MPRIS player: GNOME's and KDE's media controls show the
 * song and its cover, and the keyboard's media keys reach the app through them. Times on the bus
 * are microseconds.
 */
class MprisPlayer(private val bus: DBusConnection, private val actions: MprisActions) : MediaPlayer2, MediaPlayer2Player, Properties {
    private data class Song(val id: String?, val title: String?, val artist: String?, val artUrl: String?, val durationMs: Long)

    private data class Clock(val positionMs: Long, val at: Long, val playing: Boolean)

    @Volatile private var song = Song(null, null, null, null, 0)

    @Volatile private var clock = Clock(0, System.currentTimeMillis(), false)

    init {
        bus.exportObject(PATH, this)
        bus.requestBusName(BUS_NAME)
    }

    override fun getObjectPath(): String = PATH

    // ---- What the app tells the bus ----

    fun show(id: String?, title: String?, artist: String?, artUrl: String?, durationMs: Long, positionMs: Long, playing: Boolean) {
        val changedSong = song.id != id || song.durationMs != durationMs || song.title != title
        song = Song(id, title, artist, artUrl, durationMs)
        clock = Clock(positionMs, System.currentTimeMillis(), playing && id != null)
        val player = playerProperties()
        val changed =
            if (changedSong) {
                player.filterKeys { it in SONG_PROPERTIES }
            } else {
                player.filterKeys { it == "PlaybackStatus" }
            }
        emit(Properties.PropertiesChanged(PATH, PLAYER, changed, emptyList()))
    }

    fun seeked(positionMs: Long) {
        clock = clock.copy(positionMs = positionMs, at = System.currentTimeMillis())
        emit(MediaPlayer2Player.Seeked(PATH, positionMs * US_PER_MS))
    }

    fun close() {
        runCatching { bus.releaseBusName(BUS_NAME) }
        runCatching { bus.close() }
    }

    private fun emit(signal: DBusSignal) {
        runCatching { bus.sendMessage(signal) }
    }

    private fun positionMs(): Long {
        val now = clock
        val running = if (now.playing) now.positionMs + (System.currentTimeMillis() - now.at) else now.positionMs
        val duration = song.durationMs
        return if (duration > 0) running.coerceIn(0, duration) else running.coerceAtLeast(0)
    }

    // ---- org.mpris.MediaPlayer2 ----

    override fun Raise() = actions.raise()

    override fun Quit() = Unit

    // ---- org.mpris.MediaPlayer2.Player ----

    override fun Next() = actions.next()

    override fun Previous() = actions.previous()

    override fun Pause() = actions.pause()

    override fun PlayPause() = actions.playPause()

    override fun Stop() = actions.pause()

    override fun Play() = actions.play()

    override fun Seek(offset: Long) {
        if (song.id == null) return
        actions.seek((positionMs() + offset / US_PER_MS).coerceAtLeast(0))
    }

    override fun SetPosition(trackId: DBusPath, position: Long) {
        val id = song.id ?: return
        if (trackId.path != trackPath(id) || position < 0) return
        if (song.durationMs > 0 && position / US_PER_MS > song.durationMs) return
        actions.seek(position / US_PER_MS)
    }

    override fun OpenUri(uri: String) = Unit

    // ---- org.freedesktop.DBus.Properties ----

    @Suppress("UNCHECKED_CAST")
    override fun <A : Any?> Get(interfaceName: String, propertyName: String): A =
        (GetAll(interfaceName)[propertyName] ?: throw UnknownProperty("No property $propertyName on $interfaceName")) as A

    override fun <A : Any?> Set(interfaceName: String, propertyName: String, value: A) = Unit

    override fun GetAll(interfaceName: String): Map<String, Variant<*>> =
        when (interfaceName) {
            ROOT -> ROOT_PROPERTIES
            PLAYER -> playerProperties()
            else -> emptyMap()
        }

    private fun playerProperties(): Map<String, Variant<*>> {
        val now = song
        val hasSong = now.id != null
        return linkedMapOf(
            "PlaybackStatus" to Variant(if (!hasSong) "Stopped" else if (clock.playing) "Playing" else "Paused"),
            "Metadata" to Variant(metadata(now), "a{sv}"),
            "Position" to Variant(positionMs() * US_PER_MS),
            "Rate" to Variant(1.0),
            "MinimumRate" to Variant(1.0),
            "MaximumRate" to Variant(1.0),
            "Volume" to Variant(1.0),
            "CanGoNext" to Variant(hasSong),
            "CanGoPrevious" to Variant(hasSong),
            "CanPlay" to Variant(hasSong),
            "CanPause" to Variant(hasSong),
            "CanSeek" to Variant(hasSong && now.durationMs > 0),
            "CanControl" to Variant(true),
        )
    }

    private fun metadata(now: Song): Map<String, Variant<*>> {
        val id = now.id ?: return mapOf("mpris:trackid" to Variant(DBusPath(NO_TRACK)))
        return buildMap {
            put("mpris:trackid", Variant(DBusPath(trackPath(id))))
            if (now.durationMs > 0) put("mpris:length", Variant(now.durationMs * US_PER_MS))
            now.title?.let { put("xesam:title", Variant(it)) }
            now.artist?.let { put("xesam:artist", Variant(listOf(it), "as")) }
            now.artUrl?.let { put("mpris:artUrl", Variant(it)) }
        }
    }

    companion object {
        const val BUS_NAME = "org.mpris.MediaPlayer2.sonara"
        const val PATH = "/org/mpris/MediaPlayer2"
        const val ROOT = "org.mpris.MediaPlayer2"
        const val PLAYER = "org.mpris.MediaPlayer2.Player"
        private const val NO_TRACK = "/org/mpris/MediaPlayer2/TrackList/NoTrack"
        private const val US_PER_MS = 1_000L
        private val ROOT_PROPERTIES: Map<String, Variant<*>> =
            mapOf(
                "CanQuit" to Variant(false),
                "CanRaise" to Variant(true),
                "HasTrackList" to Variant(false),
                "Identity" to Variant("Sonara"),
                "DesktopEntry" to Variant(LinuxDesktop.DESKTOP_ENTRY),
                "SupportedUriSchemes" to Variant(emptyList<String>(), "as"),
                "SupportedMimeTypes" to Variant(emptyList<String>(), "as"),
            )
        private val SONG_PROPERTIES = setOf("PlaybackStatus", "Metadata", "CanGoNext", "CanGoPrevious", "CanPlay", "CanPause", "CanSeek")

        /** An object path may hold letters, digits and _ only; YouTube's ids also use - . */
        fun trackPath(id: String): String = "/app/sonara/track/" + id.map { if (it.isLetterOrDigit() && it.code < 128) it else '_' }.joinToString("")
    }
}

/** The one player on the user's session bus, when there is one. */
object LinuxMpris {
    @Volatile private var player: MprisPlayer? = null

    fun listen(actions: MprisActions) {
        if (player != null) return
        player =
            runCatching { MprisPlayer(sessionBus(), actions) }
                .onFailure { System.err.println("Sonara: no media controls, the session bus couldn't be reached ($it)") }
                .getOrNull()
    }

    /**
     * The session bus by its socket file. Java can't reach the "abstract" sockets some older setups
     * name first; the desktop's own bus, in the user's runtime folder, is the same bus by a path.
     */
    private fun sessionBus(): DBusConnection {
        val named = System.getenv("DBUS_SESSION_BUS_ADDRESS").orEmpty().split(';').firstOrNull { it.startsWith("unix:path=") }
        val runtime = System.getenv("XDG_RUNTIME_DIR")?.let { File(it, "bus") }?.takeIf { it.exists() }?.let { "unix:path=${it.path}" }
        val address = named ?: runtime ?: return DBusConnectionBuilder.forSessionBus().build()
        return DBusConnectionBuilder.forAddress(address).build()
    }

    fun show(id: String?, title: String?, artist: String?, artUrl: String?, durationMs: Long, positionMs: Long, playing: Boolean) {
        player?.show(id, title, artist, artUrl, durationMs, positionMs, playing)
    }

    fun seeked(positionMs: Long) {
        player?.seeked(positionMs)
    }
}
