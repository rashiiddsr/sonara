/**
 * Sonara (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Experiment requested by the owner: AI translation of lyrics. The owner's key is built in from
 * local.properties, sealed so the app holds no readable copy (SONARA_AI_KEY, optional SONARA_AI_BASE_URL, SONARA_AI_MODEL and
 * SONARA_AI_FALLBACK_MODELS; users are never asked for one), for an OpenAI-compatible chat
 * endpoint, Gemini by default. When a model fails, the next one is tried; when all fail, or
 * there is no key, the lyrics simply show without a translation.
 * It is kept in this file and the lyrics page so the feature can be removed in one go.
 */

package com.metrolist.music.lyrics

import android.content.Context
import com.metrolist.music.BuildConfig
import com.metrolist.music.constants.LyricsTranslateKey
import com.metrolist.music.constants.LyricsTranslateLanguageKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.LyricsEntity
import com.metrolist.music.utils.dataStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import timber.log.Timber
import java.util.Locale

object LyricsTranslator {
    /** Stored in [com.metrolist.music.db.entities.LyricsEntity.translationMode]. */
    // Bumped when the prompt changes, so translations saved by an older prompt are redone.
    const val MODE = "Sonara-2"

    /** English unless the user picks another language in Settings. */
    const val DEFAULT_LANGUAGE = "en"

    /** Languages offered in Settings, each in its own name. */
    val languages =
        listOf(
            "en" to "English",
            "id" to "Bahasa Indonesia",
            "ms" to "Bahasa Melayu",
            "ja" to "日本語",
            "ko" to "한국어",
            "zh-CN" to "中文（简体）",
            "zh-TW" to "中文（繁體）",
            "th" to "ไทย",
            "vi" to "Tiếng Việt",
            "fil" to "Filipino",
            "hi" to "हिन्दी",
            "ar" to "العربية",
            "es" to "Español",
            "pt-BR" to "Português (Brasil)",
            "fr" to "Français",
            "de" to "Deutsch",
            "it" to "Italiano",
            "ru" to "Русский",
            "tr" to "Türkçe",
        )

    fun languageName(code: String): String = languages.firstOrNull { it.first == code }?.second ?: code

    private const val AI_BATCH_LINES = 150
    private const val PAIR = '\t'
    private const val AI_TEMPERATURE = 0.3

    // Opened from its sealed form the first time it is needed (SealedKey).
    private val aiKey by lazy { SealedKey.open(BuildConfig.AI_TRANSLATE_KEY_SEALED) }
    private val aiAvailable get() = aiKey.isNotBlank()

    private val json = Json { ignoreUnknownKeys = true }

