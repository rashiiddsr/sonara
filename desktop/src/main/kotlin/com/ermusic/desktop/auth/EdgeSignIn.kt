/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ermusic.desktop.auth

import com.ermusic.desktop.AppFiles
import com.ermusic.desktop.Os
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class NoBrowserException : Exception("Microsoft Edge or Google Chrome is needed to sign in")

/**
 * Google's sign-in page, in a window of the default browser (Edge when it can't be used), on a
 * profile of its own: another browser's cookies can't be read, they are encrypted for that browser.
 * YouTube Music's web API only accepts the session cookies of that flow (the Android app reads
 * them from a WebView for the same reason). Once the page reaches YouTube Music signed in, the
 * cookies are read over Edge's local debugging port and the window closes.
 */
object EdgeSignIn {
    private const val SIGN_IN_URL =
        "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com"
    private const val MUSIC_URL = "https://music.youtube.com"
    private const val POLL_MS = 1_500L
    private const val SIGN_IN_TIMEOUT_MS = 15 * 60 * 1000L
    private const val PAGE_DATA_SCRIPT =
        "JSON.stringify(window.yt&&yt.config_?{v:yt.config_.VISITOR_DATA,d:yt.config_.DATASYNC_ID," +
            "a:String(yt.config_.SESSION_INDEX||0)}:null)"

    private val profile = File(AppFiles.root, "signin-browser")
    private val http = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    fun forgetBrowserProfile() {
        profile.deleteRecursively()
    }

    /** Chromium browsers that can be asked for cookies, by the id the system knows them under. */
    private val browsers =
        if (Os.isMac) {
            listOf(
                "com.google.chrome" to "Google Chrome.app/Contents/MacOS/Google Chrome",
                "com.microsoft.edgemac" to "Microsoft Edge.app/Contents/MacOS/Microsoft Edge",
                "com.brave.browser" to "Brave Browser.app/Contents/MacOS/Brave Browser",
                "com.vivaldi.vivaldi" to "Vivaldi.app/Contents/MacOS/Vivaldi",
            )
        } else {
            listOf(
                "Chrome" to "Google\\Chrome\\Application\\chrome.exe",
                "MSEdge" to "Microsoft\\Edge\\Application\\msedge.exe",
                "Brave" to "BraveSoftware\\Brave-Browser\\Application\\brave.exe",
                "Vivaldi" to "Vivaldi\\Application\\vivaldi.exe",
            )
        }

    private fun defaultBrowserId(): String? = if (Os.isMac) macDefaultBrowserId() else windowsDefaultBrowserId()

    private fun windowsDefaultBrowserId(): String? =
        runCatching {
            Advapi32Util.registryGetStringValue(
                WinReg.HKEY_CURRENT_USER,
                "Software\\Microsoft\\Windows\\Shell\\Associations\\UrlAssociations\\https\\UserChoice",
                "ProgId",
            )
        }.getOrNull()

    /** The app macOS opens https links with, from Launch Services' list of handlers. */
    private fun macDefaultBrowserId(): String? =
        runCatching {
            val handlers = File(System.getProperty("user.home"), "Library/Preferences/com.apple.LaunchServices/com.apple.launchservices.secure.plist")
            val process = ProcessBuilder("/usr/bin/plutil", "-convert", "xml1", "-o", "-", handlers.path).redirectError(ProcessBuilder.Redirect.DISCARD).start()
            val xml = process.inputStream.bufferedReader().readText()
            process.waitFor()
            xml.split("<dict>").firstOrNull { MAC_HTTPS_SCHEME.containsMatchIn(it) }?.let { MAC_HANDLER.find(it)?.groupValues?.get(1) }
        }.getOrNull()

    private val MAC_HTTPS_SCHEME = Regex("""<key>LSHandlerURLScheme</key>\s*<string>https</string>""")
    private val MAC_HANDLER = Regex("""<key>LSHandlerRoleAll</key>\s*<string>([^<]+)</string>""")

    /** The default browser when it can be used (Firefox and Safari can't), otherwise any that is installed. */
    private fun findBrowser(): String? {
        val roots =
            if (Os.isMac) {
                listOf("/Applications", System.getProperty("user.home") + "/Applications")
            } else {
                listOfNotNull(System.getenv("ProgramFiles(x86)"), System.getenv("ProgramFiles"), System.getenv("LOCALAPPDATA"))
            }
        fun installed(app: String) = roots.map { File(it, app) }.firstOrNull(File::exists)?.path
        val preferred = defaultBrowserId()?.let { id -> browsers.firstOrNull { id.contains(it.first, ignoreCase = true) } }
        return (listOfNotNull(preferred) + browsers).firstNotNullOfOrNull { installed(it.second) }
    }

