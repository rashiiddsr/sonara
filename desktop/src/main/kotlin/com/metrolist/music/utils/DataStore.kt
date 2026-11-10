/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** The Android sources ask their preferences whether a provider is on; here all of them are. */
object NoPreferences

val Context.dataStore: NoPreferences get() = NoPreferences

operator fun NoPreferences.get(key: Any?): Boolean? = null

/** For work that must finish even if the user leaves the page. */
val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
