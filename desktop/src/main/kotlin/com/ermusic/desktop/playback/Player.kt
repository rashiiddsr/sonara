/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ermusic.desktop.playback

import com.ermusic.desktop.data.History
import com.ermusic.desktop.data.Settings
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.music.utils.YouTubeWebSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FrameGrabber
import java.nio.ShortBuffer
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/** One person credited on a song; [id] is missing when YouTube gave only the name. */
@Serializable
data class Credit(val name: String, val id: String? = null)

@Serializable
data class Track(
    val id: String,
    val title: String,
    val artists: String,
    val artUrl: String?,
    val durationMs: Long,
    val artistId: String? = null,
    val albumId: String? = null,
    val albumName: String? = null,
    val setVideoId: String? = null,
    val credits: List<Credit> = emptyList(),
) {
    val shareLink: String get() = "https://music.youtube.com/watch?v=$id"

    /** Songs saved before each artist was kept have only the joined names and the first id. */
    val allCredits: List<Credit>
        get() = credits.ifEmpty { artists.split(", ").mapIndexed { index, name -> Credit(name, artistId.takeIf { index == 0 }) } }
}

fun SongItem.toTrack() =
    Track(
        id = id,
        title = title,
        artists = artists.joinToString { it.name },
        artUrl = thumbnail,
        durationMs = (duration ?: 0) * MS_PER_SECOND,
        artistId = artists.firstOrNull()?.id,
        albumId = album?.id,
        albumName = album?.name,
        setVideoId = setVideoId,
        credits = artists.map { Credit(it.name, it.id) },
    )

enum class Repeat { Off, All, One }

data class PlayerState(
    val queue: List<Track> = emptyList(),
    val index: Int = -1,
    val playing: Boolean = false,
    val loading: Boolean = false,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: Repeat = Repeat.Off,
    val volume: Float = 1f,
    val muted: Boolean = false,
    val error: String? = null,
    val sleepAtMs: Long? = null,
    val sleepAfterSong: Boolean = false,
) {
    val current: Track? get() = queue.getOrNull(index)
    val next: Track?
        get() =
            when {
                repeat == Repeat.One -> null
                index + 1 in queue.indices -> queue[index + 1]
                repeat == Repeat.All -> queue.firstOrNull()
                else -> null
            }
    val sleepActive: Boolean get() = sleepAtMs != null || sleepAfterSong
}

/** Queue and transport. Songs are decoded by FFmpeg and written to the default audio device. */
class Player(private val scope: CoroutineScope) {
    private val mutableState = MutableStateFlow(PlayerState(volume = Settings.volume.value))
    val state: StateFlow<PlayerState> = mutableState.asStateFlow()

    private val mutablePosition = MutableStateFlow(0L)

    /** Apart from [state]: it changes many times a second, and few screens read it. */
    val position: StateFlow<Long> = mutablePosition.asStateFlow()

    private var loadJob: Job? = null
    private var radioJob: Job? = null
    private var sleepJob: Job? = null
    private var historyJob: Job? = null

    /** Called once a play has reached the account's history. */
    var onPlayReported: () -> Unit = {}
    private var radioContinuation: Pair<WatchEndpoint, String>? = null
    private var unshuffled: List<Track>? = null

    @Volatile private var session: Session? = null

    /** The song fading out under a crossfade. */
    @Volatile private var outgoing: Session? = null

    fun play(tracks: List<Track>, start: Int = 0) {
        if (tracks.isEmpty()) return
        radioJob?.cancel()
        radioContinuation = null
        unshuffled = null
        mutableState.update { it.copy(queue = tracks, shuffle = false) }
        load(start.coerceIn(tracks.indices))
    }

    /** Plays one song now and fills the queue with its radio. */
    fun playRadio(track: Track) {
        play(listOf(track))
        startRadio(WatchEndpoint(videoId = track.id))
    }

