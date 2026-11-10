/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ermusic.desktop.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.window.WindowPlacement
import com.ermusic.desktop.Os
import com.sun.jna.CallbackReference
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.LRESULT
import com.sun.jna.platform.win32.WinDef.WPARAM
import com.sun.jna.platform.win32.WinUser
import kotlinx.coroutines.flow.MutableStateFlow
import java.awt.Desktop
import java.awt.Frame
import java.awt.MouseInfo
import java.awt.event.WindowStateListener
import java.util.concurrent.ConcurrentHashMap

/**
 * The window's own frame, with the app drawn up to the top edge, the way desktop apps do it. The
 * native frame stays (so resizing, drag-to-snap and shadows are Windows' own); the title bar's
 * space is handed to the client area, and hit tests tell Windows which parts of the app's top bar
 * drag the window. The maximize button answers as the real one, so Windows 11 shows Snap Layouts.
 */
object WindowChrome {
    private const val GWL_STYLE = -16
    private const val GWLP_WNDPROC = -4
    private const val WM_NCCALCSIZE = 0x0083
    private const val WM_NCHITTEST = 0x0084
    private const val WM_NCMOUSEMOVE = 0x00A0
    private const val WM_NCLBUTTONDOWN = 0x00A1
    private const val WM_NCLBUTTONUP = 0x00A2
    private const val HTTRANSPARENT = -1
    private const val HTCLIENT = 1
    private const val HTCAPTION = 2
    private const val HTMAXBUTTON = 9
    private const val HTTOP = 12
    private const val HTTOPLEFT = 13
    private const val HTTOPRIGHT = 14
    private const val WS_CAPTION = 0x00C00000L
    private const val WS_THICKFRAME = 0x00040000L
    private const val SWP_NOSIZE = 0x0001
    private const val SWP_NOMOVE = 0x0002
    private const val SWP_NOZORDER = 0x0004
    private const val SWP_FRAMECHANGED = 0x0020
    private const val SWP_SHOWWINDOW = 0x0040
    private const val SW_MINIMIZE = 6
    private const val SW_MAXIMIZE = 3
    private const val SW_RESTORE = 9
    private const val MONITOR_DEFAULT_TO_NEAREST = 2
    private const val RESIZE_EDGE_PX = 6
    private const val RESIZE_CORNER_PX = 16
    private const val RECT_TOP_OFFSET = 4L

    @Suppress("FunctionName")
    private interface Api : Library {
        fun SetWindowLongPtrW(hwnd: HWND, index: Int, value: Pointer): Pointer?
        fun GetWindowLongPtrW(hwnd: HWND, index: Int): Pointer?
        fun CallWindowProcW(previous: Pointer, hwnd: HWND, message: Int, wParam: WPARAM, lParam: LPARAM): LRESULT
        fun ScreenToClient(hwnd: HWND, point: WinDef.POINT): Boolean
        fun IsZoomed(hwnd: HWND): Boolean
        fun IsIconic(hwnd: HWND): Boolean
        fun SetForegroundWindow(hwnd: HWND): Boolean
        fun ShowWindow(hwnd: HWND, command: Int): Boolean
        fun SetWindowPos(hwnd: HWND, insertAfter: HWND?, x: Int, y: Int, width: Int, height: Int, flags: Int): Boolean
    }

    private data class Area(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        fun contains(x: Int, y: Int) = x in left until right && y in top until bottom
    }

    private val api by lazy { Native.load("user32", Api::class.java) }
    private val controls = ConcurrentHashMap<Any, Area>()

    // JNA callbacks must stay reachable for as long as Windows can call them.
    private val hooks = mutableListOf<WinUser.WindowProc>()

    @Volatile private var maximizeArea: Area? = null

    @Volatile private var captionHeightPx = 0

    @Volatile private var fullscreen = false

    @Volatile private var hwnd: HWND? = null
    private var window: ComposeWindow? = null
    private var savedStyle = 0L
    private var savedRect: WinDef.RECT? = null

    /** False when the frame couldn't be taken over; the native title bar then stays. */
    val active = MutableStateFlow(false)
    val maximized = MutableStateFlow(false)
    val maximizeHovered = MutableStateFlow(false)

    fun install(target: ComposeWindow) {
        if (active.value) return
        window = target
        if (Os.isMac) {
            installMac(target)
            return
        }
        runCatching {
            val handle = HWND(Native.getComponentPointer(target))
            hwnd = handle
            hookFrame(handle)
            val children = mutableListOf<HWND>()
            User32.INSTANCE.EnumChildWindows(handle, { child, _ -> children.add(child) }, null)
            children.forEach(::hookChild)
            target.addWindowStateListener(
                WindowStateListener { maximized.value = it.newState and Frame.MAXIMIZED_BOTH == Frame.MAXIMIZED_BOTH },
            )
            api.SetWindowPos(handle, null, 0, 0, 0, 0, SWP_NOMOVE or SWP_NOSIZE or SWP_NOZORDER or SWP_FRAMECHANGED)
            active.value = true
        }
    }

