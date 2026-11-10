/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package android.util

object Base64 {
    const val NO_WRAP = 2

    fun encodeToString(input: ByteArray, flags: Int): String = java.util.Base64.getEncoder().encodeToString(input)
}