    /** Plays what YouTube's endpoint (a playlist's or an artist's radio or shuffle) answers. */
    fun playEndpoint(endpoint: WatchEndpoint) {
        scope.launch(Dispatchers.IO) {
            val result = YouTube.next(endpoint).getOrNull() ?: return@launch
            play(result.items.map { it.toTrack() }, result.currentIndex ?: 0)
            radioContinuation = result.continuation?.let { result.endpoint to it }
        }
    }

    private fun startRadio(endpoint: WatchEndpoint) {
        radioJob = scope.launch(Dispatchers.IO) { appendRadio(endpoint, null) }
    }

    private suspend fun appendRadio(endpoint: WatchEndpoint, continuation: String?) {
        val result = YouTube.next(endpoint, continuation).getOrNull() ?: return
        radioContinuation = result.continuation?.let { result.endpoint to it }
        mutableState.update { state ->
            val known = state.queue.mapTo(HashSet()) { it.id }
            state.copy(queue = state.queue + result.items.map { it.toTrack() }.filter { known.add(it.id) })
        }
    }

    fun playNext(tracks: List<Track>) {
        if (mutableState.value.current == null) return play(tracks)
        mutableState.update { state ->
            state.copy(queue = state.queue.take(state.index + 1) + tracks + state.queue.drop(state.index + 1))
        }
    }

    fun addToQueue(tracks: List<Track>) {
        if (mutableState.value.current == null) return play(tracks)
        mutableState.update { it.copy(queue = it.queue + tracks) }
    }

    fun toggle() {
        val state = mutableState.value
        if (state.current == null) return
        if (session == null && !state.loading) load(state.index, mutablePosition.value) else mutableState.update { it.copy(playing = !it.playing) }
    }

    fun next() {
        val state = mutableState.value
        when {
            state.index + 1 in state.queue.indices -> load(state.index + 1)
            state.repeat != Repeat.Off && state.queue.isNotEmpty() -> load(0)
        }
    }

    /** Back to the start of the song, or to the previous one in its first seconds. */
    fun previous() {
        val state = mutableState.value
        if (mutablePosition.value > RESTART_WITHIN_MS || state.index <= 0) seek(0) else load(state.index - 1)
    }

    fun jumpTo(index: Int) {
        if (index in mutableState.value.queue.indices) load(index)
    }

    fun removeAt(index: Int) {
        val state = mutableState.value
        if (index !in state.queue.indices || index == state.index) return
        mutableState.update {
            it.copy(queue = it.queue.filterIndexed { i, _ -> i != index }, index = if (index < it.index) it.index - 1 else it.index)
        }
    }

    fun clearQueue() {
        mutableState.update { state -> state.copy(queue = listOfNotNull(state.current), index = if (state.current == null) -1 else 0) }
    }

    fun seek(positionMs: Long) {
        outgoing?.cancelled = true
        mutablePosition.value = positionMs
        session?.seekTo = positionMs
    }

