/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ermusic.desktop.window

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.DefaultWindowExceptionHandlerFactory
import androidx.compose.ui.window.WindowExceptionHandler
import androidx.compose.ui.window.WindowExceptionHandlerFactory
import com.ermusic.desktop.AppFiles
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime

/**
 * What went wrong, written down: errors go to logs/errors.log in the app's data folder, with
 * where they happened and their stack trace, so a bug can be traced after the fact.
 *
 * One error is let go without a word: Compose's "RootNodeOwner is already disposed", raised when
 * the system still asks a window or popup that has just closed (the mini player, a menu, the
 * window at quit) to draw or move. Nothing is lost then; Compose's own handler would show an
 * error box and close the main window over it.
 */
object ErrorLog {
    private val file = File(AppFiles.root, "logs/errors.log")
    private const val MAX_BYTES = 1_048_576L
    private const val REPEAT_GAP_MS = 5_000L

    /** Errors from threads with no handler of their own (the window's thread among them). */
    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            record(thread.name, error)
            if (isDisposedWindow(error)) return@setDefaultUncaughtExceptionHandler
            previous?.uncaughtException(thread, error) ?: error.printStackTrace()
        }
    }

    /** For Compose's windows: logs, lets the disposed-window error go, and hands the rest on. */
    @OptIn(ExperimentalComposeUiApi::class)
    val windows =
        WindowExceptionHandlerFactory { window ->
            WindowExceptionHandler { error ->
                record("window", error)
                if (!isDisposedWindow(error)) DefaultWindowExceptionHandlerFactory.exceptionHandler(window).onException(error)
            }
        }

    /**
     * Compose's own words for "this window or popup has already closed", and only those: anything
     * else, a broken icon say, is a real bug and must still be seen.
     */
    private val closedWindowMessages =
        setOf(
            "RootNodeOwner is already disposed",
            "ComposeScene is already closed",
            "postponeInvalidation called after ComposeScene is closed",
        )

    /** By the words alone: Compose raises these as different exception types. */
    fun isDisposedWindow(error: Throwable): Boolean = generateSequence(error) { it.cause }.any { it.message in closedWindowMessages }

    private var lastClosedWindowAt = 0L

    @Synchronized
    fun record(where: String, error: Throwable) {
        // It can come once a frame for a moment; a line now and then says as much.
        if (isDisposedWindow(error)) {
            val now = System.currentTimeMillis()
            if (now - lastClosedWindowAt < REPEAT_GAP_MS) return
            lastClosedWindowAt = now
        }
        runCatching {
            file.parentFile.mkdirs()
            // Kept small: the older half goes once it passes a megabyte.
            if (file.length() > MAX_BYTES) file.writeText(file.readText().takeLast((MAX_BYTES / 2).toInt()))
            val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }
            file.appendText("${LocalDateTime.now()} [$where]\n$trace\n")
        }
    }
}
