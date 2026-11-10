/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.content.Context
import com.metrolist.music.constants.JoinedCollaborationsKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Playlists this account joined as a collaborator. YouTube's library lists them like playlists
 * you follow, so SONARA remembers them to offer "Add a song" there, as YouTube Music does.
 */
object Collaborations {
    fun joined(context: Context): Flow<Set<String>> =
        context.dataStore.data.map { prefs -> prefs[JoinedCollaborationsKey].orEmpty() }

    suspend fun isJoined(
        context: Context,
        playlistId: String,
    ): Boolean = playlistId.removePrefix("VL") in joined(context).first()

    suspend fun markJoined(
        context: Context,
        playlistId: String,
    ) {
        context.safeDataStoreEdit { prefs ->
            prefs[JoinedCollaborationsKey] = prefs[JoinedCollaborationsKey].orEmpty() + playlistId.removePrefix("VL")
        }
    }
}
