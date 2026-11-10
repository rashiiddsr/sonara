/**
 * Sonara (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Song recognition is based on MusicRecognizer by Aleksey Saenko
 * (https://github.com/aleksey-saenko/MusicRecognizer) and the Shazam signature algorithm from
 * SongRec (https://github.com/marin-m/SongRec).
 *
 * Experiment requested by the owner: it uses Shazam's unofficial endpoint, so it can stop
 * working at any time. Everything lives in this package, `com.metrolist.shazamkit` and
 * `SongRecognitionSheet`, so it can be removed in one go.
 */

package com.metrolist.music.recognition

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.metrolist.shazamkit.Shazam
import com.metrolist.shazamkit.models.RecognitionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import kotlin.math.sqrt

/** What listening for a song ended with. */
sealed interface SongRecognition {
    data class Match(val result: RecognitionResult) : SongRecognition

    /** Heard the audio but Shazam knows no recording like it (a sung or hummed tune ends here). */
    data object NoMatch : SongRecognition

    /** The microphone or the network failed. */
    data object Failed : SongRecognition
}

/**
 * Listens through the microphone and asks Shazam what is playing. It tries after 4, 8 and 12
 * seconds of audio, so a clear song is found early, while a quiet one still gets the full
 * 12 seconds. Recording keeps running while a request is out, so the audio has no gaps.
 */
object SongRecognizer {
    private const val TAG = "SongRecognizer"
    private const val SAMPLE_RATE = 44_100
    private const val BYTES_PER_SAMPLE = 2
    private const val POLL_MS = 50L
    private const val LEVEL_GAIN = 6f
    private val CHECKPOINTS_MS = longArrayOf(4_000L, 8_000L, 12_000L)

    /** The longest it listens, for progress shown to the user. */
    const val MAX_LISTEN_MS = 12_000L

    /** Needs the RECORD_AUDIO permission; [onLevel] gets the loudness, 0 to 1, as it listens. */
    @SuppressLint("MissingPermission")
    suspend fun recognize(onLevel: (Float) -> Unit): SongRecognition =
        withContext(Dispatchers.IO) {
            val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minBuffer <= 0) return@withContext SongRecognition.Failed
            val record =
                runCatching {
                    AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuffer * 2)
                }.getOrNull()
            if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
                record?.release()
                return@withContext SongRecognition.Failed
            }
            val pcm = ByteArrayOutputStream()
            var micFailed = false
            try {
                record.startRecording()
                coroutineScope {
                    val reader =
                        launch(Dispatchers.IO) {
                            val buffer = ByteArray(minBuffer)
                            while (isActive) {
                                val read = record.read(buffer, 0, buffer.size)
                                if (read < 0) {
                                    micFailed = true
                                    break
                                }
                                if (read > 0) {
                                    synchronized(pcm) { pcm.write(buffer, 0, read) }
                                    onLevel(level(buffer, read))
                                }
                            }
                        }
                    var outcome: SongRecognition = SongRecognition.NoMatch
                    for (checkpoint in CHECKPOINTS_MS) {
                        val target = SAMPLE_RATE * BYTES_PER_SAMPLE * checkpoint / 1000
                        while (synchronized(pcm) { pcm.size() } < target && !micFailed) delay(POLL_MS)
                        if (micFailed) {
                            outcome = SongRecognition.Failed
                            break
                        }
                        outcome = identify(synchronized(pcm) { pcm.toByteArray() })
                        if (outcome is SongRecognition.Match) break
                    }
                    reader.cancelAndJoin()
                    outcome
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Recognition failed")
                SongRecognition.Failed
            } finally {
                runCatching { record.stop() }
                record.release()
            }
        }

    private suspend fun identify(audio: ByteArray): SongRecognition {
        val resampled =
            AudioResampler
                .resample(
                    DecodedAudio(audio, channelCount = 1, sampleRate = SAMPLE_RATE, pcmEncoding = AudioFormat.ENCODING_PCM_16BIT),
                    VibraSignature.REQUIRED_SAMPLE_RATE,
                ).getOrElse { return SongRecognition.Failed }
        val signature = runCatching { VibraSignature.fromI16(resampled.data) }.getOrElse { return SongRecognition.Failed }
        val durationMs = (resampled.data.size / BYTES_PER_SAMPLE) * 1000L / VibraSignature.REQUIRED_SAMPLE_RATE
        return Shazam.recognize(signature, durationMs).fold(
            onSuccess = { SongRecognition.Match(it) },
            onFailure = { error ->
                // A miss on a short sample is normal: the next checkpoint has more audio.
                if (error.message?.contains("No match", ignoreCase = true) == true) {
                    SongRecognition.NoMatch
                } else {
                    Timber.tag(TAG).w(error, "Shazam request failed")
                    SongRecognition.Failed
                }
            },
        )
    }

    /** Loudness of a 16-bit little-endian buffer, scaled so speech and music fill most of 0..1. */
    private fun level(
        buffer: ByteArray,
        length: Int,
    ): Float {
        var sum = 0.0
        val samples = length / BYTES_PER_SAMPLE
        if (samples == 0) return 0f
        for (i in 0 until samples) {
            val s = (buffer[i * 2].toInt() and 0xFF) or (buffer[i * 2 + 1].toInt() shl 8)
            sum += s.toDouble() * s
        }
        return (sqrt(sum / samples).toFloat() / Short.MAX_VALUE * LEVEL_GAIN).coerceIn(0f, 1f)
    }
}
