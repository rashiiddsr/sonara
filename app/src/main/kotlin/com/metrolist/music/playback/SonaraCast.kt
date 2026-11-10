/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import android.os.SystemClock
import android.os.Looper
import android.os.Handler
import com.metrolist.music.ui.utils.resize
import com.metrolist.music.lyrics.LyricsUtils
import com.google.android.gms.cast.TextTrackStyle
import com.google.android.gms.cast.MediaTrack
import android.util.Base64
import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import androidx.core.content.getSystemService
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions
import com.google.android.gms.common.images.WebImage
import com.metrolist.music.constants.AudioQuality
import com.metrolist.music.extensions.metadata
import com.metrolist.music.utils.InnerTubeXPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import com.google.android.gms.cast.MediaMetadata as CastMetadata

private const val LYRICS_TRACK_ID = 1L
private const val TV_ART_PX = 1200

/** Synced lyrics (LRC) as WebVTT cues; null when the lyrics have no timing. */
internal fun toWebVtt(lrc: String): String? {
    val lines = LyricsUtils.parseLyrics(lrc).filter { it.text.isNotBlank() }
    if (lines.size < 2 || lines.all { it.time == 0L }) return null
    fun stamp(ms: Long) = "%02d:%02d:%02d.%03d".format(ms / 3_600_000, ms / 60_000 % 60, ms / 1000 % 60, ms % 1000)
    return buildString {
        append("WEBVTT\n\n")
        lines.forEachIndexed { i, line ->
            val end = lines.getOrNull(i + 1)?.time ?: (line.time + 5_000)
            append(stamp(line.time)).append(" --> ").append(stamp(maxOf(end, line.time + 500))).append('\n')
            append(line.text.trim()).append("\n\n")
        }
    }
}

/** Default Media Receiver; plays a plain stream URL with metadata, no custom receiver needed. */
class CastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder()
            .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
            // The app's own MediaSession and notification stay in charge; Cast adds none.
            .setCastMediaOptions(CastMediaOptions.Builder().setNotificationOptions(null).setMediaSessionEnabled(false).build())
            .setStopReceiverApplicationWhenEndingSession(true)
            .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}

data class CastState(
    val available: Boolean = false,
    val casting: Boolean = false,
    val deviceName: String? = null,
    val error: String? = null,
)

/**
 * Experimental Google Cast. While a Cast device is connected the phone keeps playing the queue
 * muted, and the TV mirrors it: the current track is resolved to a stream URL and loaded on the
 * Default Media Receiver, then play/pause, seeks and track changes follow the phone. Queue, skip,
 * like and lyrics therefore keep working unchanged.
 *
 * Known limits of this trial: the stream is fetched twice (phone and TV), downloads are streamed
 * rather than sent from the phone, and some streams may be refused by the TV because YouTube
 * expects headers the receiver cannot send.
 */
object SonaraCast {
    private val _state = MutableStateFlow(CastState())
    val state: StateFlow<CastState> = _state.asStateFlow()

    /** Multiplier for the phone's own volume: 0 while casting. */
    val localVolume = MutableStateFlow(1f)

    private var castContext: CastContext? = null
    private var player: Player? = null
    private var scope: CoroutineScope? = null
    private var appContext: Context? = null
    private var loadJob: Job? = null
    private var lyricsFor: (suspend (String) -> String?)? = null
    private var loadedId: String? = null

    fun context(context: Context): CastContext? {
        castContext?.let { return it }
        return runCatching { CastContext.getSharedInstance(context.applicationContext) }
            .onFailure { Timber.w(it, "Cast unavailable") }
            .getOrNull()
            ?.also {
                castContext = it
                _state.value = _state.value.copy(available = true)
            }
    }

