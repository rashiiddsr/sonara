/**
 * Sonara (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/** Mixes left and right into both ears when Mono audio is on; otherwise passes sound through. */
@UnstableApi
class MonoAudioProcessor : BaseAudioProcessor() {
    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat =
        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT && inputAudioFormat.channelCount == STEREO) {
            inputAudioFormat
        } else {
            AudioProcessor.AudioFormat.NOT_SET
        }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val output = replaceOutputBuffer(inputBuffer.remaining())
        if (enabled) {
            while (inputBuffer.remaining() >= BYTES_PER_FRAME) {
                val mixed = ((inputBuffer.getShort().toInt() + inputBuffer.getShort().toInt()) / STEREO).toShort()
                output.putShort(mixed)
                output.putShort(mixed)
            }
        } else {
            output.put(inputBuffer)
        }
        output.flip()
    }

    companion object {
        private const val STEREO = 2
        private const val BYTES_PER_FRAME = 4

        /** One setting for every player, including the second one used during a crossfade. */
        @Volatile
        var enabled = false
    }
}
