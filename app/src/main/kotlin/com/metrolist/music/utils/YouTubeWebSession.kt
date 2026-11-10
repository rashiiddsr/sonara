/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import org.json.JSONArray
import timber.log.Timber
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.utils.parseCookieString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import kotlin.random.Random

/**
 * Raw calls to YouTube Music made exactly like music.youtube.com makes them: WEB_REMIX context,
 * the account cookie and a SAPISIDHASH authorization. Used where InnerTubeX has no API or its
 * behaviour cannot be checked (playlist visibility, history registration).
 */
object YouTubeWebSession {
    private const val TAG = "SonaraWeb"
    const val ORIGIN = "https://music.youtube.com"
    private const val CLIENT_VERSION = "1.20250310.01.00"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private val http by lazy { OkHttpClient() }

    /** A web client to talk as; history falls back through these when one won't play a song. */
    data class Client(
        val name: String,
        val version: String,
        val origin: String,
        val userAgent: String,
    )

    val WebRemix = Client("WEB_REMIX", CLIENT_VERSION, ORIGIN, USER_AGENT)
    private val Web = Client("WEB", "2.20250312.04.00", "https://www.youtube.com", USER_AGENT)
    private val Tv = Client("TVHTML5", "7.20250312.16.00", "https://www.youtube.com", "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version")

    private data class RemixConfig(val clientVersion: String, val signatureTimestamp: Int?, val fetchedAt: Long)

    @Volatile private var remixConfig: RemixConfig? = null

    /**
     * The client version and player signature timestamp music.youtube.com uses right now, read
     * from its page and player script (cached for a few hours). YouTube Music's player answers
     * "Video unavailable" to an outdated version / missing timestamp, like ytmusicapi's get_song.
     */
    private fun currentRemixConfig(): RemixConfig? {
        remixConfig?.takeIf { System.currentTimeMillis() - it.fetchedAt < CONFIG_TTL_MS }?.let { return it }
        val page = signed(Request.Builder().url("$ORIGIN/").get())?.build() ?: return null
        val html = runCatching { http.newCall(page).execute().use { it.body.string() } }.getOrNull() ?: return null
        val version = Regex("\"INNERTUBE_CLIENT_VERSION\":\"([0-9.]+)\"").find(html)?.groupValues?.get(1) ?: return null
        val jsPath = Regex("\"jsUrl\":\"([^\"]+)\"").find(html)?.groupValues?.get(1)
        val sts = jsPath?.let { path ->
            runCatching {
                val js = http.newCall(Request.Builder().url(if (path.startsWith("http")) path else "$ORIGIN$path").header("User-Agent", USER_AGENT).build())
                    .execute().use { it.body.string() }
                Regex("""(?:signatureTimestamp|sts)\s*[:=]\s*(\d{5})""").find(js)?.groupValues?.get(1)?.toInt()
            }.getOrNull()
        }
        Timber.tag(TAG).d("remix config: version=$version sts=$sts")
        return RemixConfig(version, sts, System.currentTimeMillis()).also { remixConfig = it }
    }

    private fun signed(builder: Request.Builder, client: Client = WebRemix): Request.Builder? {
        val cookie = YouTube.cookie ?: return null
        val sapisid = parseCookieString(cookie).let { it["SAPISID"] ?: it["__Secure-3PAPISID"] } ?: return null
        val now = System.currentTimeMillis() / 1000
        val hash = MessageDigest.getInstance("SHA-1")
            .digest("$now $sapisid ${client.origin}".toByteArray())
            .joinToString("") { "%02x".format(it) }
        return builder
            .header("Cookie", cookie)
            .header("Authorization", "SAPISIDHASH ${now}_$hash")
            .header("X-Goog-AuthUser", YouTube.authUser)
            .header("X-Origin", client.origin)
            .header("Origin", client.origin)
            .header("Referer", "${client.origin}/")
            .header("User-Agent", client.userAgent)
            .apply { YouTube.visitorData?.let { header("X-Goog-Visitor-Id", it) } }
    }

    /** POST to youtubei/v1/[endpoint]; null when signed out or on failure. */
    suspend fun post(
        endpoint: String,
        payload: JSONObject,
        client: Client = WebRemix,
    ): JSONObject? = postWithStatus(endpoint, payload, client).second

