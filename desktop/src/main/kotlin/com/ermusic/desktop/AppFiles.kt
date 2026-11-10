/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ermusic.desktop

import java.io.File

/**
 * Where the app keeps its data. On Windows: %LOCALAPPDATA%\Sonara\data, since the folder above it
 * is where the per-user installer puts the program, and what it replaces on an update. On a Mac:
 * ~/Library/Application Support/Sonara, where Mac apps keep theirs.
 */
object AppFiles {
    private val home = System.getProperty("user.home")
    private val movedFromInstallFolder =
        listOf(
            "session.bin", "settings.properties", "history.json", "searches.json", "downloads.json",
            "player_config.properties", "speed_dial.json", "songs", "images", "downloads", "translations", "signin-browser",
        )

    val root: File =
        if (Os.isMac) {
            File(home, "Library/Application Support/Sonara").apply { mkdirs() }
        } else {
            val install = File(System.getenv("LOCALAPPDATA") ?: home, "Sonara")
            File(install, "data").apply {
                mkdirs()
                movedFromInstallFolder.forEach { name ->
                    val old = File(install, name)
                    val new = File(this, name)
                    if (old.exists() && !new.exists()) old.renameTo(new)
                }
            }
        }
}
