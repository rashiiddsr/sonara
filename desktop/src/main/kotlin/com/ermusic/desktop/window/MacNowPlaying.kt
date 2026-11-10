/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ermusic.desktop.window

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import java.util.concurrent.Executors

/**
 * The Mac's "Now Playing": media keys, AirPods and Control Center reach Sonara through it, and it
 * shows the song there. macOS sends those commands only to the app that says it is playing, so the
 * song and its state are kept up to date. Talks to the Objective-C runtime directly, through JNA.
 */
internal object MacNowPlaying {
    private const val FRAMEWORK = "/System/Library/Frameworks/MediaPlayer.framework/MediaPlayer"
    private const val HANDLED = 0L
    private const val COMMAND_FAILED = 200L
    private const val STATE_PLAYING = 1L
    private const val STATE_PAUSED = 2L
    private const val STATE_STOPPED = 3L
    private const val MS_PER_SECOND = 1000.0

    @Suppress("FunctionName")
    private interface Runtime : Library {
        fun objc_getClass(name: String): Pointer?
        fun sel_registerName(name: String): Pointer
        fun objc_allocateClassPair(superclass: Pointer, name: String, extraBytes: Long): Pointer?
        fun objc_registerClassPair(cls: Pointer)
        fun class_addMethod(cls: Pointer, selector: Pointer, implementation: Callback, types: String): Boolean
        fun objc_autoreleasePoolPush(): Pointer
        fun objc_autoreleasePoolPop(pool: Pointer)
    }

    /** One command's handler: (self, _cmd, event) -> MPRemoteCommandHandlerStatus. Public for JNA. */
    fun interface Handler : Callback {
        fun invoke(self: Pointer?, selector: Pointer?, event: Pointer?): Long
    }

    private val runtime by lazy { Native.load("objc", Runtime::class.java) }
    private val send by lazy { NativeLibrary.getInstance("objc").getFunction("objc_msgSend") }
    private val mediaPlayer by lazy { NativeLibrary.getInstance(FRAMEWORK) }

    // Callbacks must stay reachable for as long as macOS can call them.
    private val handlers = mutableListOf<Handler>()

    // Now Playing is updated off the UI, one change after another.
    private val updates = Executors.newSingleThreadExecutor { Thread(it, "sonara-now-playing").apply { isDaemon = true } }

    @Volatile private var ready = false

    fun listen(
        onPlayPause: () -> Unit,
        onPlay: () -> Unit,
        onPause: () -> Unit,
        onNext: () -> Unit,
        onPrevious: () -> Unit,
        onSeek: (Long) -> Unit,
    ) {
        updates.execute {
            runCatching {
                mediaPlayer
                val target = makeTarget(
                    "sonaraToggle:" to Handler { _, _, _ -> handled(onPlayPause) },
                    "sonaraPlay:" to Handler { _, _, _ -> handled(onPlay) },
                    "sonaraPause:" to Handler { _, _, _ -> handled(onPause) },
                    "sonaraNext:" to Handler { _, _, _ -> handled(onNext) },
                    "sonaraPrevious:" to Handler { _, _, _ -> handled(onPrevious) },
                    "sonaraSeek:" to Handler { _, _, event ->
                        val seconds = event?.let { send.invokeDouble(arrayOf(it, selector("positionTime"))) }
                        if (seconds == null) COMMAND_FAILED else handled { onSeek((seconds * MS_PER_SECOND).toLong()) }
                    },
                )
                val center = message(cls("MPRemoteCommandCenter"), "sharedCommandCenter")
                listOf(
                    "togglePlayPauseCommand" to "sonaraToggle:",
                    "playCommand" to "sonaraPlay:",
                    "pauseCommand" to "sonaraPause:",
                    "nextTrackCommand" to "sonaraNext:",
                    "previousTrackCommand" to "sonaraPrevious:",
                    "changePlaybackPositionCommand" to "sonaraSeek:",
                ).forEach { (command, action) ->
                    val remote = message(center, command)
                    send.invoke(Pointer::class.java, arrayOf(remote, selector("addTarget:action:"), target, selector(action)))
                }
                ready = true
            }
        }
    }

