/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ermusic.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Properties

/** The sealed key built into the app opens to exactly the key in local.properties. */
class SealedKeyTest {
    private val expected: String? =
        listOf(File("../local.properties"), File("local.properties"))
            .firstOrNull(File::isFile)
            ?.let { file -> Properties().apply { file.inputStream().use(::load) }.getProperty("SONARA_AI_KEY")?.trim() }
            ?.takeIf { it.isNotEmpty() }
            ?: System.getenv("SONARA_AI_KEY")?.trim()?.takeIf { it.isNotEmpty() }

    @Test
    fun sealedKeyOpensToTheOwnersKey() {
        // Compared without printing either value.
        if (expected == null) assertEquals("", BuildSecrets.AI_KEY) else assertTrue("the sealed key opens to the configured one", BuildSecrets.AI_KEY == expected)
    }

    @Test
    fun damagedSealOpensToNothing() {
        assertEquals("", SealedKey.open("not a sealed key"))
    }
}