    /** Like [post], with the HTTP status (0 when signed out or the request failed). */
    suspend fun postWithStatus(
        endpoint: String,
        payload: JSONObject,
        client: Client = WebRemix,
    ): Pair<Int, JSONObject?> = withContext(Dispatchers.IO) {
        val user = JSONObject()
        YouTube.dataSyncId?.substringBefore("||")?.takeIf { it.isNotBlank() }?.let { user.put("onBehalfOfUser", it) }
        payload.put(
            "context",
            JSONObject()
                .put("client", JSONObject().put("clientName", client.name).put("clientVersion", client.version).put("hl", "en"))
                .put("user", user),
        )
        val request = signed(
            Request.Builder()
                .url("${client.origin}/youtubei/v1/$endpoint?prettyPrint=false")
                .post(payload.toString().toRequestBody("application/json".toMediaType())),
            client,
        )?.build()
        if (request == null) {
            Timber.tag(TAG).w("$endpoint: not signed in (no cookie or SAPISID)")
            return@withContext 0 to null
        }
        runCatching {
            http.newCall(request).execute().use { response ->
                val text = response.body.string()
                if (!response.isSuccessful) {
                    Timber.tag(TAG).w("$endpoint: HTTP ${response.code} ${text.take(300)}")
                    return@use response.code to null
                }
                response.code to JSONObject(text)
            }
        }.onFailure { Timber.tag(TAG).w(it, "$endpoint failed") }.getOrDefault(0 to null)
    }

    /**
     * Adds a play to the account's YouTube history, as music.youtube.com does when a song starts:
     * the player response for this account gives the playback-stats URLs, which are then pinged
     * with the account session. [fallbackTrackingUrl] (from the stream) is used if the web player
     * response has none.
     */
    suspend fun recordPlay(
        videoId: String,
        fallbackTrackingUrl: String? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        // YouTube Music's own client may answer UNPLAYABLE for a signed-in request without a
        // player token; the YouTube web and TV clients share the same account history.
        // YouTube Music history only lists plays reported by YouTube Music's client, so that one
        // goes first, with the PoToken music.youtube.com sends (without it the answer is
        // "Video unavailable"). Web and TV are fallbacks: they still land in YouTube history.
        val session = YouTube.dataSyncId?.substringBefore("||")?.takeIf { it.isNotBlank() } ?: YouTube.visitorData
        val poToken = session?.let { InnerTubeXPlayer.webPlayerPoToken(videoId, it) }
        Timber.tag(TAG).d("history $videoId: poToken=${poToken != null}")
        val config = currentRemixConfig()
        val remix = config?.let { WebRemix.copy(version = it.clientVersion) } ?: WebRemix
        for (client in listOf(remix, Web, Tv)) {
            val body = JSONObject().put("videoId", videoId).put("contentCheckOk", true).put("racyCheckOk", true)
            if (poToken != null) body.put("serviceIntegrityDimensions", JSONObject().put("poToken", poToken))
            if (client === remix && config?.signatureTimestamp != null) {
                body.put("playbackContext", JSONObject().put("contentPlaybackContext", JSONObject().put("signatureTimestamp", config.signatureTimestamp)))
            }
            val player = post("player", body, client)
            val status = player?.optJSONObject("playabilityStatus")
            val tracking = player?.optJSONObject("playbackTracking")
            Timber.tag(TAG).d(
                "history $videoId via ${client.name}: status=${status?.optString("status")} " +
                    "reason=${status?.optString("reason")?.take(80)} tracking=${tracking != null}",
            )
            val playback = tracking?.optJSONObject("videostatsPlaybackUrl")?.optString("baseUrl")?.takeIf { it.isNotBlank() } ?: continue
            val watchtime = tracking.optJSONObject("videostatsWatchtimeUrl")?.optString("baseUrl")?.takeIf { it.isNotBlank() }
            val cpn = newCpn()
            val ok = ping("$playback&ver=2&c=${client.name}&cpn=$cpn", "playback", client)
            watchtime?.let { ping("$it&ver=2&c=${client.name}&cpn=$cpn&cmt=10&st=0&et=10&state=playing", "watchtime", client) }
            if (ok) return@withContext true
        }
        fallbackTrackingUrl?.let { ping("$it&ver=2&c=WEB_REMIX&cpn=${newCpn()}", "stream-fallback", WebRemix) } ?: false
    }

