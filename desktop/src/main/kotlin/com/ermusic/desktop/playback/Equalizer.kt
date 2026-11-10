/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ermusic.desktop.playback

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** A ten-band graphic equalizer: peaking filters run over the decoded stereo samples. */
class Equalizer(private val sampleRate: Int) {
    private class Band(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double) {
        var z1Left = 0.0
        var z2Left = 0.0
        var z1Right = 0.0
        var z2Right = 0.0
    }

    private var applied = ""
    private var bands = emptyList<Band>()
    private var preamp = 1.0

    var left = 0.0
        private set
    var right = 0.0
        private set

    private fun prepare(gains: String) {
        if (gains == applied) return
        applied = gains
        val values = parse(gains)
        val highest = values.max().coerceAtLeast(0f)
        // Taken back by the loudest boost, so the filters can't push a sample past full scale.
        preamp = DECIBEL_BASE.pow(-highest / DECIBELS_PER_DECADE.toDouble())
        bands = FREQUENCIES.indices.filter { values[it] != 0f && FREQUENCIES[it] < sampleRate / 2 }.map { band(FREQUENCIES[it].toDouble(), values[it].toDouble()) }
    }

    private fun band(frequency: Double, gain: Double): Band {
        val a = DECIBEL_BASE.pow(gain / PEAK_DIVISOR)
        val omega = 2 * PI * frequency / sampleRate
        val alpha = sin(omega) / (2 * QUALITY)
        val cosine = cos(omega)
        val a0 = 1 + alpha / a
        return Band((1 + alpha * a) / a0, -2 * cosine / a0, (1 - alpha * a) / a0, -2 * cosine / a0, (1 - alpha / a) / a0)
    }

    /** Runs one stereo sample; the result is in [left] and [right]. */
    fun process(gains: String, inputLeft: Double, inputRight: Double) {
        prepare(gains)
        var l = inputLeft * preamp
        var r = inputRight * preamp
        for (band in bands) {
            val outLeft = band.b0 * l + band.z1Left
            band.z1Left = band.b1 * l - band.a1 * outLeft + band.z2Left
            band.z2Left = band.b2 * l - band.a2 * outLeft
            l = outLeft
            val outRight = band.b0 * r + band.z1Right
            band.z1Right = band.b1 * r - band.a1 * outRight + band.z2Right
            band.z2Right = band.b2 * r - band.a2 * outRight
            r = outRight
        }
        left = l
        right = r
    }

    companion object {
        private const val DECIBEL_BASE = 10.0
        private const val DECIBELS_PER_DECADE = 20f
        private const val PEAK_DIVISOR = 40.0
        private const val QUALITY = 1.41
        const val MAX_GAIN_DB = 12f

        val FREQUENCIES = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
        val LABELS = listOf("31", "62", "125", "250", "500", "1K", "2K", "4K", "8K", "16K")

        val PRESETS =
            linkedMapOf(
                "Flat" to listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
                "Bass boost" to listOf(7, 6, 4, 2, 0, 0, 0, 0, 0, 0),
                "Treble boost" to listOf(0, 0, 0, 0, 0, 1, 3, 5, 6, 7),
                "Vocal" to listOf(-2, -2, -1, 1, 3, 4, 3, 1, 0, -1),
                "Rock" to listOf(5, 3, 1, -1, -2, -1, 1, 3, 4, 5),
                "Pop" to listOf(-1, 1, 3, 4, 3, 0, -1, -1, 0, 1),
                "Acoustic" to listOf(3, 3, 2, 1, 1, 1, 2, 3, 3, 2),
                "Night" to listOf(-3, -2, -1, 0, 1, 2, 2, 1, -1, -3),
            )

        fun parse(gains: String): List<Float> {
            val values = gains.split(',').mapNotNull { it.toFloatOrNull() }
            return if (values.size == FREQUENCIES.size) values else List(FREQUENCIES.size) { 0f }
        }

        fun encode(values: List<Float>): String = values.joinToString(",")
    }
}
