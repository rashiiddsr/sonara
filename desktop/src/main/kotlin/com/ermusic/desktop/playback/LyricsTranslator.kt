/**
 * Sonara (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * The Android app's AI translator (app/.../lyrics/LyricsTranslator.kt): an OpenAI-compatible chat
 * endpoint, Gemini by default, with the owner's key built in from local.properties. The model gets
 * the whole song numbered and answers only for the lines it translated. When every model fails,
 * lyrics show without a translation. Results are kept on disk per song and language.
 */

package com.ermusic.desktop.playback

import com.ermusic.desktop.AppFiles
import com.ermusic.desktop.BuildSecrets
import kotlinx.coroutines.Dispatchers
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

object LyricsTranslator {
    private const val MODE = "Sonara-2"
    private const val TEMPERATURE = 0.3
    private const val TIMEOUT_SECONDS = 90L
    private const val PAIR = '\t'

    val languages =
        listOf(
            "en" to "English", "id" to "Bahasa Indonesia", "ms" to "Bahasa Melayu", "ja" to "日本語", "ko" to "한국어",
            "zh-CN" to "中文（简体）", "zh-TW" to "中文（繁體）", "th" to "ไทย", "vi" to "Tiếng Việt", "fil" to "Filipino",
            "hi" to "हिन्दी", "ar" to "العربية", "es" to "Español", "pt-BR" to "Português (Brasil)", "fr" to "Français",
            "de" to "Deutsch", "it" to "Italiano", "ru" to "Русский", "tr" to "Türkçe",
        )

    private val dir = File(AppFiles.root, "translations").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true }
    private val http =
        OkHttpClient.Builder().callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).build()
    private val vocals =
        Regex(
            "o+h*|a+h+|aa+|e+h+|u+h+|u+m+|m+|h+m+|(ha)+h?|l+a+|n+a+|d+a+|d+o+|b+a+|s+h+a+|w+h?o+a*h*|" +
                "ye+a*h*|ye+|y+o+|he+y+|a+y+|o+o+h*|la+la+|na+na+|doo+|shoo+|bop|uh+|ooh+|woo+|whoo+",
        )

    private fun isVocalsOnly(line: String): Boolean {
        val words = line.lowercase().split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }
        return words.isEmpty() || words.all { vocals.matches(it) }
    }

    private fun comparable(text: String) = text.lowercase().filter { it.isLetterOrDigit() }

    fun shouldShow(source: String, translation: String?): Boolean =
        !translation.isNullOrBlank() && !isVocalsOnly(source) && comparable(translation) != comparable(source)

    /** Source line to translation, or an empty map when that isn't possible right now. */
    suspend fun translate(trackId: String, lines: List<String>, target: String): Map<String, String> =
        withContext(Dispatchers.IO) {
            if (BuildSecrets.AI_KEY.isBlank()) return@withContext emptyMap()
            val file = File(dir, "$trackId.$target.$MODE.tsv")
            if (file.exists()) return@withContext decode(file.readText())
            val sources = lines.map { it.trim() }.filter { it.isNotEmpty() && !isVocalsOnly(it) }.distinct()
            val found = sources.takeIf { it.isNotEmpty() }?.let { batch -> firstAnswer(batch, target) } ?: return@withContext emptyMap()
            file.writeText(found.entries.joinToString("\n") { (source, text) -> "${source.replace('\t', ' ')}$PAIR${text.replace('\t', ' ')}" })
            found
        }

    private fun decode(stored: String): Map<String, String> =
        stored.lineSequence().mapNotNull { row ->
            val split = row.indexOf(PAIR)
            if (split <= 0) null else row.substring(0, split) to row.substring(split + 1)
        }.toMap()

    private fun firstAnswer(batch: List<String>, target: String): Map<String, String>? {
        val models = (listOf(BuildSecrets.AI_MODEL) + BuildSecrets.AI_FALLBACK_MODELS.split(',')).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        return models.firstNotNullOfOrNull { model -> runCatching { ask(batch, target, model) }.getOrNull() }
    }

    private fun ask(batch: List<String>, target: String, model: String): Map<String, String> {
        val language = Locale.forLanguageTag(target).getDisplayName(Locale.ENGLISH).ifBlank { target }
        val body =
            buildJsonObject {
                put("model", model)
                put("temperature", TEMPERATURE)
                putJsonArray("messages") {
                    addJsonObject {
                        put("role", "system")
                        put("content", instructions(language))
                    }
                    addJsonObject {
                        put("role", "user")
                        put("content", batch.withIndex().joinToString("\n") { (i, line) -> "${i + 1}\t$line" })
                    }
                }
            }
        val request =
            Request.Builder()
                .url(BuildSecrets.AI_BASE_URL.trimEnd('/') + "/chat/completions")
                .header("Authorization", "Bearer ${BuildSecrets.AI_KEY}")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
        val text =
            http.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "HTTP ${response.code}" }
                response.body.string()
            }
        val content =
            json.parseToJsonElement(text).jsonObject["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull.orEmpty()
        return checkNotNull(parse(content)).mapNotNull { (n, line) -> batch.getOrNull(n - 1)?.let { it to line } }.toMap()
    }

    private fun parse(content: String): Map<Int, String>? {
        val cleaned = content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val root = runCatching { json.parseToJsonElement(cleaned) }.getOrNull() as? JsonObject ?: return null
        val rows = root["lines"] as? JsonArray ?: return null
        return rows.mapNotNull { row ->
            val item = row as? JsonObject ?: return@mapNotNull null
            val n = (item["n"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: return@mapNotNull null
            val t = (item["t"] as? JsonPrimitive)?.contentOrNull?.replace('\n', ' ')?.trim()
            if (t.isNullOrEmpty()) null else n to t
        }.toMap()
    }

    private fun instructions(language: String) =
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
}
