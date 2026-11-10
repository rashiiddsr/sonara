/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ermusic.desktop

/** Which desktop Sonara runs on, and the words and keys that differ between them. */
object Os {
    val isMac: Boolean = System.getProperty("os.name").orEmpty().startsWith("Mac", ignoreCase = true)
    val isWindows: Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    /** The name people know the system by. */
    val name: String = if (isMac) "macOS" else "Windows"

    /** The key that goes with shortcuts: Command on a Mac, Control elsewhere. */
    val shortcutKey: String = if (isMac) "⌘" else "Ctrl "
}
