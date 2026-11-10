/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ermusic.desktop.auth

import java.util.concurrent.TimeUnit
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

class NoBrowserException : Exception("Google Chrome, Microsoft Edge, Brave, Vivaldi or Chromium is needed to sign in")

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
    private const val PROFILE_RELEASE_S = 5L
    private const val SIGN_IN_TIMEOUT_MS = 15 * 60 * 1000L
    private const val PAGE_DATA_SCRIPT =
        "JSON.stringify(window.yt&&yt.config_?{v:yt.config_.VISITOR_DATA,d:yt.config_.DATASYNC_ID," +
            "a:String(yt.config_.SESSION_INDEX||0)}:null)"

    private val profile = File(AppFiles.root, "signin-browser")

    // A browser from the Snap Store may only write under its own folder: the profile goes there.
    private fun snapProfile(name: String) = File(System.getProperty("user.home"), "snap/$name/common/sonara-signin")
    private val http = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * After signing in, the browser's own caches, models and components go (tens of megabytes it
     * fetches again if it is ever needed). Its settings and cookies stay, so signing in again later
     * remembers the account.
     */
    fun tidyProfile() {
        if (signingIn) return
        val profiles = listOf(profile) + if (Os.isLinux) linuxBrowsers.flatMap { it.second }.map(::snapProfile) else emptyList()
        profiles.filter(File::isDirectory).forEach { root ->
            root.listFiles().orEmpty().filter { it.name !in PROFILE_KEEP }.forEach { it.deleteRecursively() }
            File(root, "Default").listFiles().orEmpty().filter { it.name in PROFILE_CACHES }.forEach { it.deleteRecursively() }
        }
    }

    @Volatile private var signingIn = false

    // A profile's top level: the browser's key for its cookies and the user's profile.
    private val PROFILE_KEEP = setOf("Local State", "Default", "First Run")

    // Inside the profile: caches only.
    private val PROFILE_CACHES =
        setOf(
            "Cache", "Code Cache", "GPUCache", "DawnCache", "DawnGraphiteCache", "DawnWebGPUCache", "GrShaderCache", "ShaderCache",
            "Service Worker", "Extensions", "EdgeCoupons", "Edge Profile Picture.png", "optimization_guide_hint_cache_store",
        )

    fun forgetBrowserProfile() {
        profile.deleteRecursively()
        if (Os.isLinux) linuxBrowsers.flatMap { it.second }.forEach { snapProfile(it).deleteRecursively() }
    }

    /** Linux: browsers by the menu entry the system names them by, and the commands they go by. */
    private val linuxBrowsers =
        listOf(
            "google-chrome" to listOf("google-chrome-stable", "google-chrome"),
            "microsoft-edge" to listOf("microsoft-edge-stable", "microsoft-edge"),
            "brave" to listOf("brave-browser", "brave"),
            "vivaldi" to listOf("vivaldi-stable", "vivaldi"),
            "chromium" to listOf("chromium", "chromium-browser"),
        )

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

    private fun defaultBrowserId(): String? =
        when {
            Os.isMac -> macDefaultBrowserId()
            Os.isLinux -> linuxDefaultBrowserId()
            else -> windowsDefaultBrowserId()
        }

    /** The menu entry Linux opens links with, google-chrome.desktop say. */
    private fun linuxDefaultBrowserId(): String? =
        runCatching {
            val process = ProcessBuilder("xdg-settings", "get", "default-web-browser").redirectError(ProcessBuilder.Redirect.DISCARD).start()
            val id = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            id.takeIf { it.isNotEmpty() }
        }.getOrNull()

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
        if (Os.isLinux) return findLinuxBrowser()
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

    /** On Linux, browsers are commands: on the PATH, in the usual places, or from the Snap Store last. */
    private fun findLinuxBrowser(): String? {
        val path = System.getenv("PATH").orEmpty().split(':').filter { it.startsWith("/") && !it.startsWith("/snap") }
        val folders = (path + listOf("/usr/bin", "/usr/local/bin", "/opt/google/chrome", "/opt/microsoft/msedge", "/opt/brave.com/brave", "/opt/vivaldi")).distinct()
        val default = defaultBrowserId()
        val ordered = linuxBrowsers.sortedBy { (id, _) -> if (default?.contains(id, ignoreCase = true) == true) 0 else 1 }
        fun found(folders: List<String>) =
            ordered.firstNotNullOfOrNull { (_, names) ->
                names.firstNotNullOfOrNull { name -> folders.map { File(it, name) }.firstOrNull { it.isFile && it.canExecute() }?.path }
            }
        return found(folders) ?: found(listOf("/snap/bin"))
    }

    /**
     * Where the sign-in browser keeps its profile. A browser from the Snap Store, or a small script
     * that starts one (Ubuntu's chromium-browser is such a script), gets one under its own folder.
     */
    private fun profileFor(browser: String): File {
        if (browser.startsWith("/snap/")) return snapProfile(File(browser).name)
        val file = File(browser)
        val script = if (file.length() in 1..SCRIPT_BYTES) runCatching { file.readText() }.getOrNull() else null
        val snap = script?.let { SNAP_COMMAND.find(it)?.groupValues?.get(1) }
        return if (snap != null) snapProfile(snap) else profile
    }

    private const val SCRIPT_BYTES = 16_384L
    private val SNAP_COMMAND = Regex("""/snap/bin/([a-z0-9-]+)""")

    /** The signed-in account, or null when the user closed the window first. */
    suspend fun signIn(): Account? =
        withContext(Dispatchers.IO) {
            val browser = findBrowser() ?: throw NoBrowserException()
            val port = ServerSocket(0).use { it.localPort }
            val arguments =
                listOf(
                    browser,
                    "--user-data-dir=${profileFor(browser).path}",
                    "--remote-debugging-port=$port",
                    "--no-first-run",
                    "--no-default-browser-check",
                    "--window-size=520,780",
                ) +
                    // A profile of its own needs no keychain entry; this keeps macOS from asking for one.
                    listOfNotNull("--use-mock-keychain".takeIf { Os.isMac }) +
                    "--app=$SIGN_IN_URL"
            signingIn = true
            val process = runCatching { ProcessBuilder(arguments).start() }.onFailure { signingIn = false }.getOrThrow()
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
                // Once the browser has let go of its files.
                runCatching { process.waitFor(PROFILE_RELEASE_S, TimeUnit.SECONDS) }
                signingIn = false
                runCatching { tidyProfile() }
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
