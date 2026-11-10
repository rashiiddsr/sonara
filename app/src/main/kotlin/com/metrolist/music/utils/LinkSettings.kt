/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

private const val YOUTUBE_MUSIC_PACKAGE = "com.google.android.apps.youtube.music"
private val SAMPLE_LINK: Uri = Uri.parse("https://music.youtube.com/watch?v=dQw4w9WgXcQ")

/**
 * Whether a music.youtube.com link opens in SONARA right now: asks Android which app it would
 * open such a link with (a chooser counts as no), the same on every Android version.
 */
fun opensYouTubeMusicLinks(context: Context): Boolean {
    val view = Intent(Intent.ACTION_VIEW, SAMPLE_LINK).addCategory(Intent.CATEGORY_BROWSABLE)
    val handler = runCatching {
        context.packageManager.resolveActivity(view, PackageManager.MATCH_DEFAULT_ONLY)
    }.getOrNull()
    return handler?.activityInfo?.packageName == context.packageName
}

/**
 * Android 12 and later: SONARA's "Open by default" page, where music.youtube.com is added.
 * Before: YouTube Music's app page, where its "Open supported links" is turned off (Android then
 * asks which app to use and offers SONARA). False when no settings page could be opened.
 */
fun openLinkSettings(context: Context): Boolean {
    val pages = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, Uri.parse("package:${context.packageName}")))
        } else {
            val installed = runCatching { context.packageManager.getPackageInfo(YOUTUBE_MUSIC_PACKAGE, 0) }.isSuccess
            if (installed) add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$YOUTUBE_MUSIC_PACKAGE")))
        }
        add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
    }
    return pages.any { page ->
        runCatching { context.startActivity(page.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }
}
