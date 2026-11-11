/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ermusic.desktop.data

import com.ermusic.desktop.AppFiles
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * A copy of what is the user's own, in one .zip they can keep anywhere: settings, history, speed
 * dial, recent searches, downloads (the list and the songs), lyrics and translations. The
 * sign-in is left out: it is sealed for this computer and this user, useless anywhere else, and a
 * live Google session is not something to leave lying in a file.
 *
 * Restoring happens when the app next opens, before anything is read: the backup is checked and
 * unpacked beside the data first, so a broken file never replaces what is there.
 */
object Backup {
    /** Marks a Sonara backup; restoring refuses a .zip without it. */
    const val MARKER = "sonara-backup.txt"

    /** What a backup holds, relative to the data folder. */
    val included = listOf("settings.properties", "history.json", "searches.json", "speed_dial.json", "downloads.json", "downloads", "lyrics", "translations")

    private const val PENDING = "restore-pending"

    /** Writes the backup to [target]; returns how many files went in. */
    fun write(target: File, root: File = AppFiles.root, version: String = ""): Int {
        val part = File(target.path + ".part")
        var files = 0
        ZipOutputStream(part.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(MARKER))
            zip.write("Sonara backup $version\n".toByteArray())
            zip.closeEntry()
            included.map { File(root, it) }.filter(File::exists).forEach { item ->
                item.walkTopDown().filter(File::isFile).forEach { file ->
                    zip.putNextEntry(ZipEntry(file.relativeTo(root).invariantSeparatorsPath))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    files++
                }
            }
        }
        target.delete()
        if (!part.renameTo(target)) throw IOException("Could not write ${target.path}")
        return files
    }

    /**
     * Checks [source] and unpacks it beside the data, to be put in place when the app next opens.
     * Throws when it is not a Sonara backup.
     */
    fun stage(source: File, root: File = AppFiles.root) {
        val pending = File(root, PENDING)
        pending.deleteRecursively()
        try {
            ZipFile(source).use { zip ->
                if (zip.getEntry(MARKER) == null) throw IOException("This file isn't a Sonara backup.")
                val base = pending.canonicalFile
                zip.entries().asSequence().filter { !it.isDirectory && it.name != MARKER }.forEach { entry ->
                    val out = File(pending, entry.name).canonicalFile
                    // Only into the data folder, and only what a backup may hold.
                    if (!out.path.startsWith(base.path + File.separator)) throw IOException("This backup has a file outside Sonara's data.")
                    if (entry.name.substringBefore('/') !in included) return@forEach
                    out.parentFile.mkdirs()
                    zip.getInputStream(entry).use { input -> out.outputStream().use { input.copyTo(it) } }
                }
            }
        } catch (error: Exception) {
            pending.deleteRecursively()
            throw error
        }
    }

    fun hasPending(root: File = AppFiles.root): Boolean = File(root, PENDING).isDirectory

    /** At start, before anything reads the data: what a restore brought replaces the same items. */
    fun applyPending(root: File = AppFiles.root) {
        val pending = File(root, PENDING)
        if (!pending.isDirectory) return
        included.forEach { name ->
            val incoming = File(pending, name)
            if (!incoming.exists()) return@forEach
            val current = File(root, name)
            current.deleteRecursively()
            if (!incoming.renameTo(current)) incoming.copyRecursively(current, overwrite = true)
        }
        pending.deleteRecursively()
    }
}
