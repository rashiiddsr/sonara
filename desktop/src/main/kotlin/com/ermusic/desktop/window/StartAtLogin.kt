/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ermusic.desktop.window

import com.ermusic.desktop.Os
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import java.io.File

/**
 * Opening Sonara when the user signs in: the per-user "Run" entry on Windows, a launch agent on a
 * Mac. Only an installed copy has a program to point at.
 */
object StartAtLogin {
    private const val RUN_KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val VALUE = "Sonara"
    private const val AGENT_LABEL = "app.sonara.desktop"
    private val executable: String? = System.getProperty("jpackage.app-path")
    private val agent = File(System.getProperty("user.home"), "Library/LaunchAgents/$AGENT_LABEL.plist")

    val available: Boolean get() = executable != null && (!Os.isMac || bundle != null)

    /** What the setting is called where it runs. */
    val label: String = if (Os.isMac) "Open at login" else "Start with Windows"

    /** The .app the running program sits in: launch agents open the bundle, as the Dock does. */
    private val bundle: String? get() = executable?.substringBefore(".app/", "")?.takeIf { it.isNotEmpty() }?.plus(".app")

    fun enabled(): Boolean =
        if (Os.isMac) {
            agent.exists()
        } else {
            runCatching { Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, RUN_KEY, VALUE) }.getOrDefault(false)
        }

    fun set(on: Boolean) {
        runCatching {
            when {
                Os.isMac && on -> writeAgent(requireNotNull(bundle))
                Os.isMac -> agent.delete()
                on -> Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, RUN_KEY, VALUE, "\"$executable\"")
                enabled() -> Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, RUN_KEY, VALUE)
            }
        }
    }

    private fun writeAgent(app: String) {
        agent.parentFile.mkdirs()
        val path = app.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        agent.writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
                <key>Label</key>
                <string>$AGENT_LABEL</string>
                <key>ProgramArguments</key>
                <array>
                    <string>/usr/bin/open</string>
                    <string>-a</string>
                    <string>$path</string>
                </array>
                <key>RunAtLoad</key>
                <true/>
            </dict>
            </plist>
            """.trimIndent() + "\n",
        )
    }
}
