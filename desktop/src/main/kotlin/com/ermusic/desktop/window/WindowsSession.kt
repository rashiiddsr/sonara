/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ermusic.desktop.window

import com.ermusic.desktop.Os
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.LRESULT
import com.sun.jna.platform.win32.WinDef.WPARAM
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.CallbackReference
import java.awt.Window
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * Sonara and Windows' installer. Updating means replacing files the running app holds, so the
 * installer asks the app to close (its "Close applications automatically"). Sonara then quits
 * for good, even when closing the window would only send it to the tray, and asks to be opened
 * again once the update is done. Shutting the computer down goes the same way.
 */
object WindowsSession {
    private const val GWLP_WNDPROC = -4
    private const val WM_QUERYENDSESSION = 0x0011
    private const val WM_ENDSESSION = 0x0016
    private const val RESTART_NO_CRASH = 1
    private const val RESTART_NO_HANG = 2
    private const val RESTART_NO_REBOOT = 8

    @Suppress("FunctionName")
    private interface User : Library {
        fun SetWindowLongPtrW(hwnd: HWND, index: Int, value: Pointer): Pointer?
        fun GetWindowLongPtrW(hwnd: HWND, index: Int): Pointer?
        fun CallWindowProcW(previous: Pointer, hwnd: HWND, message: Int, wParam: WPARAM, lParam: LPARAM): LRESULT
    }

    @Suppress("FunctionName")
    private interface Kernel : Library {
        fun RegisterApplicationRestart(commandLine: WString?, flags: Int): Int
    }

    private val user by lazy { Native.load("user32", User::class.java) }

    // JNA callbacks must stay reachable for as long as Windows can call them.
    private val hooks = mutableListOf<WinUser.WindowProc>()

    @Volatile private var ending = false

    /**
     * Listens on [window] for Windows asking the app to end; [onEnd] stops playback and lets go of
     * what the app holds, then the app quits.
     */
    fun install(window: Window, onEnd: () -> Unit) {
        if (!Os.isWindows) return
        runCatching {
            // Opened again after an update; not after a crash or a hang, nor after a restart of
            // the computer, which Windows' own setting already covers for those who want it.
            if (System.getProperty("jpackage.app-path") != null) {
                Native.load("kernel32", Kernel::class.java).RegisterApplicationRestart(null, RESTART_NO_CRASH or RESTART_NO_HANG or RESTART_NO_REBOOT)
            }
            val handle = HWND(Native.getComponentPointer(window))
            val previous = requireNotNull(user.GetWindowLongPtrW(handle, GWLP_WNDPROC))
            val hook =
                WinUser.WindowProc { hwnd, message, wParam, lParam ->
                    when {
                        // Yes, the app can end now: nothing in it needs the user's say first.
                        message == WM_QUERYENDSESSION -> LRESULT(1)
                        message == WM_ENDSESSION && wParam.toLong() != 0L -> {
                            end(onEnd)
                            LRESULT(0)
                        }
                        else -> user.CallWindowProcW(previous, hwnd, message, wParam, lParam)
                    }
                }
            hooks += hook
            user.SetWindowLongPtrW(handle, GWLP_WNDPROC, CallbackReference.getFunctionPointer(hook))
        }.onFailure { System.err.println("Sonara: can't follow Windows' requests to close ($it)") }
    }

    /** Quits from a thread of its own: the window's thread must answer Windows straight away. */
    private fun end(onEnd: () -> Unit) {
        if (ending) return
        ending = true
        thread(name = "sonara-end-session") {
            runCatching(onEnd)
            exitProcess(0)
        }
    }
}