    private fun replaceProcedure(handle: HWND, make: (Pointer) -> WinUser.WindowProc) {
        val previous = requireNotNull(api.GetWindowLongPtrW(handle, GWLP_WNDPROC))
        val hook = make(previous)
        hooks.add(hook)
        api.SetWindowLongPtrW(handle, GWLP_WNDPROC, CallbackReference.getFunctionPointer(hook))
    }

    private fun hookFrame(handle: HWND) =
        replaceProcedure(handle) { previous ->
            WinUser.WindowProc { window, message, wParam, lParam ->
                fun default() = api.CallWindowProcW(previous, window, message, wParam, lParam)
                when {
                    message == WM_NCCALCSIZE && wParam.toLong() != 0L -> {
                        if (fullscreen) return@WindowProc LRESULT(0)
                        val rect = Pointer(lParam.toLong())
                        val left = rect.getInt(0)
                        val top = rect.getInt(RECT_TOP_OFFSET)
                        val result = default()
                        // Windows keeps the side and bottom frames; only the title bar goes. A
                        // maximized window hangs past the screen by one frame on every side.
                        val inset = if (api.IsZoomed(window)) rect.getInt(0) - left else 0
                        rect.setInt(RECT_TOP_OFFSET, top + inset)
                        result
                    }
                    message == WM_NCHITTEST -> {
                        val result = default()
                        if (result.toInt() != HTCLIENT) result else LRESULT(region(lParam).toLong())
                    }
                    message == WM_NCMOUSEMOVE -> {
                        maximizeHovered.value = wParam.toInt() == HTMAXBUTTON
                        default()
                    }
                    message == WM_NCLBUTTONDOWN && wParam.toInt() == HTMAXBUTTON -> LRESULT(0)
                    message == WM_NCLBUTTONUP && wParam.toInt() == HTMAXBUTTON -> {
                        toggleMaximize()
                        LRESULT(0)
                    }
                    else -> default()
                }
            }
        }

    /** The app's canvas sits over the whole frame: it lets through what belongs to the frame. */
    private fun hookChild(handle: HWND) =
        replaceProcedure(handle) { previous ->
            WinUser.WindowProc { window, message, wParam, lParam ->
                if (message == WM_NCHITTEST && region(lParam) != HTCLIENT) {
                    LRESULT(HTTRANSPARENT.toLong())
                } else {
                    api.CallWindowProcW(previous, window, message, wParam, lParam)
                }
            }
        }

    private fun region(lParam: LPARAM): Int {
        val frame = hwnd ?: return HTCLIENT
        if (fullscreen) return HTCLIENT
        val raw = lParam.toLong()
        val point = WinDef.POINT(raw.toShort().toInt(), (raw shr Short.SIZE_BITS).toShort().toInt())
        api.ScreenToClient(frame, point)
        val (x, y) = point.x to point.y
        val width = (window?.width ?: 0) * scale()
        if (y in 0 until RESIZE_EDGE_PX && !api.IsZoomed(frame)) {
            return when {
                x < RESIZE_CORNER_PX -> HTTOPLEFT
                x >= width - RESIZE_CORNER_PX -> HTTOPRIGHT
                else -> HTTOP
            }
        }
        return when {
            y >= captionHeightPx || y < 0 -> HTCLIENT
            maximizeArea?.contains(x, y) == true -> HTMAXBUTTON
            controls.values.any { it.contains(x, y) } -> HTCLIENT
            else -> HTCAPTION
        }
    }

    private fun scale(): Int = window?.graphicsConfiguration?.defaultTransform?.scaleX?.let { kotlin.math.ceil(it).toInt() } ?: 1

    fun setCaptionHeight(px: Int) {
        captionHeightPx = px
    }

    /** Puts the window in front of the others, un-minimized. */
    fun bringToFront() {
        if (Os.isMac) {
            val target = window ?: return
            if (target.extendedState and Frame.ICONIFIED != 0) target.extendedState = target.extendedState and Frame.ICONIFIED.inv()
            target.toFront()
            runCatching { Desktop.getDesktop().requestForeground(true) }
            return
        }
        val frame = hwnd ?: return
        if (api.IsIconic(frame)) api.ShowWindow(frame, SW_RESTORE)
        api.SetForegroundWindow(frame)
    }

    fun minimize() {
        if (Os.isMac) {
            window?.let { it.extendedState = it.extendedState or Frame.ICONIFIED }
            return
        }
        hwnd?.let { api.ShowWindow(it, SW_MINIMIZE) }
    }

    fun toggleMaximize() {
        if (Os.isMac) {
            // The Mac's zoom: the window fills the screen around the menu bar and the Dock.
            window?.let { it.extendedState = if (it.extendedState and Frame.MAXIMIZED_BOTH == Frame.MAXIMIZED_BOTH) Frame.NORMAL else Frame.MAXIMIZED_BOTH }
            return
        }
        val frame = hwnd ?: return
        api.ShowWindow(frame, if (api.IsZoomed(frame)) SW_RESTORE else SW_MAXIMIZE)
    }

