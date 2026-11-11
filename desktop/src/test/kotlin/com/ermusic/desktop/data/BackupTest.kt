/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ermusic.desktop.data

import com.ermusic.desktop.window.Uninstall
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Back up, restore and delete, on folders of their own (never the real data). */
class BackupTest {
    private fun folder() = Files.createTempDirectory("sonara-test").toFile()

    private fun File.put(path: String, text: String) = File(this, path).apply { parentFile.mkdirs(); writeText(text) }

    private fun filled(): File =
        folder().apply {
            put("settings.properties", "translator=true")
            put("history.json", "[1]")
            put("downloads/abc.audio", "song")
            put("lyrics/abc.txt", "la la")
            put("session.bin", "secret")
            put("songs/cached.audio", "cache")
        }

    @Test
    fun aBackupComesBackWhole() {
        val from = filled()
        val zip = File(folder(), "backup.zip")
        Backup.write(zip, from, "test")

        val to = folder().apply { put("settings.properties", "old") }
        Backup.stage(zip, to)
        assertTrue(Backup.hasPending(to))
        assertEquals("old", File(to, "settings.properties").readText())

        Backup.applyPending(to)
        assertFalse(Backup.hasPending(to))
        assertEquals("translator=true", File(to, "settings.properties").readText())
        assertEquals("song", File(to, "downloads/abc.audio").readText())
        assertEquals("la la", File(to, "lyrics/abc.txt").readText())
    }

    @Test
    fun theSignInAndTheCacheStayOut() {
        val zip = File(folder(), "backup.zip")
        Backup.write(zip, filled())
        val to = folder()
        Backup.stage(zip, to)
        Backup.applyPending(to)
        assertFalse(File(to, "session.bin").exists())
        assertFalse(File(to, "songs").exists())
    }

    @Test
    fun anyOtherZipIsRefused() {
        val zip = File(folder(), "other.zip")
        ZipOutputStream(zip.outputStream()).use { it.putNextEntry(ZipEntry("settings.properties")); it.write("x".toByteArray()); it.closeEntry() }
        val to = folder().apply { put("settings.properties", "mine") }
        try {
            Backup.stage(zip, to)
            fail("a zip without the marker was taken")
        } catch (expected: Exception) {
        }
        assertFalse(Backup.hasPending(to))
        assertEquals("mine", File(to, "settings.properties").readText())
    }

    @Test
    fun aBackupCantWriteOutsideTheData() {
        val zip = File(folder(), "evil.zip")
        ZipOutputStream(zip.outputStream()).use {
            it.putNextEntry(ZipEntry(Backup.MARKER)); it.closeEntry()
            it.putNextEntry(ZipEntry("../escaped.txt")); it.write("x".toByteArray()); it.closeEntry()
        }
        val to = folder()
        try {
            Backup.stage(zip, to)
            fail("a path outside the data was accepted")
        } catch (expected: Exception) {
        }
        assertFalse(File(to.parentFile, "escaped.txt").exists())
        assertFalse(Backup.hasPending(to))
    }

    @Test
    fun deletingRemovesEverything() {
        val root = filled()
        Uninstall.deleteData(root)
        assertFalse(root.exists())
    }
}
