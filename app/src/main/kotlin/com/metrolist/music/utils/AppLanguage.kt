/**
 * Sonara (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Sonara speaks English, whatever the phone's language: the owner's choice, with no setting to
 * change it. Android 13 and later keep the language per app; older versions get it on the
 * activity's resources (MainActivity, [setAppLocale]).
 */
object AppLanguage {
    const val TAG = "en"

    val locale: Locale = Locale.ENGLISH

    fun applyAtStart(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val manager = context.getSystemService(LocaleManager::class.java) ?: return
            if (manager.applicationLocales.toLanguageTags() != TAG) manager.applicationLocales = LocaleList.forLanguageTags(TAG)
        }
    }
}
