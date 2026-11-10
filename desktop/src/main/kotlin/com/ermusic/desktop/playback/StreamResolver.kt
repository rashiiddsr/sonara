/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ermusic.desktop.playback

import com.ermusic.desktop.AppFiles
import com.ermusic.desktop.data.Settings
import com.metrolist.innertube.YouTube
import com.metrolist.innertubex.InnerTubeLogger
import com.metrolist.innertubex.cipher.PlayerConfigRepository
import com.metrolist.innertubex.cipher.RemotePlayerConfigStore
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.AudioQuality
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.extraction.PoTokenResult
import com.metrolist.innertubex.extraction.TokenProvider
import com.metrolist.innertubex.extraction.TokenProviderCapabilities
import com.metrolist.innertubex.extraction.YtConfigParser
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import com.metrolist.innertubex.extraction.generateClientPlaybackNonce
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.io.File
import java.util.Properties

/** A playable audio URL and what it takes to read it. */
data class ResolvedStream(
    val url: String,
    val headers: Map<String, String>,
    val client: String,
    val mimeType: String?,
    val loudnessDb: Double?,
)

/** Finds the audio URL of a song, through the same extractor the Android app uses. */
object StreamResolver {
    private data class Bundle(val generation: Long, val cipher: YouTubeCipherService, val extractor: InnerTubeExtractor)

    @Volatile private var current: Bundle? = null
    private val mutex = Mutex()

    private val logger = InnerTubeLogger { event -> Timber.d("${event.tag}: ${event.message} ${event.details}") }

    // The Android app runs BotGuard in a WebView; here the extractor sticks to the clients that
    // work without its token.
    private val noTokens =
        object : TokenProvider {
            override val capabilities = TokenProviderCapabilities(providers = emptySet(), usesWebView = false)

            override suspend fun getPoToken(videoId: String, visitorData: String, cookie: String?): PoTokenResult? = null

            override suspend fun close() = Unit
        }

    suspend fun resolve(videoId: String): ResolvedStream {
        val hints = ContentHints().withStreamCapabilities(allowHls = false, allowSabr = false, allowBoundedRange = true)
        val stream =
            requireNotNull(
                bundle().extractor.extract(
                    videoId = videoId,
                    hints = hints,
                    excludedClients = emptySet(),
                    audioQuality = if (Settings.highQuality.value) AudioQuality.HIGH else AudioQuality.LOW,
                    clientPlaybackNonce = generateClientPlaybackNonce(),
                ),
            ) { "No playable stream" }
        return ResolvedStream(stream.audioUrl, stream.headers, stream.clientName, stream.mimeType, stream.loudnessDb)
    }

    private suspend fun bundle(): Bundle {
        val transport = YouTube.extractionTransport()
        current?.takeIf { it.generation == transport.generation }?.let { return it }
        return mutex.withLock {
            val latest = YouTube.extractionTransport()
            current?.takeIf { it.generation == latest.generation }?.let { return@withLock it }
            runCatching { current?.cipher?.dispose() }
            val store = RemotePlayerConfigStore(latest.httpClient, ConfigFile, logger)
            val cipher = YouTubeCipherService(latest.httpClient, store, logger)
            val parser = YtConfigParserImpl(latest.httpClient, latest.innerTube, store, logger).withEmbeddedFallback()
            val extractor =
                InnerTubeExtractor(
                    configParser = parser,
                    cipherService = cipher,
                    innerTube = latest.innerTube,
                    tokenProvider = noTokens,
                    logger = logger,
                )
            Bundle(latest.generation, cipher, extractor).also { current = it }
        }
    }

    private fun YtConfigParser.withEmbeddedFallback(): YtConfigParser =
        object : YtConfigParser by this {
            override suspend fun fetchConfig(videoId: String, useLoginCookies: Boolean) =
                try {
                    this@withEmbeddedFallback.fetchConfig(videoId, useLoginCookies)
                } catch (_: IllegalStateException) {
                    this@withEmbeddedFallback.fetchEmbeddedConfig(videoId, useLoginCookies = false)
                }
        }

    /** The player configuration cached between runs. */
    private object ConfigFile : PlayerConfigRepository {
        private const val SOURCE =
            "https://raw.githubusercontent.com/ZemerTeam/zemer-cipher/master/library/src/main/assets/player_configs.json"
        private val file = File(AppFiles.root, "player_config.properties")
        private val values = Properties().apply { if (file.exists()) file.inputStream().use(::load) }

        private fun put(key: String, value: String) {
            values.setProperty(key, value)
            file.outputStream().use { values.store(it, null) }
        }

        override val enabled = true
        override val sourceUrl = SOURCE
        override val defaultSourceUrl = SOURCE
        override var cachedJson: String
            get() = values.getProperty("json", "")
            set(value) = put("json", value)
        override var cachedAtMs: Long
            get() = values.getProperty("cached_at_ms", "0").toLong()
            set(value) = put("cached_at_ms", value.toString())
        override var cachedSourceUrl: String
            get() = values.getProperty("source_url", "")
            set(value) = put("source_url", value)
        override var cachedEtag: String
            get() = values.getProperty("etag", "")
            set(value) = put("etag", value)
    }
}
