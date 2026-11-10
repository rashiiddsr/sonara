/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ermusic.desktop.window

import com.ermusic.desktop.Os
import java.awt.Toolkit

/**
 * Linux's desktops tie a window to its menu entry by the window's class. Java names it after the
 * main class unless told otherwise, and the dock then shows a second, iconless Sonara; so it is
 * named after the menu entry the package installs (sonara-Sonara.desktop, AppImage's too).
 */
object LinuxDesktop {
    /** The menu entry's name, without .desktop: jpackage's "package-name" + "-" + "app name". */
    const val DESKTOP_ENTRY = "sonara-Sonara"

    /** Before the first window: X11's toolkit reads the name once. Needs --add-opens (build file). */
    fun nameWindows() {
        if (!Os.isLinux) return
        runCatching {
            val toolkit = Toolkit.getDefaultToolkit()
            val field = toolkit.javaClass.getDeclaredField("awtAppClassName")
            field.isAccessible = true
            field.set(toolkit, DESKTOP_ENTRY)
        }
    }
}
