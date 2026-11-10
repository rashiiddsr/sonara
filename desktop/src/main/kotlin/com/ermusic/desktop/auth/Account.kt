/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ermusic.desktop.auth

import com.ermusic.desktop.AppFiles
import com.ermusic.desktop.Os
import com.metrolist.innertube.YouTube
import com.sun.jna.platform.win32.Crypt32Util
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** What YouTube Music's web API needs to act for the signed-in account. */
@Serializable
data class Account(
    val cookie: String,
    val visitorData: String,
    val dataSyncId: String,
    val authUser: String,
)

/**
 * The account on disk, encrypted for this user: with DPAPI on Windows, with a key in the login
 * keychain on a Mac. It is a live Google session.
 */
object AccountStore {
    private val file = File(AppFiles.root, "session.bin")

    fun load(): Account? =
        runCatching {
            val sealed = file.readBytes()
            val plain = if (Os.isMac) MacKeychain.open(sealed) else Crypt32Util.cryptUnprotectData(sealed)
            Json.decodeFromString<Account>(String(plain, Charsets.UTF_8))
        }.getOrNull()

    fun save(account: Account) {
        val plain = Json.encodeToString(account).toByteArray(Charsets.UTF_8)
        file.writeBytes(if (Os.isMac) MacKeychain.seal(plain) else Crypt32Util.cryptProtectData(plain))
    }

    fun clear() {
        file.delete()
        if (Os.isMac) MacKeychain.forget()
        EdgeSignIn.forgetBrowserProfile()
    }

    /** Points the YouTube client at [account], or at no account. */
    fun use(account: Account?) {
        YouTube.cookie = account?.cookie
        YouTube.dataSyncId = account?.dataSyncId
        YouTube.authUser = account?.authUser ?: "0"
        if (account != null) YouTube.visitorData = account.visitorData
    }
}
