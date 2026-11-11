/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

@file:JvmName("UninstallMain")

package com.ermusic.desktop

import com.ermusic.desktop.window.Uninstall
import java.io.File

/**
 * Run by the Windows installer while it removes Sonara, as
 * `runtime\bin\javaw.exe -cp app\* -Djpackage.app-path=...\Sonara.exe com.ermusic.desktop.UninstallMain [keep|delete]`.
 *
 * Not through Sonara.exe: that launcher starts the app in a second process, and the installer
 * would wait only for the first, going on to delete the program while the question is still up.
 */
fun main(args: Array<String>) {
    // Nothing here may stop the uninstall; what goes wrong is written to %TEMP%\sonara-uninstall.log.
    runCatching {
        useInstalledDataFolder()
        Uninstall.fromInstaller(args.firstOrNull())
    }.onFailure { error ->
        runCatching { File(System.getProperty("java.io.tmpdir"), "sonara-uninstall.log").appendText("${java.time.LocalDateTime.now()} ${error.stackTraceToString()}\n") }
    }
}

/**
 * The data folder the installed copy uses: its launcher settings (app\<name>.cfg) may name one
 * (-Dsonara.data, a test copy's), and that, not the usual folder, is what the question is about.
 */
private fun useInstalledDataFolder() {
    if (!System.getProperty("sonara.data").isNullOrBlank()) return
    val program = System.getProperty("jpackage.app-path")?.let(::File) ?: return
    val config = File(program.parentFile, "app/${program.nameWithoutExtension}.cfg")
    val option = "java-options=-Dsonara.data="
    val folder = runCatching { config.readLines() }.getOrNull()?.firstOrNull { it.startsWith(option) }?.removePrefix(option)?.trim()
    if (!folder.isNullOrEmpty()) System.setProperty("sonara.data", folder)
}
