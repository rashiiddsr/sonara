/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ermusic.desktop.auth

import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The Mac's counterpart of DPAPI: the session is sealed with AES-GCM under a key kept in the
 * login keychain, so only this user, signed in, can open it. The key goes to the `security` tool
 * through its standard input, never on a command line other processes could read.
 */
object MacKeychain {
    private const val TOOL = "/usr/bin/security"
    private const val SERVICE = "Sonara session key"
    private const val ACCOUNT = "Sonara"
    private const val KEY_BYTES = 32
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val TOOL_TIMEOUT_S = 10L

    private val random = SecureRandom()

    fun seal(plain: ByteArray): ByteArray {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key(create = true), "AES"), GCMParameterSpec(TAG_BITS, iv))
        return iv + cipher.doFinal(plain)
    }

    fun open(sealed: ByteArray): ByteArray {
        val key = key(create = false) ?: error("No session key in the keychain")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
        return cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
    }

    fun forget() {
        run(listOf(TOOL, "delete-generic-password", "-s", SERVICE, "-a", ACCOUNT))
    }

    private fun key(create: Boolean): ByteArray? {
        read()?.let { return it }
        if (!create) return null
        val fresh = ByteArray(KEY_BYTES).also(random::nextBytes)
        val encoded = Base64.getEncoder().encodeToString(fresh)
        run(listOf(TOOL, "-i"), input = "add-generic-password -U -s \"$SERVICE\" -a \"$ACCOUNT\" -w \"$encoded\"\n")
        // Should a system's tool not take commands that way, it takes the key as an argument.
        if (read() == null) run(listOf(TOOL, "add-generic-password", "-U", "-s", SERVICE, "-a", ACCOUNT, "-w", encoded))
        return read() ?: error("The keychain didn't keep the session key")
    }

    private fun read(): ByteArray? =
        run(listOf(TOOL, "find-generic-password", "-s", SERVICE, "-a", ACCOUNT, "-w"))
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }
            ?.takeIf { it.size == KEY_BYTES }

    /** The tool's output when it succeeded, otherwise null. */
    private fun run(command: List<String>, input: String? = null): String? =
        runCatching {
            val process = ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start()
            process.outputStream.use { stream -> input?.let { stream.write(it.toByteArray(Charsets.UTF_8)) } }
            val output = process.inputStream.bufferedReader().readText()
            if (!process.waitFor(TOOL_TIMEOUT_S, TimeUnit.SECONDS)) {
                process.destroy()
                return null
            }
            output.takeIf { process.exitValue() == 0 }
        }.getOrNull()
}