    /** True full screen: no frame, over the taskbar, like a video. On a Mac, its own full screen. */
    fun setFullscreen(on: Boolean) {
        if (Os.isMac) {
            val target = window ?: return
            if (on == fullscreen) return
            fullscreen = on
            macFullscreenReached = false
            target.placement = if (on) WindowPlacement.Fullscreen else WindowPlacement.Floating
            return
        }
        val frame = hwnd ?: return
        if (on == fullscreen) return
        if (on) {
            savedStyle = Pointer.nativeValue(api.GetWindowLongPtrW(frame, GWL_STYLE))
            savedRect = WinDef.RECT().also { User32.INSTANCE.GetWindowRect(frame, it) }
            fullscreen = true
            api.SetWindowLongPtrW(frame, GWL_STYLE, Pointer(savedStyle and (WS_CAPTION or WS_THICKFRAME).inv()))
            val info = WinUser.MONITORINFO()
            User32.INSTANCE.GetMonitorInfo(User32.INSTANCE.MonitorFromWindow(frame, MONITOR_DEFAULT_TO_NEAREST), info)
            val area = info.rcMonitor
            api.SetWindowPos(frame, null, area.left, area.top, area.right - area.left, area.bottom - area.top, SWP_FRAMECHANGED or SWP_SHOWWINDOW)
        } else {
            fullscreen = false
            api.SetWindowLongPtrW(frame, GWL_STYLE, Pointer(savedStyle))
            val area = savedRect ?: return
            api.SetWindowPos(frame, null, area.left, area.top, area.right - area.left, area.bottom - area.top, SWP_FRAMECHANGED or SWP_SHOWWINDOW)
            if (api.IsZoomed(frame)) {
                // Windows places a maximized window by itself, inside the taskbar.
                api.ShowWindow(frame, SW_RESTORE)
                api.ShowWindow(frame, SW_MAXIMIZE)
            }
        }
    }

    private fun androidx.compose.ui.geometry.Rect.toArea() = Area(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())

    /** Marks a control in the top bar: clicks on it reach the app, not the window drag. */
    @Composable
    fun Modifier.captionControl(): Modifier {
        val key = remember { Any() }
        DisposableEffect(key) { onDispose { controls.remove(key) } }
        return onGloballyPositioned { controls[key] = it.boundsInWindow().toArea() }
    }

    /** Marks the maximize button, which Windows treats as its own. */
    fun Modifier.maximizeButtonArea(): Modifier = onGloballyPositioned { maximizeArea = it.boundsInWindow().toArea() }

    // ---- Mac ----

    private class DragState {
        var coordinates: LayoutCoordinates? = null
        var lastPressAt = 0L
    }

    @Volatile private var macFullscreenReached = false

    /** Called when the Mac left full screen by itself: its green button, a gesture, the menu. */
    var onLeftFullscreen: (() -> Unit)? = null

    /**
     * The app drawn under a see-through title bar, with the window's own traffic lights left where
     * they are. Dragging and double-clicking the app's top bar move and zoom the window
     * ([windowDragArea]), since AWT hands those clicks to the app.
     */
    private fun installMac(target: ComposeWindow) {
        runCatching {
            target.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
            target.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
            target.rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
            target.addWindowStateListener(
                WindowStateListener { maximized.value = it.newState and Frame.MAXIMIZED_BOTH == Frame.MAXIMIZED_BOTH },
            )
            active.value = true
        }
    }

    /** Polled by the app while it shows full screen, so leaving it from the Mac's side is noticed. */
    fun checkMacFullscreen() {
        val target = window ?: return
        if (!Os.isMac || !fullscreen) return
        val now = target.placement == WindowPlacement.Fullscreen
        when {
            now -> macFullscreenReached = true
            macFullscreenReached -> {
                fullscreen = false
                macFullscreenReached = false
                onLeftFullscreen?.invoke()
            }
        }
    }

    /**
     * On a Mac, the app's top bar moves the window when dragged where nothing else answers, and
     * zooms it on a double click, the way a title bar does. Nothing on Windows, whose frame does it.
     */
    @Composable
    fun Modifier.windowDragArea(): Modifier {
        if (!Os.isMac) return this
        val drag = remember { DragState() }
        return onGloballyPositioned { drag.coordinates = it }.pointerInput(drag) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val inWindow = drag.coordinates?.localToWindow(down.position) ?: down.position
                if (controls.values.any { it.contains(inWindow.x.toInt(), inWindow.y.toInt()) }) return@awaitEachGesture
                val target = window ?: return@awaitEachGesture
                val now = System.currentTimeMillis()
                if (now - drag.lastPressAt < viewConfiguration.doubleTapTimeoutMillis) {
                    drag.lastPressAt = 0L
                    toggleMaximize()
                    return@awaitEachGesture
                }
                drag.lastPressAt = now
                val start = MouseInfo.getPointerInfo()?.location ?: return@awaitEachGesture
                val origin = target.location
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    val at = MouseInfo.getPointerInfo()?.location ?: break
                    target.setLocation(origin.x + at.x - start.x, origin.y + at.y - start.y)
                }
            }
        }
    }
}