    /** What plays now; null when nothing does. */
    fun show(title: String?, artist: String?, durationMs: Long, positionMs: Long, playing: Boolean) {
        if (!ready) return
        updates.execute {
            runCatching {
                val pool = runtime.objc_autoreleasePoolPush()
                try {
                    val center = message(cls("MPNowPlayingInfoCenter"), "defaultCenter")
                    if (title == null) {
                        send.invoke(Void::class.java, arrayOf(center, selector("setNowPlayingInfo:"), null))
                        send.invoke(Void::class.java, arrayOf(center, selector("setPlaybackState:"), STATE_STOPPED))
                        return@runCatching
                    }
                    val info = message(cls("NSMutableDictionary"), "dictionary")
                    put(info, "MPMediaItemPropertyTitle", string(title))
                    put(info, "MPMediaItemPropertyArtist", string(artist.orEmpty()))
                    put(info, "MPMediaItemPropertyPlaybackDuration", number(durationMs / MS_PER_SECOND))
                    put(info, "MPNowPlayingInfoPropertyElapsedPlaybackTime", number(positionMs / MS_PER_SECOND))
                    put(info, "MPNowPlayingInfoPropertyPlaybackRate", number(if (playing) 1.0 else 0.0))
                    send.invoke(Void::class.java, arrayOf(center, selector("setNowPlayingInfo:"), info))
                    send.invoke(Void::class.java, arrayOf(center, selector("setPlaybackState:"), if (playing) STATE_PLAYING else STATE_PAUSED))
                } finally {
                    runtime.objc_autoreleasePoolPop(pool)
                }
            }
        }
    }

    private fun handled(action: () -> Unit): Long = runCatching(action).fold({ HANDLED }, { COMMAND_FAILED })

    /** An NSObject subclass whose methods are the given handlers, and one instance of it. */
    private fun makeTarget(vararg methods: Pair<String, Handler>): Pointer {
        val name = "SonaraRemoteTarget"
        val existing = runtime.objc_getClass(name)
        val cls =
            existing ?: requireNotNull(runtime.objc_allocateClassPair(cls("NSObject"), name, 0)).also { made ->
                methods.forEach { (selectorName, handler) ->
                    handlers.add(handler)
                    // q: a 64-bit status; @ self, : the selector, @ the event.
                    runtime.class_addMethod(made, selector(selectorName), handler, "q@:@")
                }
                runtime.objc_registerClassPair(made)
            }
        return message(message(cls, "alloc"), "init")
    }

    private fun cls(name: String): Pointer = requireNotNull(runtime.objc_getClass(name)) { "No class $name" }

    private fun selector(name: String): Pointer = runtime.sel_registerName(name)

    private fun message(receiver: Pointer, selectorName: String): Pointer =
        requireNotNull(send.invoke(Pointer::class.java, arrayOf(receiver, selector(selectorName))) as Pointer?) { "No answer to $selectorName" }

    private fun string(text: String): Pointer {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val memory = Memory(bytes.size + 1L).apply {
            write(0, bytes, 0, bytes.size)
            setByte(bytes.size.toLong(), 0)
        }
        return requireNotNull(send.invoke(Pointer::class.java, arrayOf(cls("NSString"), selector("stringWithUTF8String:"), memory)) as Pointer?)
    }

    private fun number(value: Double): Pointer =
        requireNotNull(send.invoke(Pointer::class.java, arrayOf(cls("NSNumber"), selector("numberWithDouble:"), value)) as Pointer?)

    /** Sets a value under one of MediaPlayer's own key constants. */
    private fun put(dictionary: Pointer, keyName: String, value: Pointer) {
        val key = mediaPlayer.getGlobalVariableAddress(keyName).getPointer(0)
        send.invoke(Void::class.java, arrayOf(dictionary, selector("setObject:forKey:"), value, key))
    }
}
