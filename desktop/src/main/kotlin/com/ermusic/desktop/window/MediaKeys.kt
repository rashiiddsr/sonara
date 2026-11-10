/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ermusic.desktop.window

import com.ermusic.desktop.Os
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinUser
import kotlin.concurrent.thread
import kotlin.math.abs

/**
 * The keyboard's media keys, anywhere in the system. On Windows a key another app already holds
 * stays with that app; RegisterHotKey delivers to the thread that registered it, hence the thread
 * of its own. On a Mac they come through Now Playing ([MacNowPlaying]), which also shows the song;
 * on Linux through MPRIS ([LinuxMpris]), the desktop's media controls.
 */
object MediaKeys {
    private const val WM_HOTKEY = 0x0312
    private const val VK_MEDIA_NEXT = 0xB0
    private const val VK_MEDIA_PREVIOUS = 0xB1
    private const val VK_MEDIA_PLAY_PAUSE = 0xB3
    private const val DRIFT_MS = 2_000L

    private data class Shown(val id: String?, val playing: Boolean, val durationMs: Long, val positionMs: Long, val at: Long)

    @Volatile private var shown: Shown? = null

    fun listen(
        onPlayPause: () -> Unit,
        onNext: () -> Unit,
        onPrevious: () -> Unit,
        onPlay: () -> Unit = onPlayPause,
        onPause: () -> Unit = onPlayPause,
        onSeek: (Long) -> Unit = {},
        onRaise: () -> Unit = {},
    ) {
        if (Os.isMac) {
            MacNowPlaying.listen(onPlayPause, onPlay, onPause, onNext, onPrevious, onSeek)
            return
        }
        if (Os.isLinux) {
            LinuxMpris.listen(MprisActions(onPlayPause, onPlay, onPause, onNext, onPrevious, onSeek, onRaise))
            return
        }
        if (!Os.isWindows) return
        thread(isDaemon = true, name = "sonara-media-keys") {
            val actions = mapOf(1 to onPlayPause, 2 to onNext, 3 to onPrevious)
            val keys = mapOf(1 to VK_MEDIA_PLAY_PAUSE, 2 to VK_MEDIA_NEXT, 3 to VK_MEDIA_PREVIOUS)
            keys.forEach { (id, key) -> User32.INSTANCE.RegisterHotKey(null, id, 0, key) }
            val message = WinUser.MSG()
            while (User32.INSTANCE.GetMessage(message, null, 0, 0) > 0) {
                if (message.message == WM_HOTKEY) actions[message.wParam.toInt()]?.invoke()
            }
        }
    }

    /** The song now, for the Mac's Now Playing and Linux's media controls; nothing to do on Windows. */
    fun nowPlaying(id: String?, title: String?, artist: String?, artUrl: String?, durationMs: Long, positionMs: Long, playing: Boolean) {
        if (Os.isWindows) return
        val last = shown
        if (last != null && last.id == id && last.playing == playing && last.durationMs == durationMs) return
        shown = Shown(id, playing, durationMs, positionMs, System.currentTimeMillis())
        if (Os.isLinux) {
            LinuxMpris.show(id, title, artist, artUrl, durationMs, positionMs, playing)
        } else {
            MacNowPlaying.show(title?.takeIf { id != null }, artist, durationMs, positionMs, playing)
        }
    }

    /** Where the song is; Now Playing is told again only when it jumped (a seek), not as it runs. */
    fun position(positionMs: Long, title: String?, artist: String?) {
        if (Os.isWindows) return
        val last = shown ?: return
        if (last.id == null) return
        val expected = if (last.playing) last.positionMs + (System.currentTimeMillis() - last.at) else last.positionMs
        if (abs(positionMs - expected) < DRIFT_MS) return
        shown = last.copy(positionMs = positionMs, at = System.currentTimeMillis())
        if (Os.isLinux) LinuxMpris.seeked(positionMs) else MacNowPlaying.show(title, artist, last.durationMs, positionMs, last.playing)
    }
}
