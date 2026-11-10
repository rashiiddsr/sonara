/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ermusic.desktop.auth

import com.ermusic.desktop.AppFiles
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Linux's counterpart of DPAPI: the session is sealed with AES-GCM under a key kept in the
 * desktop's keyring (GNOME Keyring, KWallet) through `secret-tool`, which takes the key on its
 * standard input. Where there is no keyring, the key sits in a file only this user can read,
 * as browsers do on such systems.
 */
object LinuxKeyring {
    private const val TOOL = "secret-tool"
    private const val LABEL = "Sonara session key"
    private val attributes = listOf("application", "sonara", "purpose", "session")
    private const val KEY_BYTES = 32
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val TOOL_TIMEOUT_S = 30L

    private val random = SecureRandom()
    private val keyFile = File(AppFiles.root, "session.key")

    fun seal(plain: ByteArray): ByteArray {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key(create = true), "AES"), GCMParameterSpec(TAG_BITS, iv))
        return iv + cipher.doFinal(plain)
    }

    fun open(sealed: ByteArray): ByteArray {
        val key = key(create = false) ?: error("No session key")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
        return cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
    }

    fun forget() {
        run(listOf(TOOL, "clear") + attributes)
        keyFile.delete()
    }

    private fun key(create: Boolean): ByteArray? {
        read()?.let { return it }
        if (!create) return null
        val fresh = ByteArray(KEY_BYTES).also(random::nextBytes)
        val encoded = Base64.getEncoder().encodeToString(fresh)
        run(listOf(TOOL, "store", "--label=$LABEL") + attributes, input = encoded)
        if (fromKeyring() != null) {
            keyFile.delete()
            return fresh
        }
        writeKeyFile(encoded)
        return read() ?: error("The session key couldn't be kept")
    }

    private fun read(): ByteArray? = fromKeyring() ?: decode(runCatching { keyFile.readText() }.getOrNull())

    private fun fromKeyring(): ByteArray? = decode(run(listOf(TOOL, "lookup") + attributes))

    private fun decode(text: String?): ByteArray? =
        text?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }
            ?.takeIf { it.size == KEY_BYTES }

    /** Made readable by this user alone before the key goes in. */
    private fun writeKeyFile(encoded: String) {
        keyFile.delete()
        val path = keyFile.toPath()
        runCatching { Files.createFile(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))) }
            .onFailure { keyFile.createNewFile() }
        keyFile.writeText(encoded)
    }

    /** The tool's output when it succeeded, otherwise null (also when it isn't installed). */
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
