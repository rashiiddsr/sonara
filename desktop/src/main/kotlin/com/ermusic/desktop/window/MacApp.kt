/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ermusic.desktop.window

import java.awt.Desktop
import java.awt.desktop.AppReopenedListener

/**
 * What a Mac app answers from its menu and its Dock icon: Quit (⌘Q) stops the player first,
 * Settings (⌘,) and About open Sonara's own page, and a click on the Dock icon brings the window
 * back when it was closed to the menu bar.
 */
object MacApp {
    fun install(onQuit: () -> Unit, onReopen: () -> Unit, onSettings: () -> Unit) {
        if (!Desktop.isDesktopSupported()) return
        val desktop = Desktop.getDesktop()
        runCatching {
            desktop.setQuitHandler { _, response ->
                onQuit()
                response.performQuit()
            }
        }
        runCatching { desktop.addAppEventListener(AppReopenedListener { onReopen() }) }
        runCatching { desktop.setPreferencesHandler { onSettings() } }
        runCatching { desktop.setAboutHandler { onSettings() } }
    }
}
