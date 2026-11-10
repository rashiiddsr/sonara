/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ermusic.desktop.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Whether YouTube Music can be reached. The computer doesn't say when the network comes and goes,
 * so this is asked only after something failed; once it can't be reached, it is asked again every
 * few seconds until it can. Songs already fetched and downloads play all the while.
 */
object Connection {
    private val mutableOnline = MutableStateFlow(true)
    val online: StateFlow<Boolean> = mutableOnline.asStateFlow()

    private val http =
        OkHttpClient.Builder()
            .callTimeout(PROBE_TIMEOUT_S, TimeUnit.SECONDS)
            .connectTimeout(PROBE_TIMEOUT_S, TimeUnit.SECONDS)
            .build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var watcher: Job? = null

    /** Whether YouTube Music answers now. When it doesn't, it is watched until it does. */
    suspend fun check(): Boolean {
        val reached = reach()
        mutableOnline.value = reached
        if (!reached) watch()
        return reached
    }

    /** Returns once YouTube Music answers again. */
    suspend fun awaitOnline() {
        if (check()) return
        online.first { it }
    }

    @Synchronized
    private fun watch() {
        if (watcher?.isActive == true) return
        watcher =
            scope.launch {
                while (!reach()) delay(PROBE_EVERY_MS)
                mutableOnline.value = true
            }
    }

    /** Any answer at all counts, an error page too: only a missing connection gives none. */
    private suspend fun reach(): Boolean =
        withContext(Dispatchers.IO) {
            runCatching { http.newCall(Request.Builder().url(PROBE_URL).head().build()).execute().use { true } }.getOrDefault(false)
        }

    private const val PROBE_URL = "https://music.youtube.com/"
    private const val PROBE_TIMEOUT_S = 5L
    private const val PROBE_EVERY_MS = 5_000L
}
