/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ermusic.desktop.window

import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.bin.EmbeddedDBusDaemon
import org.freedesktop.dbus.connections.BusAddress
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.connections.transports.TransportBuilder
import org.freedesktop.dbus.connections.transports.TransportBuilder.SaslAuthMode
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.types.Variant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The MPRIS player on a D-Bus of its own (over TCP, so it runs on any system), asked the way
 * GNOME's and KDE's media controls ask: properties, the song's details, and the buttons.
 */
class MprisPlayerTest {
    private lateinit var daemon: EmbeddedDBusDaemon
    private lateinit var appBus: DBusConnection
    private lateinit var desktopBus: DBusConnection
    private lateinit var player: MprisPlayer
    private val calls = CopyOnWriteArrayList<String>()

    @Before
    fun start() {
        val address = BusAddress.of(TransportBuilder.createDynamicSession("TCP", true))
        daemon = EmbeddedDBusDaemon(address)
        daemon.setSaslAuthMode(SaslAuthMode.AUTH_ANONYMOUS)
        daemon.startInBackgroundAndWait(DAEMON_START_MS)
        val connectTo = BusAddress.of(address).removeParameter("listen")
        fun connect() = DBusConnectionBuilder.forAddress(connectTo).withShared(false).transportConfig().configureSasl().withAuthMode(SaslAuthMode.AUTH_ANONYMOUS).back().back().build()
        appBus = connect()
        desktopBus = connect()
        player =
            MprisPlayer(
                appBus,
                MprisActions(
                    playPause = { calls += "playPause" },
                    play = { calls += "play" },
                    pause = { calls += "pause" },
                    next = { calls += "next" },
                    previous = { calls += "previous" },
                    seek = { calls += "seek:$it" },
                    raise = { calls += "raise" },
                ),
            )
    }

    @After
    fun stop() {
        runCatching { desktopBus.close() }
        player.close()
        runCatching { daemon.close() }
    }

    private fun properties() = desktopBus.getRemoteObject(MprisPlayer.BUS_NAME, MprisPlayer.PATH, Properties::class.java)

    private fun controls() = desktopBus.getRemoteObject(MprisPlayer.BUS_NAME, MprisPlayer.PATH, MediaPlayer2Player::class.java)

    @Test
    fun saysWhoItIs() {
        val root = properties().GetAll(MprisPlayer.ROOT)
        assertEquals("Sonara", root["Identity"]?.value)
        assertEquals(LinuxDesktop.DESKTOP_ENTRY, root["DesktopEntry"]?.value)
        assertEquals("Stopped", properties().Get<Any>(MprisPlayer.PLAYER, "PlaybackStatus"))
    }

    @Test
    fun showsTheSongAndWhereItIs() {
        player.show("abc-123_X", "Song title", "The artist", "https://example.com/cover.jpg", 200_000, 30_000, playing = false)
        val all = properties().GetAll(MprisPlayer.PLAYER)
        assertEquals("Paused", all["PlaybackStatus"]?.value)
        assertEquals(30_000_000L, all["Position"]?.value)
        assertEquals(true, all["CanSeek"]?.value)
        @Suppress("UNCHECKED_CAST")
        val metadata = all["Metadata"]?.value as Map<String, Variant<*>>
        assertEquals("Song title", metadata["xesam:title"]?.value)
        assertEquals(listOf("The artist"), metadata["xesam:artist"]?.value)
        assertEquals(200_000_000L, metadata["mpris:length"]?.value)
        assertEquals("https://example.com/cover.jpg", metadata["mpris:artUrl"]?.value)
        assertEquals(MprisPlayer.trackPath("abc-123_X"), (metadata["mpris:trackid"]?.value as DBusPath).path)
    }

    @Test
    fun tellsTheDesktopWhenThePlayerChanges() {
        val changed = CountDownLatch(1)
        val status = CopyOnWriteArrayList<Any?>()
        desktopBus.addSigHandler(Properties.PropertiesChanged::class.java) { signal ->
            if (signal.interfaceName == MprisPlayer.PLAYER) {
                status += signal.propertiesChanged["PlaybackStatus"]?.value
                changed.countDown()
            }
        }
        player.show("song", "Title", "Artist", null, 100_000, 0, playing = true)
        assertTrue("no PropertiesChanged", changed.await(SIGNAL_WAIT_S, TimeUnit.SECONDS))
        assertEquals("Playing", status.first())
    }

    @Test
    fun tellsTheDesktopWhenTheSongJumped() {
        val jumped = CountDownLatch(1)
        val positions = CopyOnWriteArrayList<Long>()
        desktopBus.addSigHandler(MediaPlayer2Player.Seeked::class.java) { signal ->
            positions += signal.position
            jumped.countDown()
        }
        player.show("song", "Title", "Artist", null, 100_000, 0, playing = false)
        player.seeked(61_000)
        assertTrue("no Seeked", jumped.await(SIGNAL_WAIT_S, TimeUnit.SECONDS))
        assertEquals(listOf(61_000_000L), positions)
        assertEquals(61_000_000L, properties().Get<Any>(MprisPlayer.PLAYER, "Position"))
    }

    @Test
    fun buttonsReachTheApp() {
        player.show("song", "Title", "Artist", null, 100_000, 10_000, playing = false)
        controls().PlayPause()
        controls().Next()
        controls().Previous()
        controls().Pause()
        controls().Play()
        controls().Seek(5_000_000)
        controls().SetPosition(DBusPath(MprisPlayer.trackPath("song")), 42_000_000)
        // A position for a song that isn't playing any more is ignored, as MPRIS asks.
        controls().SetPosition(DBusPath(MprisPlayer.trackPath("other")), 1_000_000)
        desktopBus.getRemoteObject(MprisPlayer.BUS_NAME, MprisPlayer.PATH, MediaPlayer2::class.java).Raise()
        assertEquals(listOf("playPause", "next", "previous", "pause", "play", "seek:15000", "seek:42000", "raise"), calls)
    }

    private companion object {
        const val DAEMON_START_MS = 10_000L
        const val SIGNAL_WAIT_S = 5L
    }
}
