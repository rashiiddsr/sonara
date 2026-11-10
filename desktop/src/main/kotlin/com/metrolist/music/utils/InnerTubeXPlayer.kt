/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

/** What YouTubeWebSession asks of the Android app's stream extractor. */
object InnerTubeXPlayer {
    suspend fun webPlayerPoToken(videoId: String, sessionId: String): String? = null
}