    /**
     * Uploads a playlist cover the way music.youtube.com does: a resumable upload session, the
     * image bytes, then ACTION_SET_CUSTOM_THUMBNAIL with the returned blob id. Returns null on
     * failure; every step is logged.
     */
    suspend fun uploadPlaylistCover(
        playlistId: String,
        image: ByteArray,
    ): Boolean = withContext(Dispatchers.IO) {
        val version = currentRemixConfig()?.clientVersion ?: CLIENT_VERSION
        val candidates = listOf(
            "$ORIGIN/playlist_image_upload/playlist_custom_thumbnail",
            "$ORIGIN/playlist_image_upload/playlist_custom_thumbnail?authuser=${YouTube.authUser}",
            "https://www.youtube.com/playlist_image_upload/playlist_custom_thumbnail",
        )
        for (base in candidates) {
            val start = signed(
                Request.Builder().url(base)
                    .header("X-Goog-Upload-Command", "start")
                    .header("X-Goog-Upload-Protocol", "resumable")
                    .header("X-Goog-Upload-Header-Content-Length", image.size.toString())
                    .header("X-YouTube-Client-Name", "67")
                    .header("X-YouTube-Client-Version", version)
                    .post(ByteArray(0).toRequestBody()),
            )?.build() ?: return@withContext false
            val session = runCatching {
                http.newCall(start).execute().use { r ->
                    val url = r.header("X-Goog-Upload-URL")
                    val id = r.header("X-Goog-Upload-Id") ?: r.header("X-Guploader-Uploadid")
                    Timber.tag(TAG).d("cover start $base: HTTP ${r.code} url=${url != null} id=${id != null} ${if (!r.isSuccessful) r.body.string().take(200) else ""}")
                    if (!r.isSuccessful) null else url ?: id?.let { "$base${if ('?' in base) "&" else "?"}upload_id=$it&upload_protocol=resumable" }
                }
            }.onFailure { Timber.tag(TAG).w(it, "cover start failed") }.getOrNull() ?: continue

            val upload = signed(
                Request.Builder().url(session)
                    .header("X-Goog-Upload-Command", "upload, finalize")
                    .header("X-Goog-Upload-Offset", "0")
                    .post(image.toRequestBody("image/jpeg".toMediaType())),
            )?.build() ?: return@withContext false
            val blob = runCatching {
                http.newCall(upload).execute().use { r ->
                    val text = r.body.string()
                    Timber.tag(TAG).d("cover upload: HTTP ${r.code} ${text.take(200)}")
                    if (!r.isSuccessful) null else JSONObject(text).optString("encryptedBlobId").takeIf { it.isNotBlank() }
                }
            }.onFailure { Timber.tag(TAG).w(it, "cover upload failed") }.getOrNull() ?: return@withContext false

            // The apply format isn't documented; try the shapes seen in YouTube's own clients in order.
            val keyed = JSONObject()
                .put("imageKey", JSONObject().put("type", "PLAYLIST_IMAGE_TYPE_CUSTOM_THUMBNAIL").put("name", "studio_square_thumbnail"))
                .put("playlistScottyEncryptedBlobId", blob)
            val plain = JSONObject().put("playlistScottyEncryptedBlobId", blob)
            val bareId = playlistId.removePrefix("VL")
            for ((label, thumb, id) in listOf(
                Triple("keyed", keyed, bareId),
                Triple("plain", plain, bareId),
                Triple("keyed+VL", keyed, "VL$bareId"),
            )) {
                val action = JSONObject().put("action", "ACTION_SET_CUSTOM_THUMBNAIL").put("addedCustomThumbnail", thumb)
                val applied = post(
                    "browse/edit_playlist",
                    JSONObject().put("playlistId", id).put("actions", JSONArray().put(action)),
                    WebRemix.copy(version = version),
                )
                Timber.tag(TAG).d("cover apply ($label): status=${applied?.optString("status")}")
                if (applied?.optString("status") == "STATUS_SUCCEEDED") return@withContext true
            }
            return@withContext false
        }
        false
    }

    private fun newCpn() = (1..16).map { CPN_ALPHABET[Random.nextInt(CPN_ALPHABET.length)] }.joinToString("")

    private fun ping(url: String, label: String, client: Client): Boolean {
        val request = signed(Request.Builder().url(url).get(), client)?.build() ?: return false
        return runCatching {
            http.newCall(request).execute().use {
                Timber.tag(TAG).d("history $label ping: HTTP ${it.code}")
                it.isSuccessful
            }
        }.onFailure { Timber.tag(TAG).w(it, "history $label ping failed") }.getOrDefault(false)
    }

    private const val CONFIG_TTL_MS = 6 * 60 * 60 * 1000L
    private const val CPN_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"
}
