/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ermusic.desktop.ui

import androidx.compose.ui.res.loadXmlImageVector
import androidx.compose.ui.unit.Density
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.xml.sax.InputSource

/**
 * Every icon must load on the desktop. Android's vector files can hold things only Android reads
 * (@android:color/white); one copied over as it is breaks the page that draws it.
 */
class IconsTest {
    @Test
    fun everyIconLoads() {
        val icons = File("src/main/resources/icons").listFiles { file -> file.extension == "xml" }.orEmpty()
        assertTrue("no icons found", icons.isNotEmpty())
        icons.forEach { icon ->
            runCatching { icon.inputStream().use { loadXmlImageVector(InputSource(it), Density(1f)) } }
                .onFailure { throw AssertionError("${icon.name} can't be read on the desktop: $it", it) }
        }
    }
}
