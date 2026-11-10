/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import timber.log.Timber
import java.io.File
import java.time.LocalDateTime

/**
 * For work the user started that must finish even if they leave the screen (saving a playlist
 * edit, adding a song). A screen's own scope is cancelled on navigation, which silently dropped
 * these half way.
 */
val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * Keeps the SonaraWeb diagnostics in the app's external files dir, where adb can read them
 * (HyperOS prunes logcat too quickly to rely on it). Capped at 512 KB.
 */
class SonaraFileTree(context: Context) : Timber.Tree() {
    private val file = context.getExternalFilesDir(null)?.let { File(it, "sonara.log") }

    override fun isLoggable(tag: String?, priority: Int) = tag == "SonaraWeb"

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val target = file ?: return
        runCatching {
            if (target.length() > 512 * 1024) target.writeText("")
            val level = if (priority >= Log.WARN) "W" else "D"
            target.appendText("${LocalDateTime.now()} $level $message${t?.let { " | " + it.message } ?: ""}\n")
        }
    }
}
