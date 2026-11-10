/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ermusic.desktop

import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The translator's key arrives sealed (AES-GCM, by the build: `sealKey` in desktop/build.gradle.kts),
 * so the app holds no readable copy of it, and it is opened only when a translation is asked for.
 * The Android app uses the same scheme (lyrics/SealedKey.kt); the two must stay alike.
 */
internal object SealedKey {
    private const val PEPPER = "Sonara/lyrics/translator/2026"
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    /** The key, or "" when there is none or it can't be opened. */
    fun open(sealed: String): String {
        if (sealed.isEmpty()) return ""
        return runCatching {
            val bytes = Base64.getDecoder().decode(sealed)
            val salt = bytes.copyOfRange(0, SALT_BYTES)
            val iv = bytes.copyOfRange(SALT_BYTES, SALT_BYTES + IV_BYTES)
            val secret = MessageDigest.getInstance("SHA-256").digest(salt + PEPPER.toByteArray(Charsets.UTF_8))
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(secret, "AES"), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(bytes, SALT_BYTES + IV_BYTES, bytes.size - SALT_BYTES - IV_BYTES), Charsets.UTF_8)
        }.getOrDefault("")
    }
}
