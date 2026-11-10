/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.lyrics

import androidx.datastore.preferences.core.Preferences

/**
 * Lyrics providers in the fixed order they are tried. There is no user setting for this;
 * the first provider that returns lyrics wins.
 */
object LyricsProviderRegistry {
    val orderedProviders: List<LyricsProvider> =
        listOf(
            BetterLyricsProvider,
            LrcLibLyricsProvider,
            KuGouLyricsProvider,
            PaxsenixLyricsProvider,
            LyricsPlusProvider,
            ZemerLyricsProvider,
            YouTubeSubtitleLyricsProvider,
            YouTubeLyricsProvider,
        )

    val providerNames: List<String> = orderedProviders.map { it.name }

    fun getProviderByName(name: String): LyricsProvider? = orderedProviders.find { it.name == name }

    @Suppress("UNUSED_PARAMETER")
    fun getEnabledProviders(preferences: Preferences): List<LyricsProvider> = orderedProviders
}
