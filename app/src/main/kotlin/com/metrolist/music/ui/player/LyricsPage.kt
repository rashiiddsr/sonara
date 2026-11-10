/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.LyricsTranslateKey
import com.metrolist.music.constants.LyricsTranslateLanguageKey
import com.metrolist.music.db.entities.LyricsEntity
import com.metrolist.music.di.LyricsHelperEntryPoint
import com.metrolist.music.lyrics.LyricsTranslator
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.ui.component.EmptyState
import com.metrolist.music.ui.component.LoadingState
import com.metrolist.music.ui.component.Lyrics
import com.metrolist.music.ui.theme.Size
import com.metrolist.music.ui.theme.Spacing
import com.metrolist.music.utils.rememberPreference
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Synced lyrics page inside the player. Fetches lyrics for the current track if the database has
 * none yet; the lyrics component itself follows playback (word-level when the provider has it)
 * and [sliderPositionProvider] lets it preview a seek while the slider is dragged.
 */
@Composable
fun LyricsPage(
    mediaMetadata: MediaMetadata,
    sliderPositionProvider: () -> Long?,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val currentLyrics by playerConnection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)
    val lyrics = remember(currentLyrics) { currentLyrics?.lyrics?.trim() }
    val context = LocalContext.current
    val database = LocalDatabase.current

    LaunchedEffect(mediaMetadata.id, currentLyrics) {
        if (currentLyrics != null) return@LaunchedEffect
        delay(300)
        withContext(Dispatchers.IO) {
            runCatching {
                val helper =
                    EntryPointAccessors
                        .fromApplication(context.applicationContext, LyricsHelperEntryPoint::class.java)
                        .lyricsHelper()
                val fetched = helper.getLyrics(mediaMetadata)
                database.query { upsert(LyricsEntity(mediaMetadata.id, fetched.lyrics, fetched.provider)) }
            }
        }
    }

    // AI translator (Settings). The service translates lyrics as soon as it fetches them for a
    // song that starts, so they are usually here already; this covers lyrics that arrived
    // another way (fetched just above, picked in the lyrics menu). When the translator is off,
    // nothing is translated and nothing saved earlier is shown.
    val translate by rememberPreference(LyricsTranslateKey, defaultValue = false)
    val target by rememberPreference(LyricsTranslateLanguageKey, defaultValue = LyricsTranslator.DEFAULT_LANGUAGE)
    val entity = currentLyrics
    val stored = remember(entity, target) { LyricsTranslator.storedFor(entity, target) }
    val inProgress by LyricsTranslator.inProgress.collectAsStateWithLifecycle()
    val translating = translate && stored == null && LyricsTranslator.isTranslating(inProgress, mediaMetadata.id)
    LaunchedEffect(translate, target, entity?.id, entity?.lyrics, stored == null) {
        if (!translate || entity == null || stored != null) return@LaunchedEffect
        // If every model fails the lyrics just show without a translation; it is tried again
        // the next time the lyrics open.
        LyricsTranslator.ensureTranslated(context, database, entity.id)
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        when (lyrics) {
            null -> LoadingState(rows = 6)
            LyricsEntity.LYRICS_NOT_FOUND ->
                EmptyState(icon = R.drawable.lyrics, title = stringResource(R.string.lyrics_not_found))
            else -> {
                ProvideTextStyle(MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Start)) {
                    Lyrics(
                        sliderPositionProvider = sliderPositionProvider,
                        showLyrics = true,
                        translations = if (translate) stored.orEmpty() else emptyMap(),
                        modifier = Modifier.padding(horizontal = Spacing.XS),
                    )
                }
                if (translating) {
                    CircularProgressIndicator(
                        strokeWidth = Spacing.XXS,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.S).size(Size.IconSmall),
                    )
                }
            }
        }
    }
}