    private val client by lazy {
        HttpClient(OkHttp) {
            expectSuccess = false
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                requestTimeoutMillis = 90_000
                socketTimeoutMillis = 90_000
            }
        }
    }

    /**
     * Translates the lines of one song into [target], keyed by the line itself. Lines that need
     * no translation (wholly in [target], or only "ooh"s) get none, so a song half in English,
     * half in Indonesian, translated into English, gets translations under its Indonesian lines;
     * a line with a few English words in it, translated into Indonesian, is translated whole.
     *
     * The model reads the whole song at once, numbered, and answers only for the lines it
     * translated, by number: it decides line by line, with the song as context, and a skipped
     * line cannot shift the others. Fails when there is no key or every model failed.
     */
    suspend fun translate(
        lines: List<String>,
        target: String,
    ): Result<Map<String, String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                check(aiAvailable) { "No AI key built in" }
                val sources = lines.map { it.trim() }.filter { it.isNotEmpty() && !isVocalsOnly(it) }.distinct()
                coroutineScope {
                    sources
                        .chunked(AI_BATCH_LINES)
                        .map { batch -> async { translateBatch(batch, target) } }
                        .awaitAll()
                        .fold(emptyMap<String, String>()) { all, part -> all + part }
                }
            }.onFailure { if (it is CancellationException) throw it }
        }

    /**
     * Whether a translation is worth showing under [source]: not when it says the same thing up to
     * case, spacing and punctuation (a model that translated a line already in the target language
     * anyway) (so a song half in English, half in Indonesian, translated
     * into English, shows translations under the Indonesian lines only), nor for vocals-only lines.
     */
    fun shouldShow(
        source: String,
        translation: String?,
    ): Boolean {
        if (translation.isNullOrBlank() || isVocalsOnly(source)) return false
        return comparable(translation) != comparable(source)
    }

    /** "Ooh, ooh", "la la la", "(yeah!)", "mmm", "na-na-na": sounds, not words; nothing to translate. */
    internal fun isVocalsOnly(line: String): Boolean {
        val words = line.lowercase().split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }
        return words.isEmpty() || words.all { VOCALS.matches(it) }
    }

    private fun comparable(text: String) = text.lowercase().filter { it.isLetterOrDigit() }

    private val VOCALS =
        Regex(
            // Real words stay out: "a", "he", "hi" are not here ("a" alone needs a second a or an h).
            "o+h*|a+h+|aa+|e+h+|u+h+|u+m+|m+|h+m+|(ha)+h?|l+a+|n+a+|d+a+|d+o+|b+a+|s+h+a+|w+h?o+a*h*|" +
                "ye+a*h*|ye+|y+o+|he+y+|a+y+|o+o+h*|la+la+|na+na+|doo+|shoo+|bop|uh+|ooh+|woo+|whoo+",
        )

    private val inProgressState = MutableStateFlow<Set<String>>(emptySet())

    /** `songId|language` pairs being translated right now, for the lyrics page's spinner. */
    val inProgress: StateFlow<Set<String>> = inProgressState.asStateFlow()

    fun isTranslating(
        songs: Set<String>,
        songId: String,
    ): Boolean = songs.any { it.startsWith("$songId|") }

    /** The saved translation of [entity] into [target], or null when there is none yet. */
    fun storedFor(
        entity: LyricsEntity?,
        target: String,
    ): Map<String, String>? =
        entity
            // An empty translation is a result too: the whole song was already in that language.
            ?.takeIf { it.translationLanguage == target && it.translationMode == MODE }
            ?.let { decode(it.translatedLyrics) }

    /**
     * When the AI translator is on, translates the saved lyrics of [songId] into the chosen
     * language and saves the result with them. Called as soon as lyrics are fetched for a song
     * that starts (and the next one in the queue), so the lyrics page opens already translated;
     * the page calls it too, for lyrics that arrived another way. Does nothing when the
     * translator is off, the song has no lyrics, the translation is already saved, or another
     * caller is translating the same song. Returns false only when translating failed.
     */
    suspend fun ensureTranslated(
        context: Context,
        database: MusicDatabase,
        songId: String,
    ): Boolean {
        val prefs = context.dataStore.data.first()
        if (prefs[LyricsTranslateKey] != true) return true
        val target = prefs[LyricsTranslateLanguageKey] ?: DEFAULT_LANGUAGE
        val entity = database.lyrics(songId).first() ?: return true
        val text = entity.lyrics.trim()
        if (text.isEmpty() || text == LyricsEntity.LYRICS_NOT_FOUND || storedFor(entity, target) != null) return true
        val key = "$songId|$target"
        if (!claim(key)) return true
        try {
            val sources = if (lyricsTextLooksSynced(text)) LyricsUtils.parseLyrics(text).map { it.text } else text.lines()
            val found = translate(sources, target).getOrElse { return false }
            withContext(Dispatchers.IO) {
                val current = database.lyrics(songId).first()
                // Lyrics edited or replaced meanwhile: this translation belongs to the old text.
                if (current != null && current.lyrics == entity.lyrics) {
                    database.upsert(current.copy(translatedLyrics = encode(found), translationLanguage = target, translationMode = MODE))
                }
            }
            return true
        } finally {
            inProgressState.update { it - key }
        }
    }

    private fun claim(key: String): Boolean {
        while (true) {
            val now = inProgressState.value
            if (key in now) return false
            if (inProgressState.compareAndSet(now, now + key)) return true
        }
    }

    /** Tries the models in turn (busy, retired, a malformed answer) and gives up after the last. */
    private suspend fun translateBatch(
        batch: List<String>,
        target: String,
    ): Map<String, String> {
        var lastError: Exception? = null
        for (model in models) {
            try {
                return aiBatch(batch, target, model)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Timber.w(e, "AI translation with %s failed", model)
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("No AI model configured")
    }

    private val models: List<String>
        get() =
            (listOf(BuildConfig.AI_TRANSLATE_MODEL) + BuildConfig.AI_TRANSLATE_FALLBACK_MODELS.split(','))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()

    // ---- AI (OpenAI-compatible chat completions) ----

    private suspend fun aiBatch(
        batch: List<String>,
        target: String,
        model: String,
    ): Map<String, String> {
        val language = Locale.forLanguageTag(target).getDisplayName(Locale.ENGLISH).ifBlank { target }
        val body =
            buildJsonObject {
                put("model", model)
                put("temperature", AI_TEMPERATURE)
                putJsonArray("messages") {
                    addJsonObject {
                        put("role", "system")
                        put("content", aiInstructions(language))
                    }
                    addJsonObject {
                        put("role", "user")
                        put("content", batch.withIndex().joinToString("\n") { (i, line) -> "${i + 1}\t$line" })
                    }
                }
            }
        val response =
            client.post(BuildConfig.AI_TRANSLATE_BASE_URL.trimEnd('/') + "/chat/completions") {
                header("Authorization", "Bearer $aiKey")
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
        val text = response.bodyAsText()
        check(response.status.isSuccess()) { "AI translation: HTTP ${response.status.value}" }
        val content =
            json.parseToJsonElement(text).jsonObject["choices"]
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("message")
                ?.jsonObject
                ?.get("content")
                ?.jsonPrimitive
                ?.contentOrNull
                .orEmpty()
        val numbered = checkNotNull(parseAiLines(content)) { "AI translation: no JSON in the answer" }
        return numbered.mapNotNull { (n, translated) -> batch.getOrNull(n - 1)?.let { it to translated } }.toMap()
    }

    private fun aiInstructions(language: String) =
        """
        You translate song lyrics into $language for a music player.
        You get the whole song, one numbered line per row, so you can read it in context.
        Songs often mix languages, even inside one line. For every line that has any words not in
        $language, translate the whole line into $language, including those words, so it reads as
        one natural $language line. Skip only lines written entirely in $language, and lines that
        are only sounds or ad-libs (ooh, la la, yeah). Names and nicknames stay as they are.
        Keep the meaning, feeling and imagery of a song lyric, not a word-for-word gloss.
        One translation per numbered line; never merge or split lines.
        Reply with JSON only: {"lines": [{"n": <line number>, "t": "<translation>"}]}
        listing only the lines you translated.
        """.trimIndent()

    /**
     * The model's answer, `{"lines": [{"n": 1, "t": "..."}]}`, with or without a ```json fence,
     * as line number to translation. Null when it is not that JSON (then the next model is
     * tried); an empty map is a real answer: the song needs no translation.
     */
    internal fun parseAiLines(content: String): Map<Int, String>? {
        val cleaned =
            content
                .trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
        val root = runCatching { json.parseToJsonElement(cleaned) }.getOrNull() as? JsonObject ?: return null
        val rows = root["lines"] as? JsonArray ?: return null
        return rows
            .mapNotNull { row ->
                val item = row as? JsonObject ?: return@mapNotNull null
                val n = (item["n"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: return@mapNotNull null
                val t = (item["t"] as? JsonPrimitive)?.contentOrNull?.replace('\n', ' ')?.trim()
                if (t.isNullOrEmpty()) null else n to t
            }.toMap()
    }

    // ---- Storage in the lyrics table ----

    /** One line per row, `source<TAB>translation`, for the lyrics table's translatedLyrics. */
    fun encode(translations: Map<String, String>): String =
        translations.entries.joinToString("\n") { (source, translated) -> "${source.flat()}$PAIR${translated.flat()}" }

    fun decode(stored: String): Map<String, String> =
        stored
            .lineSequence()
            .mapNotNull { row ->
                val split = row.indexOf(PAIR)
                if (split <= 0) null else row.substring(0, split) to row.substring(split + 1)
            }.toMap()

    private fun String.flat(): String = replace('\t', ' ').replace('\n', ' ').trim()
}