    /** The signed-in account, or null when the user closed the window first. */
    suspend fun signIn(): Account? =
        withContext(Dispatchers.IO) {
            val browser = findBrowser() ?: throw NoBrowserException()
            val port = ServerSocket(0).use { it.localPort }
            val arguments =
                listOf(
                    browser,
                    "--user-data-dir=${profile.path}",
                    "--remote-debugging-port=$port",
                    "--no-first-run",
                    "--no-default-browser-check",
                    "--window-size=520,780",
                ) +
                    // A profile of its own needs no keychain entry; this keeps macOS from asking for one.
                    listOfNotNull("--use-mock-keychain".takeIf { Os.isMac }) +
                    "--app=$SIGN_IN_URL"
            val process = ProcessBuilder(arguments).start()
            try {
                waitForPages(port)
                val deadline = System.currentTimeMillis() + SIGN_IN_TIMEOUT_MS
                while (System.currentTimeMillis() < deadline) {
                    delay(POLL_MS)
                    // The debugging port goes away with the last window: the user closed it.
                    val pages = pages(port) ?: return@withContext null
                    val music =
                        pages.firstOrNull {
                            it.string("type") == "page" && it.string("url")?.startsWith(MUSIC_URL) == true
                        } ?: continue
                    val socket = music.string("webSocketDebuggerUrl") ?: continue
                    runCatching { readAccount(socket) }.getOrNull()?.let { return@withContext it }
                }
                null
            } finally {
                closeBrowser(port)
                process.destroy()
            }
        }

    private suspend fun waitForPages(port: Int) {
        repeat(STARTUP_TRIES) {
            if (pages(port) != null) return
            delay(STARTUP_POLL_MS)
        }
    }

    private fun get(port: Int, path: String): String? =
        runCatching {
            http.newCall(Request.Builder().url("http://127.0.0.1:$port$path").build()).execute().use {
                if (it.isSuccessful) it.body.string() else null
            }
        }.getOrNull()

    private fun pages(port: Int): List<JsonObject>? =
        get(port, "/json/list")?.let { body -> json.parseToJsonElement(body).jsonArray.map { it.jsonObject } }

    private fun JsonObject.string(key: String): String? = get(key)?.jsonPrimitive?.contentOrNull

    private suspend fun readAccount(socket: String): Account? {
        val page = Cdp(socket)
        try {
            val params = buildJsonObject { put("urls", JsonArray(listOf(kotlinx.serialization.json.JsonPrimitive(MUSIC_URL)))) }
            val cookies = page.call("Network.getCookies", params)["cookies"]!!.jsonArray.map { it.jsonObject }
            if (cookies.none { it.string("name") == "SAPISID" }) return null
            val script = buildJsonObject {
                put("expression", PAGE_DATA_SCRIPT)
                put("returnByValue", true)
            }
            val value = page.call("Runtime.evaluate", script)["result"]?.jsonObject?.string("value") ?: return null
            if (value == "null") return null
            val data = json.parseToJsonElement(value).jsonObject
            val visitor = data.string("v")?.takeIf { it.isNotBlank() } ?: return null
            return Account(
                cookie = cookies.joinToString("; ") { "${it.string("name")}=${it.string("value")}" },
                visitorData = visitor,
                dataSyncId = data.string("d").orEmpty().substringBefore("||"),
                authUser = data.string("a") ?: "0",
            )
        } finally {
            page.close()
        }
    }

    private suspend fun closeBrowser(port: Int) {
        val version = get(port, "/json/version") ?: return
        val socket = json.parseToJsonElement(version).jsonObject.string("webSocketDebuggerUrl") ?: return
        val browser = Cdp(socket)
        runCatching { browser.call("Browser.close") }
        browser.close()
    }

    private const val STARTUP_TRIES = 40
    private const val STARTUP_POLL_MS = 500L
    private const val CALL_TIMEOUT_MS = 5_000L

    /** One Chrome DevTools Protocol connection. */
    private class Cdp(url: String) : WebSocketListener() {
        private val ids = AtomicInteger()
        private val pending = ConcurrentHashMap<Int, CompletableDeferred<JsonObject>>()
        private val socket: WebSocket = http.newWebSocket(Request.Builder().url(url).build(), this)

        suspend fun call(method: String, params: JsonObject = JsonObject(emptyMap())): JsonObject {
            val id = ids.incrementAndGet()
            val answer = CompletableDeferred<JsonObject>()
            pending[id] = answer
            socket.send(
                buildJsonObject {
                    put("id", id)
                    put("method", method)
                    put("params", params)
                }.toString(),
            )
            return withTimeout(CALL_TIMEOUT_MS) { answer.await() }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val message = json.parseToJsonElement(text).jsonObject
            val id = message["id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: return
            val answer = pending.remove(id) ?: return
            val result = message["result"]?.jsonObject
            if (result != null) answer.complete(result) else answer.completeExceptionally(IllegalStateException(text))
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            pending.values.forEach { it.completeExceptionally(t) }
            pending.clear()
        }

        fun close() {
            socket.close(NORMAL_CLOSE, null)
        }

        private companion object {
            const val NORMAL_CLOSE = 1000
        }
    }
}