    fun seekBy(deltaMs: Long) {
        val state = mutableState.value
        seek((mutablePosition.value + deltaMs).coerceIn(0, state.durationMs.coerceAtLeast(0)))
    }

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        Settings.volume.value = clamped
        mutableState.update { it.copy(volume = clamped, muted = false) }
    }

    fun toggleMute() = mutableState.update { it.copy(muted = !it.muted) }

    fun cycleRepeat() =
        mutableState.update { it.copy(repeat = Repeat.entries[(it.repeat.ordinal + 1) % Repeat.entries.size]) }

    fun toggleShuffle() {
        mutableState.update { state ->
            val current = state.current ?: return@update state.copy(shuffle = !state.shuffle)
            if (state.shuffle) {
                val queue = unshuffled ?: state.queue
                unshuffled = null
                state.copy(queue = queue, index = queue.indexOf(current).coerceAtLeast(0), shuffle = false)
            } else {
                unshuffled = state.queue
                val rest = state.queue.filterIndexed { i, _ -> i != state.index }.shuffled()
                state.copy(queue = listOf(current) + rest, index = 0, shuffle = true)
            }
        }
    }

    /** Pauses after [minutes]. */
    fun sleepIn(minutes: Int) {
        sleepJob?.cancel()
        val at = System.currentTimeMillis() + minutes * MS_PER_MINUTE
        mutableState.update { it.copy(sleepAtMs = at, sleepAfterSong = false) }
        sleepJob =
            scope.launch {
                delay(minutes * MS_PER_MINUTE)
                mutableState.update { it.copy(playing = false, sleepAtMs = null) }
            }
    }

    fun sleepAfterSong() {
        sleepJob?.cancel()
        mutableState.update { it.copy(sleepAtMs = null, sleepAfterSong = true) }
    }

    fun cancelSleep() {
        sleepJob?.cancel()
        mutableState.update { it.copy(sleepAtMs = null, sleepAfterSong = false) }
    }

    fun shutdown() {
        outgoing?.cancelled = true
        session?.cancelled = true
        session = null
        loadJob?.cancel()
        mutableState.update { it.copy(playing = false) }
    }

    private fun load(index: Int, fromMs: Long = 0, crossfadeMs: Long = 0) {
        loadJob?.cancel()
        outgoing?.cancelled = true
        if (crossfadeMs > 0) {
            outgoing = session
        } else {
            session?.cancelled = true
            outgoing = null
        }
        session = null
        val track = mutableState.value.queue[index]
        mutablePosition.value = fromMs
        mutableState.update { it.copy(index = index, playing = true, loading = true, durationMs = track.durationMs, error = null) }
        History.add(track)
        reportWhenListened(track)
        loadJob =
            scope.launch(Dispatchers.IO) {
                val song =
                    try {
                        SongCache.get(track.id)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        mutableState.update { it.copy(loading = false, playing = false, error = "Couldn't load this song (${error.message})") }
                        return@launch
                    }
                if (!isActive) return@launch
                session = Session(song, fromMs, crossfadeMs).also { it.start() }
                prepareNext()
            }
    }

    /** Tells YouTube Music about the song once it has really been listened to, as the phone does. */
    private fun reportWhenListened(track: Track) {
        historyJob?.cancel()
        historyJob =
            scope.launch(Dispatchers.IO) {
                var played = 0L
                while (played < HISTORY_AFTER_MS) {
                    delay(HISTORY_TICK_MS)
                    val state = mutableState.value
                    if (state.current?.id != track.id) return@launch
                    if (state.playing && !state.loading) played += HISTORY_TICK_MS
                }
                if (YouTube.cookie != null && YouTubeWebSession.recordPlay(track.id, null)) onPlayReported()
            }
    }

    /** Readies the next song so it starts at once, and tops up a radio that is running out. */
    private fun prepareNext() {
        scope.launch(Dispatchers.IO) {
            val state = mutableState.value
            if (state.queue.size - state.index <= RADIO_TOP_UP_AT) {
                radioContinuation?.let { (endpoint, continuation) -> appendRadio(endpoint, continuation) }
            }
            mutableState.value.next?.let { runCatching { SongCache.get(it.id) } }
        }
    }

    private fun onSongEnded() {
        session = null
        val state = mutableState.value
        when {
            state.sleepAfterSong -> mutableState.update { it.copy(playing = false, sleepAfterSong = false) }
            state.repeat == Repeat.One -> load(state.index)
            state.next != null -> load(if (state.index + 1 in state.queue.indices) state.index + 1 else 0)
            Settings.autoplay.value && state.current != null -> continueWithRadio(state.current!!)
            else -> stopAtEnd()
        }
    }

    private fun stopAtEnd() {
        mutablePosition.value = 0
        mutableState.update { it.copy(playing = false) }
    }

    /** The queue ran out: carry on with songs like the last one. */
    private fun continueWithRadio(last: Track) {
        scope.launch(Dispatchers.IO) {
            appendRadio(WatchEndpoint(videoId = last.id), null)
            val state = mutableState.value
            if (state.index + 1 in state.queue.indices) load(state.index + 1) else stopAtEnd()
        }
    }

    private inner class Session(private val song: LocalSong, private val startMs: Long, private val fadeInMs: Long) : Thread("sonara-audio") {
        @Volatile var cancelled = false

        @Volatile var seekTo = NO_SEEK

        private var handedOffAtMs = NOT_HANDED_OFF
        private var totalMs = 0L
        private val equalizer = Equalizer(SAMPLE_RATE)

        // Only ever turned down: raising a quiet song could clip it.
        private val normalizeGain =
            song.loudnessDb?.takeIf { Settings.normalize.value && it > 0 }?.let { DECIBEL_BASE.pow(-it / DECIBELS_PER_DECADE).toFloat() } ?: 1f

        init {
            isDaemon = true
        }

        override fun run() {
            var ended = false
            try {
                avutil.av_log_set_level(avutil.AV_LOG_QUIET)
                FFmpegFrameGrabber(song.file.path).use { grabber ->
                    grabber.sampleMode = FrameGrabber.SampleMode.SHORT
                    grabber.audioChannels = CHANNELS
                    grabber.sampleRate = SAMPLE_RATE
                    grabber.start()
                    if (startMs > 0) moveTo(grabber, startMs)
                    val format = AudioFormat(SAMPLE_RATE.toFloat(), BITS, CHANNELS, true, false)
                    val line = AudioSystem.getSourceDataLine(format)
                    line.open(format, BUFFER_BYTES)
                    line.start()
                    totalMs = grabber.lengthInTime / US_PER_MS
                    mutableState.update { it.copy(loading = false, durationMs = totalMs, error = null) }
                    ended = pump(grabber, line)
                    if (ended && !cancelled) line.drain()
                    line.close()
                }
            } catch (error: Exception) {
                if (!cancelled) {
                    mutableState.update { it.copy(loading = false, playing = false, error = "Playback failed (${error.message})") }
                    session = null
                }
                return
            }
            if (ended && !cancelled && handedOffAtMs == NOT_HANDED_OFF) onSongEnded()
        }

        /** Decodes until the song ends (true) or this session is cancelled (false). */
        private fun pump(grabber: FFmpegFrameGrabber, line: SourceDataLine): Boolean {
            var bytes = ByteArray(BUFFER_BYTES)
            var published = 0L
            while (!cancelled) {
                val target = seekTo
                if (target != NO_SEEK) {
                    seekTo = NO_SEEK
                    line.flush()
                    moveTo(grabber, target)
                }
                val state = mutableState.value
                if (!state.playing) {
                    if (line.isRunning) line.stop()
                    sleep(PAUSE_POLL_MS)
                    continue
                }
                if (!line.isRunning) line.start()
                val frame = grabber.grabSamples() ?: return true
                val samples = frame.samples?.getOrNull(0) as? ShortBuffer ?: continue
                val count = samples.remaining()
                if (count * 2 > bytes.size) bytes = ByteArray(count * 2)
                val at = grabber.timestamp / US_PER_MS
                handOffIfDue(at, state)
                val gain = if (state.muted) 0f else state.volume * state.volume * normalizeGain * fade(at)
                val equalize = Settings.eqEnabled.value
                val mono = Settings.mono.value
                val gains = Settings.eqGains.value
                var i = 0
                while (i + 1 < count) {
                    var left = samples.get().toDouble()
                    var right = samples.get().toDouble()
                    if (equalize) {
                        equalizer.process(gains, left, right)
                        left = equalizer.left
                        right = equalizer.right
                    }
                    if (mono) {
                        left = (left + right) / 2
                        right = left
                    }
                    writeSample(bytes, i, left * gain)
                    writeSample(bytes, i + 1, right * gain)
                    i += 2
                }
                line.write(bytes, 0, count * 2)
                val now = System.currentTimeMillis()
                if (seekTo == NO_SEEK && session === this && now - published >= POSITION_PUBLISH_MS) {
                    published = now
                    val buffered = (line.bufferSize - line.available()) * MS_PER_SECOND / BYTES_PER_SECOND
                    mutablePosition.value = (grabber.timestamp / US_PER_MS - buffered).coerceAtLeast(0)
                }
            }
            return false
        }

        /**
         * The files only mark a place to jump to every 20 seconds or so, and before the first mark
         * FFmpeg lands on that mark instead of the time asked for. There, and whenever a jump
         * misses, the file is opened again and decoded up to the exact time.
         */
        private fun moveTo(grabber: FFmpegFrameGrabber, targetMs: Long) {
            if (targetMs >= EXACT_SEEK_BELOW_MS) {
                grabber.setTimestamp(targetMs * US_PER_MS)
                if (abs(grabber.timestamp / US_PER_MS - targetMs) <= SEEK_TOLERANCE_MS) return
            }
            grabber.restart()
            while (grabber.timestamp / US_PER_MS < targetMs) {
                grabber.grabSamples() ?: break
            }
        }

        private fun writeSample(bytes: ByteArray, index: Int, value: Double) {
            val sample = value.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            bytes[index * 2] = sample.toByte()
            bytes[index * 2 + 1] = (sample shr BITS_PER_BYTE).toByte()
        }

        /** With crossfade on, starts the next song while this one still has its last seconds. */
        private fun handOffIfDue(at: Long, state: PlayerState) {
            val crossfade = Settings.crossfadeSeconds.value * MS_PER_SECOND
            if (crossfade <= 0 || handedOffAtMs != NOT_HANDED_OFF || session !== this) return
            if (totalMs < crossfade * 2 || totalMs - at > crossfade) return
            if (state.next == null || state.repeat == Repeat.One || state.sleepAfterSong) return
            handedOffAtMs = at
            load(if (state.index + 1 in state.queue.indices) state.index + 1 else 0, crossfadeMs = crossfade)
        }

        private fun fade(at: Long): Float {
            val rising = if (fadeInMs > 0) ((at - startMs).toFloat() / fadeInMs).coerceIn(0f, 1f) else 1f
            val falling = if (handedOffAtMs != NOT_HANDED_OFF) ((totalMs - at).toFloat() / (totalMs - handedOffAtMs).coerceAtLeast(1)).coerceIn(0f, 1f) else 1f
            return rising * falling
        }
    }

    private companion object {
        const val NOT_HANDED_OFF = -1L
        const val EXACT_SEEK_BELOW_MS = 30_000L
        const val SEEK_TOLERANCE_MS = 1_500L
        const val HISTORY_AFTER_MS = 10_000L
        const val HISTORY_TICK_MS = 1_000L
        const val NO_SEEK = -1L
        const val SAMPLE_RATE = 48_000
        const val CHANNELS = 2
        const val BITS = 16
        const val BITS_PER_BYTE = 8
        const val BYTES_PER_SECOND = SAMPLE_RATE * CHANNELS * BITS / BITS_PER_BYTE
        const val BUFFER_BYTES = BYTES_PER_SECOND / 5
        const val US_PER_MS = 1000L
        const val PAUSE_POLL_MS = 30L
        const val POSITION_PUBLISH_MS = 40L
        const val RESTART_WITHIN_MS = 3_000L
        const val RADIO_TOP_UP_AT = 3
        const val DECIBEL_BASE = 10.0
        const val DECIBELS_PER_DECADE = 20.0
        const val MS_PER_MINUTE = 60_000L
    }
}

private const val MS_PER_SECOND = 1000L
