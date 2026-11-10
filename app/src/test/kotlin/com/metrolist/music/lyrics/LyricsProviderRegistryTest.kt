package com.metrolist.music.lyrics

import androidx.datastore.preferences.core.emptyPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsProviderRegistryTest {
    @Test
    fun providersAreTriedInFixedOrder() {
        assertEquals(
            listOf("BetterLyrics", "LrcLib", "Kugou", "Paxsenix", "LyricsPlus", "Zemer"),
            LyricsProviderRegistry.getEnabledProviders(emptyPreferences()).take(6).map { it.name },
        )
        assertEquals(8, LyricsProviderRegistry.orderedProviders.size)
    }
}
