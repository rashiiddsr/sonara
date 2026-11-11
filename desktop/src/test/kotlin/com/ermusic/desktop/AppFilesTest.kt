/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ermusic.desktop

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Windows' data leaves the program's folder, which uninstalling (and so updating) empties. */
class AppFilesTest {
    private fun local() = Files.createTempDirectory("sonara-local").toFile()

    @Test
    fun theOldDataMovesOut() {
        val local = local()
        File(local, "Sonara/data/session.bin").apply { parentFile.mkdirs(); writeText("me") }
        val root = AppFiles.windowsRoot(local)
        assertEquals(File(local, "Sonara Data"), root)
        assertEquals("me", File(root, "session.bin").readText())
        assertFalse(File(local, "Sonara/data").exists())
    }

    @Test
    fun dataAlreadyMovedIsNeverReplaced() {
        val local = local()
        File(local, "Sonara Data/session.bin").apply { parentFile.mkdirs(); writeText("new") }
        File(local, "Sonara/data/session.bin").apply { parentFile.mkdirs(); writeText("old") }
        val root = AppFiles.windowsRoot(local)
        assertEquals("new", File(root, "session.bin").readText())
        assertTrue(File(local, "Sonara/data/session.bin").exists())
    }

    @Test
    fun aFreshInstallStartsEmpty() {
        val root = AppFiles.windowsRoot(local())
        assertTrue(root.isDirectory)
        assertEquals("Sonara Data", root.name)
    }
}
