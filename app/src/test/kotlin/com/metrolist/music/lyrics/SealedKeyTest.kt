/**
 * Sonara (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.lyrics

import com.metrolist.music.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Properties

/** The sealed key in BuildConfig opens to exactly the key in local.properties, and only there. */
class SealedKeyTest {
    private val expected: String? =
        listOf(File("local.properties"), File("../local.properties"))
            .firstOrNull(File::isFile)
            ?.let { file -> Properties().apply { file.inputStream().use(::load) }.getProperty("SONARA_AI_KEY")?.trim() }
            ?.takeIf { it.isNotEmpty() }
            ?: System.getenv("SONARA_AI_KEY")?.trim()?.takeIf { it.isNotEmpty() }

    @Test
    fun sealedKeyOpensToTheOwnersKey() {
        val opened = SealedKey.open(BuildConfig.AI_TRANSLATE_KEY_SEALED)
        if (expected == null) {
            assertEquals("no key configured, nothing sealed", "", opened)
        } else {
            // Compared without printing either value.
            assertTrue("the sealed key opens to the configured one", opened == expected)
            assertFalse("the build holds no readable copy", BuildConfig.AI_TRANSLATE_KEY_SEALED.contains(expected))
        }
    }

    @Test
    fun damagedSealOpensToNothing() {
        assertEquals("", SealedKey.open("not a sealed key"))
        assertEquals("", SealedKey.open(""))
    }
}
