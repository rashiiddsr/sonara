/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ermusic.desktop

import java.io.File

/**
 * Where the app keeps its data. On Windows: %LOCALAPPDATA%\Sonara Data, apart from the program's
 * own folder (%LOCALAPPDATA%\Sonara), which Windows' uninstaller empties, and every update runs
 * the old version's uninstaller. On a Mac: ~/Library/Application Support/Sonara, where Mac apps
 * keep theirs. On Linux: the user's data folder ($XDG_DATA_HOME, ~/.local/share by default), in
 * Sonara. The data goes only when the user says so (Uninstall).
 */
object AppFiles {
    private val home = System.getProperty("user.home")

    /** What versions before 2.0 kept loose in the program's folder. */
    private val movedFromInstallFolder =
        listOf(
            "session.bin", "settings.properties", "history.json", "searches.json", "downloads.json",
            "player_config.properties", "speed_dial.json", "songs", "images", "downloads", "translations", "signin-browser",
        )

    val root: File =
        // A test copy of the installer keeps its data apart from the real one (-Dsonara.data=...).
        System.getProperty("sonara.data")?.takeIf { it.isNotBlank() }?.let { File(it).apply { mkdirs() } } ?: if (Os.isMac) {
            File(home, "Library/Application Support/Sonara").apply { mkdirs() }
        } else if (Os.isLinux) {
            val data = System.getenv("XDG_DATA_HOME")?.takeIf { it.startsWith("/") } ?: "$home/.local/share"
            File(data, "Sonara").apply { mkdirs() }
        } else {
            // The installer names the user's folder outright (its environment may not be the user's).
            windowsRoot(File(System.getProperty("sonara.localappdata")?.takeIf { it.isNotBlank() } ?: System.getenv("LOCALAPPDATA") ?: home))
        }

    /**
     * Up to 2.4.1 the data sat in the program's folder (Sonara\data). The 2.4.2 installer moves it
     * out before the old version's uninstaller can empty that folder; this moves it too, should the
     * app open first. While another copy of Sonara holds the old folder, it is used where it is.
     */
    internal fun windowsRoot(local: File): File {
        val install = File(local, "Sonara")
        val data = File(local, "Sonara Data")
        val old = File(install, "data")
        if (old.isDirectory && data.list().isNullOrEmpty()) {
            data.delete()
            if (!old.renameTo(data)) return old
        }
        return data.apply {
            mkdirs()
            movedFromInstallFolder.forEach { name ->
                val loose = File(install, name)
                val moved = File(this, name)
                if (loose.exists() && !moved.exists()) loose.renameTo(moved)
            }
        }
    }
}
