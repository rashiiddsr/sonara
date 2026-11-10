/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ermusic.desktop.window

import com.ermusic.desktop.AppFiles
import com.ermusic.desktop.Os
import com.sun.jna.Library
import com.sun.jna.Native
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.util.UUID
import kotlin.concurrent.thread

/**
 * One Sonara at a time. Opening it again wakes the one already running, window and all, instead
 * of starting a second player: a lock file says one is running, and a loopback socket (its port
 * and a random token in a file only this user can reach) tells it to come forward.
 */
object SingleInstance {
    private const val WAIT_FOR_RUNNING_TRIES = 30
    private const val WAIT_FOR_RUNNING_MS = 100L
    private const val CONNECT_TIMEOUT_MS = 2_000

    private val lockFile = File(AppFiles.root, "instance.lock")
    private val addressFile = File(AppFiles.root, "instance.address")

    // Held for as long as the app runs; the system lets go of the lock if it ever crashes.
    private var channel: FileChannel? = null
    private var lock: FileLock? = null

    /** Bumped each time another launch asks this one to come forward. */
    val showRequests = MutableStateFlow(0)

    /** The same ask from within: a click on the Mac's Dock icon while the window is away. */
    fun requestShow() = showRequests.update { it + 1 }

    @Suppress("FunctionName")
    private interface Api : Library {
        fun AllowSetForegroundWindow(processId: Int): Boolean
    }

    /** True when this launch should carry on; false when it handed over to a running one. */
    fun claim(): Boolean {
        // Only an installed copy: a developer's run may sit next to it.
        if (System.getProperty("jpackage.app-path") == null) return true
        val file = RandomAccessFile(lockFile, "rw").channel
        val held = runCatching { file.tryLock() }.getOrNull()
        if (held == null) {
            file.close()
            wakeRunning()
            return false
        }
        channel = file
        lock = held
        listen()
        return true
    }

    private fun listen() {
        val token = UUID.randomUUID().toString()
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        addressFile.writeText("${server.localPort} ${ProcessHandle.current().pid()} $token")
        thread(isDaemon = true, name = "sonara-single-instance") {
            while (true) {
                runCatching {
                    server.accept().use { socket ->
                        val line = socket.getInputStream().bufferedReader().readLine()
                        if (line == token) showRequests.update { it + 1 }
                    }
                }
            }
        }
    }

    private fun wakeRunning() {
        repeat(WAIT_FOR_RUNNING_TRIES) {
            val parts = runCatching { addressFile.readText().trim().split(' ') }.getOrNull()
            if (parts != null && parts.size == 3 && send(parts[0].toInt(), parts[1].toInt(), parts[2])) return
            Thread.sleep(WAIT_FOR_RUNNING_MS)
        }
    }

    private fun send(port: Int, processId: Int, token: String): Boolean =
        runCatching {
            // Windows only lets the app the user just started hand over the foreground.
            if (Os.isWindows) runCatching { Native.load("user32", Api::class.java).AllowSetForegroundWindow(processId) }
            Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress(InetAddress.getLoopbackAddress(), port), CONNECT_TIMEOUT_MS)
                socket.getOutputStream().bufferedWriter().use { it.write(token + "\n") }
            }
            true
        }.getOrDefault(false)
}