    /** [players] follows the service's current player, which is replaced during crossfades. */
    fun attach(
        context: Context,
        players: StateFlow<Player?>,
        scope: CoroutineScope,
        lyrics: suspend (String) -> String? = { null },
    ) {
        lyricsFor = lyrics
        val cast = context(context) ?: return
        this.appContext = context.applicationContext
        this.scope = scope
        scope.launch {
            players.collect { next ->
                player?.removeListener(playerListener)
                player = next
                next?.addListener(playerListener)
            }
        }
        cast.sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)
        cast.sessionManager.currentCastSession?.takeIf { it.isConnected }?.let(::onConnected)
    }

    fun detach() {
        player?.removeListener(playerListener)
        castContext?.sessionManager?.removeSessionManagerListener(sessionListener, CastSession::class.java)
        player = null
        scope = null
    }

    /**
     * Steps the Cast device's volume; true when a Cast session took it. Each step used to start
     * from the volume the TV last reported, which only changes once the TV answers, so quick or
     * held presses sent the same value again and again: the volume stuck, then jumped. Steps now
     * add up here and the latest value goes out at most every [VOLUME_SEND_INTERVAL_MS].
     */
    fun stepVolume(up: Boolean): Boolean {
        val s = session ?: return false
        val now = SystemClock.uptimeMillis()
        val base = targetVolume?.takeIf { now - lastVolumeStepAt < VOLUME_TARGET_KEEP_MS } ?: s.volume
        targetVolume = (base + if (up) VOLUME_STEP else -VOLUME_STEP).coerceIn(0.0, 1.0)
        lastVolumeStepAt = now
        volumeHandler.removeCallbacks(sendVolume)
        val wait = VOLUME_SEND_INTERVAL_MS - (now - lastVolumeSentAt)
        if (wait <= 0) sendVolume.run() else volumeHandler.postDelayed(sendVolume, wait)
        return true
    }

    private var targetVolume: Double? = null
    private var lastVolumeStepAt = 0L
    private var lastVolumeSentAt = 0L
    private val volumeHandler by lazy { Handler(Looper.getMainLooper()) }
    private val sendVolume = Runnable {
        val volume = targetVolume ?: return@Runnable
        runCatching { session?.volume = volume }
        lastVolumeSentAt = SystemClock.uptimeMillis()
    }

    private const val VOLUME_STEP = 0.05
    private const val VOLUME_SEND_INTERVAL_MS = 150L

    /** After this long without a press, the next one starts again from the TV's own volume. */
    private const val VOLUME_TARGET_KEEP_MS = 1500L

    fun stopCasting() {
        castContext?.sessionManager?.endCurrentSession(true)
    }

    private val session get() = castContext?.sessionManager?.currentCastSession?.takeIf { it.isConnected }
    private val remote get() = session?.remoteMediaClient

    private fun onConnected(session: CastSession) {
        _state.value = _state.value.copy(casting = true, deviceName = session.castDevice?.friendlyName, error = null)
        localVolume.value = 0f
        loadedId = null
        loadCurrent(startAt = player?.currentPosition ?: 0L)
    }

    private fun onDisconnected() {
        loadJob?.cancel()
        loadedId = null
        localVolume.value = 1f
        _state.value = _state.value.copy(casting = false, deviceName = null)
    }

    private fun loadCurrent(startAt: Long) {
        val p = player ?: return
        val item: MediaItem = p.currentMediaItem ?: return
        val meta = item.metadata ?: return
        val context = appContext ?: return
        if (loadedId == meta.id) return
        loadJob?.cancel()
        loadJob = scope?.launch {
            val playback = withContext(Dispatchers.IO) {
                InnerTubeXPlayer.playerResponseForPlayback(
                    videoId = meta.id,
                    audioQuality = AudioQuality.HIGH,
                    connectivityManager = context.getSystemService<ConnectivityManager>()!!,
                    allowBoundedRange = false,
                )
            }.getOrElse {
                _state.value = _state.value.copy(error = it.message)
                return@launch
            }
            val castMeta = CastMetadata(CastMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
                putString(CastMetadata.KEY_TITLE, meta.title)
                putString(CastMetadata.KEY_ARTIST, meta.artists.joinToString { it.name })
                meta.album?.title?.let { putString(CastMetadata.KEY_ALBUM_TITLE, it) }
                // Large artwork so it stays sharp on a TV.
                meta.thumbnailUrl?.let { addImage(WebImage(Uri.parse(it.resize(TV_ART_PX, TV_ART_PX)))) }
            }
            // Synced lyrics ride along as a subtitle track. A data: URL avoids serving files from
            // the phone (the receiver page is HTTPS and would block plain HTTP from the LAN).
            val vtt = lyricsFor?.invoke(meta.id)?.let(::toWebVtt)
            val info = MediaInfo.Builder(playback.streamUrl)
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType(playback.format.mimeType.substringBefore(";").trim())
                .setMetadata(castMeta)
                .apply {
                    if (vtt != null) {
                        setMediaTracks(
                            listOf(
                                MediaTrack.Builder(LYRICS_TRACK_ID, MediaTrack.TYPE_TEXT)
                                    .setName("Lyrics")
                                    .setSubtype(MediaTrack.SUBTYPE_SUBTITLES)
                                    .setContentId("data:text/vtt;base64," + Base64.encodeToString(vtt.toByteArray(), Base64.NO_WRAP))
                                    .setContentType("text/vtt")
                                    .setLanguage("und")
                                    .build(),
                            ),
                        )
                        setTextTrackStyle(
                            TextTrackStyle().apply {
                                fontScale = 1.3f
                                foregroundColor = 0xFFFFFFFF.toInt()
                                backgroundColor = 0x99000000.toInt()
                                edgeType = TextTrackStyle.EDGE_TYPE_NONE
                            },
                        )
                    }
                }.build()
            val request = MediaLoadRequestData.Builder()
                .setMediaInfo(info)
                .setAutoplay(player?.playWhenReady ?: true)
                .setCurrentTime(startAt)
                .apply { if (vtt != null) setActiveTrackIds(longArrayOf(LYRICS_TRACK_ID)) }
                .build()
            remote?.load(request)?.setResultCallback { result ->
                if (result.status.isSuccess) {
                    loadedId = meta.id
                } else {
                    _state.value = _state.value.copy(error = result.status.statusMessage ?: "Cast load failed (${result.status.statusCode})")
                }
            }
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (session != null) loadCurrent(startAt = 0L)
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            val r = remote ?: return
            if (playWhenReady) r.play() else r.pause()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (reason != Player.DISCONTINUITY_REASON_SEEK) return
            if (oldPosition.mediaItemIndex != newPosition.mediaItemIndex) return
            remote?.seek(MediaSeekOptions.Builder().setPosition(newPosition.positionMs).build())
        }
    }

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) = onConnected(session)
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = onConnected(session)
        override fun onSessionEnded(session: CastSession, error: Int) = onDisconnected()
        override fun onSessionSuspended(session: CastSession, reason: Int) = onDisconnected()
        override fun onSessionStartFailed(session: CastSession, error: Int) {
            _state.value = _state.value.copy(error = "Could not connect ($error)")
        }
        override fun onSessionStarting(session: CastSession) = Unit
        override fun onSessionEnding(session: CastSession) = Unit
        override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
        override fun onSessionResumeFailed(session: CastSession, error: Int) = Unit
    }
}
