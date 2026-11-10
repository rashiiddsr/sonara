/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package android.content

/** The little of Android's Context that the shared lyric sources touch. */
class Context {
    val packageName = "com.ermusic.desktop"
    val packageManager = PackageManager()
}

class PackageManager {
    fun getPackageInfo(name: String, flags: Int) = PackageInfo()
}

class PackageInfo {
    val versionName: String? = "desktop"
}
