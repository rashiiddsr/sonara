/**
 * SONARA (C) 2026, derived from Metrolist
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.async
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.CompletableDeferred
import org.json.JSONArray
import org.json.JSONObject

/** Playlist visibility as YouTube Music names it. */
enum class PlaylistPrivacy { PUBLIC, UNLISTED, PRIVATE }

data class PlaylistDetails(
    val description: String,
    val privacy: PlaylistPrivacy,
    /** YouTube offers "Remove custom image" only when the cover is a custom one. */
    val customCover: Boolean,
)

/**
 * The parts of YouTube Music's playlist editor that InnerTubeX does not cover (visibility, and
 * creating with a description), sent through [YouTubeWebSession] like music.youtube.com does.
 */
object YouTubePlaylistEditor {
    /**
     * Creates the playlist on the account; returns its id. With [sourcePlaylistId] YouTube fills it
     * with that playlist's songs itself, in order, in the same request (as ytmusicapi's
     * `source_playlist`), so a big import is not hundreds of single adds.
     */
    suspend fun create(
        title: String,
        description: String,
        privacy: PlaylistPrivacy,
        sourcePlaylistId: String? = null,
    ): String? {
        val body = JSONObject()
            .put("title", title)
            .put("description", description)
            .put("privacyStatus", privacy.name)
        sourcePlaylistId?.let { body.put("sourcePlaylistId", it.removePrefix("VL")) }
        val id = YouTubeWebSession.post("playlist/create", body)?.optString("playlistId")?.takeIf { it.isNotBlank() }
        if (sourcePlaylistId != null) timber.log.Timber.tag("SonaraWeb").d("import $sourcePlaylistId -> $id")
        return id
    }

    /** A playlist found from a link, shown so the user can check it is the right one. */
    data class ImportSource(
        val playlistId: String,
        val title: String,
        val owner: String?,
        val trackCount: Int?,
        val thumbnail: String?,
    )

    enum class ImportError { NOT_A_PLAYLIST, MIX, NOT_FOUND, EMPTY, TOO_BIG, OFFLINE }

    /** [source] when the link points to a playlist that can be copied, otherwise [error]. */
    data class ImportCheck(
        val source: ImportSource? = null,
        val error: ImportError? = null,
    )

    /**
     * The playlist id in a YouTube Music link (music.youtube.com/playlist?list=..., browse/VL...),
     * a youtube.com playlist link or a share link with &si=, or a bare id; null if there is none.
     */
    fun playlistIdFromLink(text: String): String? {
        val input = text.trim()
        Regex("""[?&]list=([A-Za-z0-9_-]+)""").find(input)?.let { return it.groupValues[1] }
        Regex("""/browse/VL([A-Za-z0-9_-]+)""").find(input)?.let { return it.groupValues[1] }
        return input.takeIf { Regex("""(PL|OLAK5uy_|RD|LM)[A-Za-z0-9_-]*""").matches(it) }
    }

    /** Reads the playlist behind [link] the way the account sees it, to show it before importing. */
    suspend fun checkImport(link: String): ImportCheck {
        val id = playlistIdFromLink(link) ?: return ImportCheck(error = ImportError.NOT_A_PLAYLIST)
        // Mixes (RD...) are made for each account as it listens and change all the time.
        if (id.startsWith("RD")) return ImportCheck(error = ImportError.MIX)
        val (code, page) = YouTubeWebSession.postWithStatus("browse", JSONObject().put("browseId", "VL$id"))
        timber.log.Timber.tag("SonaraWeb").d("import check $id: HTTP $code")
        if (code == 0) return ImportCheck(error = ImportError.OFFLINE)
        // A deleted playlist, and someone else's private one, both come back without a header.
        val header = page?.let { findObject(it, "musicResponsiveHeaderRenderer") }
            ?: return ImportCheck(error = ImportError.NOT_FOUND)
        val title = runsText(header.optJSONObject("title")).takeIf { it.isNotBlank() }
            ?: return ImportCheck(error = ImportError.NOT_FOUND)
        val second = runsText(header.optJSONObject("secondSubtitle"))
        val count = Regex("""([\d,.]+)\s+(track|song|episode)""", RegexOption.IGNORE_CASE).find(second)
            ?.groupValues?.get(1)?.replace(Regex("[,.]"), "")?.toIntOrNull()
        // The microformat reads "Playlist <bullet> Owner".
        val owner = runsText(header.optJSONObject("straplineTextOne")).takeIf { it.isNotBlank() }
            ?: avatarsOf(header.optJSONObject("facepile")).firstOrNull()?.name
            ?: page.optJSONObject("microformat")?.optJSONObject("microformatDataRenderer")
                ?.optString("description")?.substringAfter('\u2022', "")?.trim()?.takeIf { it.isNotBlank() }
        val thumbnail = header.optJSONObject("thumbnail")?.let { findObject(it, "thumbnail") }
            ?.optJSONArray("thumbnails")?.let { it.optJSONObject(it.length() - 1)?.optString("url") }
        val source = ImportSource(id, title, owner, count, thumbnail)
        return when {
            count == 0 -> ImportCheck(source, ImportError.EMPTY)
            count != null && count > MAX_PLAYLIST_SONGS -> ImportCheck(source, ImportError.TOO_BIG)
            else -> ImportCheck(source)
        }
    }

    private fun runsText(node: JSONObject?): String {
        val runs = node?.optJSONArray("runs") ?: return ""
        return (0 until runs.length()).joinToString("") { runs.optJSONObject(it)?.optString("text").orEmpty() }
    }

    /** Title, description and visibility in one edit, like the "Done" button on YouTube Music. */
    suspend fun edit(
        playlistId: String,
        title: String,
        description: String,
        privacy: PlaylistPrivacy,
    ): Boolean {
        val actions = JSONArray()
            .put(JSONObject().put("action", "ACTION_SET_PLAYLIST_NAME").put("playlistName", title))
            .put(JSONObject().put("action", "ACTION_SET_PLAYLIST_DESCRIPTION").put("playlistDescription", description))
            .put(JSONObject().put("action", "ACTION_SET_PLAYLIST_PRIVACY").put("playlistPrivacy", privacy.name))
        val body = JSONObject().put("playlistId", playlistId.removePrefix("VL")).put("actions", actions)
        return YouTubeWebSession.post("browse/edit_playlist", body)?.optString("status") == "STATUS_SUCCEEDED"
    }

    /** Drops the custom cover; YouTube goes back to its collage of the first songs. */
    suspend fun removeCover(playlistId: String): Boolean {
        val action = JSONObject()
            .put("action", "ACTION_REMOVE_CUSTOM_THUMBNAIL")
            .put(
                "deletedCustomThumbnail",
                JSONObject().put("type", "PLAYLIST_IMAGE_TYPE_CUSTOM_THUMBNAIL").put("name", "studio_square_thumbnail"),
            )
        val body = JSONObject().put("playlistId", playlistId.removePrefix("VL")).put("actions", JSONArray().put(action))
        val ok = YouTubeWebSession.post("browse/edit_playlist", body)?.optString("status") == "STATUS_SUCCEEDED"
        timber.log.Timber.tag("SonaraWeb").d("cover removed $playlistId: $ok")
        return ok
    }

    /** An invite link's playlist, with YouTube Music's own wording for the join dialog. */
    data class CollaborationInvite(
        val playlistId: String,
        val token: String,
        val title: String?,
        val message: String?,
    )

    /** Reads an invite (`list` + `jct` of the link) the way YouTube Music opens it; this does not join. */
    suspend fun invite(
        playlistId: String,
        token: String,
    ): CollaborationInvite {
        val body = JSONObject().put("browseId", "VL${playlistId.removePrefix("VL")}").put("params", inviteParams(token))
        val dialog = YouTubeWebSession.post("browse", body)?.let { findObject(it, "dialogViewModel") }
        val title = dialog?.optJSONObject("header")?.optJSONObject("dialogHeaderViewModel")
            ?.optJSONObject("headline")?.optString("content")?.takeIf { it.isNotBlank() }
        val message = findObject(dialog, "collaborativePlaylistInviteMessageViewModel")
            ?.optJSONObject("inviteMessage")?.optString("content")?.takeIf { it.isNotBlank() }
        timber.log.Timber.tag("SonaraWeb").d("invite $playlistId: dialog=${dialog != null}")
        return CollaborationInvite(playlistId.removePrefix("VL"), token, title, message)
    }

    enum class JoinResult { JOINED, ALREADY_JOINED, FAILED }

    /** "Join" in the invite dialog: the account becomes a collaborator and can add songs. */
    suspend fun joinCollaboration(invite: CollaborationInvite): JoinResult {
        val action = JSONObject().put("action", "ACTION_JOIN_COLLABORATION").put("joinCollaborationToken", invite.token)
        val body = JSONObject().put("playlistId", invite.playlistId).put("actions", JSONArray().put(action))
        val (code, answer) = YouTubeWebSession.postWithStatus("browse/edit_playlist", body)
        timber.log.Timber.tag("SonaraWeb").d("join ${invite.playlistId}: HTTP $code ${answer?.optString("status")}")
        return when {
            answer?.optString("status") == "STATUS_SUCCEEDED" -> JoinResult.JOINED
            // YouTube answers 401 to a second join by someone who is already in.
            code == 401 -> JoinResult.ALREADY_JOINED
            else -> JoinResult.FAILED
        }
    }

    /** Browse params for an invite: the token in field 39 and the fixed field 88 YouTube Music adds. */
    private fun inviteParams(token: String): String {
        val bytes = token.toByteArray()
        val proto = byteArrayOf(0xBA.toByte(), 0x02, bytes.size.toByte()) + bytes +
            byteArrayOf(0xC2.toByte(), 0x05, 0x03, 0x08, 0xD8.toByte(), 0x04)
        return android.util.Base64.encodeToString(proto, android.util.Base64.NO_WRAP)
    }

    /** The switches of YouTube Music's Collaborate panel. */
    enum class CollaborationChange { ENABLE, DISABLE, ALLOW_NEW, BLOCK_NEW }

    /**
     * A playlist's collaboration as YouTube Music's Collaborate panel shows it, with the commands
     * that panel sends for each switch ([commands], an `edit_playlist` body each).
     */
    data class Collaboration(
        val enabled: Boolean,
        val allowNew: Boolean,
        val inviteLink: String?,
        val commands: Map<CollaborationChange, JSONObject>,
    )

    private class CachedCollaboration(
        val load: Deferred<Collaboration?>,
        val at: Long,
    )

    private val collaborations = ConcurrentHashMap<String, CachedCollaboration>()

    /**
     * The Collaborate panel. YouTube takes seconds to answer it for a big playlist (measured
     * 3.4-5.9 s for 43 songs, under 1 s for an empty one), so the playlist page, the editor and the
     * panel share one request and its answer for a few minutes; [fresh] asks YouTube again.
     */
    suspend fun collaboration(
        playlistId: String,
        fresh: Boolean = false,
    ): Collaboration? {
        val bare = playlistId.removePrefix("VL")
        val now = System.currentTimeMillis()
        val cached = collaborations[bare]?.takeIf { !fresh && now - it.at < COLLABORATION_CACHE_MS }
        val load = cached?.load ?: appScope.async {
            readCollaboration(bare).also { if (it == null) collaborations.remove(bare) }
        }.also { collaborations[bare] = CachedCollaboration(it, now) }
        return load.await()
    }

    private suspend fun readCollaboration(bare: String): Collaboration? {
        val body = JSONObject().put("panelId", "PAplaylist_collaborate").put("params", panelParams(bare))
        val started = System.currentTimeMillis()
        val panel = YouTubeWebSession.post("get_panel", body)?.let { findObject(it, "playlistCollaborationViewModel") }
        timber.log.Timber.tag("SonaraWeb").d("collaborate panel $bare: ${System.currentTimeMillis() - started} ms")
        if (panel == null) {
            timber.log.Timber.tag("SonaraWeb").w("collaborate panel $bare: none")
            return null
        }
        val initial = panel.optJSONObject("playlistCollaborationFormSchema")?.optJSONObject("initialValues")
        fun switch(name: String) = panel.optJSONObject(name)?.optJSONObject("switchListItemViewModel")
        fun endpoint(node: JSONObject?, key: String) = findObject(node?.optJSONObject(key), "playlistEditEndpoint")
        val collaborate = switch("collaboratePlaylistCollaborationSetting")
        val allowNew = switch("allowNewCollaboratorsPlaylistCollaborationSetting")
        // What the panel sends; YouTube Music's own actions if a switch comes without one.
        fun edit(action: JSONObject) =
            JSONObject().put("playlistId", bare).put("actions", JSONArray().put(action))
        val invite = edit(JSONObject().put("action", "ACTION_CREATE_COLLABORATION_INVITE_LINK"))
        val commands = mapOf(
            CollaborationChange.ENABLE to (endpoint(collaborate, "switchOnCommand") ?: invite),
            CollaborationChange.DISABLE to (
                endpoint(collaborate, "switchOffCommand")
                    ?: edit(JSONObject().put("action", "ACTION_SET_CLOSED_TO_CONTRIBUTIONS").put("closedToContributions", true))
                ),
            CollaborationChange.ALLOW_NEW to (endpoint(allowNew, "switchOnCommand") ?: invite),
            CollaborationChange.BLOCK_NEW to (
                endpoint(allowNew, "switchOffCommand")
                    ?: edit(JSONObject().put("action", "ACTION_REVOKE_COLLABORATION_TOKENS"))
                ),
        )
        val link = findObject(panel, "copyLinkCommand")?.optString("shortUrl")?.takeIf { it.isNotBlank() }
        return Collaboration(
            enabled = initial?.optBoolean("isCollaborationEnabled") ?: collaborate?.optBoolean("switchedOnByDefault") ?: false,
            allowNew = initial?.optBoolean("isAllowNewCollaboratorsEnabled") ?: allowNew?.optBoolean("switchedOnByDefault") ?: false,
            inviteLink = link,
            commands = commands,
        )
    }

    /**
     * Sends one switch of the panel; returns the state YouTube accepted, or null when it refused.
     * A new invite link comes back in the answer when YouTube includes it; otherwise it is null
     * and the panel is read again for it.
     */
    suspend fun changeCollaboration(
        current: Collaboration,
        change: CollaborationChange,
    ): Collaboration? {
        val body = JSONObject(current.commands.getValue(change).toString())
        val started = System.currentTimeMillis()
        val answer = YouTubeWebSession.post("browse/edit_playlist", body)
        val status = answer?.optString("status")
        val link = findString(answer) { it.startsWith("https://") && "jct=" in it }
        timber.log.Timber.tag("SonaraWeb").d(
            "collaborate $change: $status link=${link != null} (${System.currentTimeMillis() - started} ms)",
        )
        if (status != "STATUS_SUCCEEDED") return null
        val accepted = when (change) {
            CollaborationChange.ENABLE -> current.copy(enabled = true, allowNew = true, inviteLink = link)
            CollaborationChange.DISABLE -> current.copy(enabled = false, allowNew = false, inviteLink = null)
            CollaborationChange.ALLOW_NEW -> current.copy(allowNew = true, inviteLink = link)
            CollaborationChange.BLOCK_NEW -> current.copy(allowNew = false, inviteLink = null)
        }
        body.optString("playlistId").takeIf { it.isNotBlank() }?.let { id ->
            collaborations[id.removePrefix("VL")] = CachedCollaboration(CompletableDeferred(accepted), System.currentTimeMillis())
        }
        return accepted
    }

    private fun findString(
        node: Any?,
        accept: (String) -> Boolean,
    ): String? {
        when (node) {
            is String -> return node.takeIf(accept)
            is JSONObject -> {
                val names = node.keys()
                while (names.hasNext()) findString(node.opt(names.next()), accept)?.let { return it }
            }
            is JSONArray -> for (i in 0 until node.length()) findString(node.opt(i), accept)?.let { return it }
        }
        return null
    }

    /** get_panel params for a playlist's Collaborate panel: field 138 { 1: playlistId }. */
    private fun panelParams(playlistId: String): String {
        val id = playlistId.toByteArray()
        val proto = byteArrayOf(0xD2.toByte(), 0x08, (id.size + 2).toByte(), 0x0A, id.size.toByte()) + id
        return android.util.Base64.encodeToString(proto, android.util.Base64.NO_WRAP)
    }

    /** Saves (follows) or removes a playlist from the account's library. */
    suspend fun like(
        playlistId: String,
        like: Boolean,
    ): Boolean {
        val body = JSONObject().put("target", JSONObject().put("playlistId", playlistId.removePrefix("VL")))
        val ok = YouTubeWebSession.post(if (like) "like/like" else "like/removelike", body) != null
        timber.log.Timber.tag("SonaraWeb").d("playlist ${if (like) "follow" else "unfollow"} $playlistId: $ok")
        return ok
    }

    /** Adds one song, as YouTube Music's "Save to playlist" does. */
    suspend fun addSong(
        playlistId: String,
        videoId: String,
    ): Boolean {
        val actions = JSONArray().put(JSONObject().put("action", "ACTION_ADD_VIDEO").put("addedVideoId", videoId))
        val body = JSONObject().put("playlistId", playlistId.removePrefix("VL")).put("actions", actions)
        return YouTubeWebSession.post("browse/edit_playlist", body)?.optString("status") == "STATUS_SUCCEEDED"
    }

    /** Current description and visibility, read from the playlist's edit header. */
    private class CachedDetails(
        val details: PlaylistDetails,
        val at: Long,
    )

    private val detailsCache = ConcurrentHashMap<String, CachedDetails>()
    private val pageLoads = ConcurrentHashMap<String, Deferred<JSONObject?>>()

    /**
     * The playlist page (VL...), which carries both the edit header and the songs. A big playlist
     * takes seconds to come back; [details] and [people] asking at the same time share one request.
     */
    private suspend fun firstPage(bare: String): JSONObject? =
        pageLoads.computeIfAbsent(bare) {
            appScope.async { YouTubeWebSession.post("browse", JSONObject().put("browseId", "VL$bare")) }
                .also { load -> load.invokeOnCompletion { pageLoads.remove(bare, load) } }
        }.await()

    /** The last details read or saved for this playlist, if recent: the editor opens with them at once. */
    fun cachedDetails(playlistId: String): PlaylistDetails? =
        detailsCache[playlistId.removePrefix("VL")]
            ?.takeIf { System.currentTimeMillis() - it.at < DETAILS_CACHE_MS }
            ?.details

    /** What YouTube has now, after an edit it accepted. */
    fun rememberDetails(
        playlistId: String,
        details: PlaylistDetails,
    ) {
        detailsCache[playlistId.removePrefix("VL")] = CachedDetails(details, System.currentTimeMillis())
    }

    suspend fun details(playlistId: String): PlaylistDetails? {
        val bare = playlistId.removePrefix("VL")
        val json = firstPage(bare) ?: return null
        val header = findObject(json, "musicPlaylistEditHeaderRenderer") ?: return null
        val privacy = runCatching { PlaylistPrivacy.valueOf(header.optString("privacy")) }.getOrDefault(PlaylistPrivacy.PRIVATE)
        val description = header.optJSONObject("description")?.optJSONArray("runs")
            ?.let { runs -> (0 until runs.length()).joinToString("") { runs.getJSONObject(it).optString("text") } }
            .orEmpty()
        val customCover = findObject(findObject(json, "thumbnailEditButton"), "deletedCustomThumbnail") != null
        return PlaylistDetails(description, privacy, customCover).also { rememberDetails(bare, it) }
    }

    /** Who added a song to a collaborative playlist: YouTube Music's small profile picture on the row. */
    data class Contributor(
        val name: String?,
        val avatarUrl: String,
    )

    /** A playlist as YouTube shows it to this account: whether it may add songs, and who added what. */
    data class PlaylistPeople(
        val canAddSongs: Boolean,
        /** By video id; only filled for collaborative playlists. */
        val addedBy: Map<String, Contributor>,
        /**
         * Video ids this account may remove: all of them for the owner, only its own additions for
         * a collaborator (YouTube offers "Remove from playlist" on exactly those).
         */
        val removable: Set<String>,
    )

    /**
     * Reads the playlist page (all of it) for collaboration: the owner and joined collaborators may
     * add songs, which YouTube shows as an editable header, a reorderable list, "Remove from
     * playlist" on songs, or the account's own picture among the collaborators.
     */
    suspend fun people(
        playlistId: String,
        accountImageUrl: String?,
    ): PlaylistPeople? {
        val bare = playlistId.removePrefix("VL")
        val first = firstPage(bare) ?: return null
        val shelf = findObject(first, "musicPlaylistShelfRenderer")
        val items = mutableListOf<JSONObject>()
        var token = collectItems(shelf?.optJSONArray("contents"), items)
        var pages = 0
        while (token != null && pages < MAX_CONTINUATIONS) {
            val next = YouTubeWebSession.post("browse", JSONObject().put("continuation", token)) ?: break
            val more = findArray(next, "continuationItems")
                ?: findObject(next, "musicPlaylistShelfContinuation")?.optJSONArray("contents")
            token = collectItems(more, items)
            pages++
        }

        val addedBy = HashMap<String, Contributor>()
        for (item in items) {
            val videoId = item.optJSONObject("playlistItemData")?.optString("videoId")?.takeIf { it.isNotBlank() } ?: continue
            val avatar = avatarsOf(item.optJSONObject("contributorsAvatars")).firstOrNull() ?: continue
            addedBy[videoId] = avatar
        }
        val faces = avatarsOf(findObject(first, "facepile")) + addedBy.values
        val owner = findObject(first, "musicEditablePlaylistDetailHeaderRenderer") != null
        val reorderable = shelf?.optBoolean("contentsReorderable") == true
        val removableIds = items.filter { findObject(it.optJSONObject("menu"), "playlistEditEndpoint") != null }
            .mapNotNull { it.optJSONObject("playlistItemData")?.optString("videoId")?.takeIf(String::isNotBlank) }
            .toSet()
        val removable = removableIds.isNotEmpty()
        val me = accountImageUrl?.let(::imageKey)?.let { key -> faces.any { imageKey(it.avatarUrl) == key } } == true
        timber.log.Timber.tag("SonaraWeb").d(
            "people $bare: owner=$owner reorderable=$reorderable removable=$removable me=$me " +
                "faces=${faces.size} added=${addedBy.size} items=${items.size} pages=$pages",
        )
        return PlaylistPeople(owner || reorderable || removable || me, addedBy, removableIds)
    }

    /** Adds the songs of one page; returns the token for the next page, if any. */
    private fun collectItems(
        contents: JSONArray?,
        into: MutableList<JSONObject>,
    ): String? {
        var token: String? = null
        for (i in 0 until (contents?.length() ?: 0)) {
            val entry = contents?.optJSONObject(i) ?: continue
            entry.optJSONObject("musicResponsiveListItemRenderer")?.let(into::add)
            entry.optJSONObject("continuationItemRenderer")?.let { token = findObject(it, "continuationCommand")?.optString("token") }
        }
        return token?.takeIf { it.isNotBlank() }
    }

    private fun avatarsOf(node: JSONObject?): List<Contributor> {
        val avatars = findObject(node, "avatarStackViewModel")?.optJSONArray("avatars") ?: return emptyList()
        return (0 until avatars.length()).mapNotNull { i ->
            val avatar = avatars.optJSONObject(i)?.optJSONObject("avatarViewModel") ?: return@mapNotNull null
            val url = avatar.optJSONObject("image")?.optJSONArray("sources")?.optJSONObject(0)?.optString("url")
                ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Contributor(avatar.optString("accessibilityText").takeIf { it.isNotBlank() }, url)
        }
    }

    /** The same picture comes in several sizes (`...=s48-c-k...`); the part before `=` names it. */
    private fun imageKey(url: String) = url.substringBefore('=').substringAfter("://").substringAfter('/')

    private const val MAX_CONTINUATIONS = 20

    /** YouTube's own limit for one playlist. */
    const val MAX_PLAYLIST_SONGS = 5000
    private const val COLLABORATION_CACHE_MS = 5 * 60 * 1000L
    private const val DETAILS_CACHE_MS = 5 * 60 * 1000L

    private fun findArray(node: Any?, key: String): JSONArray? {
        when (node) {
            is JSONObject -> {
                node.optJSONArray(key)?.let { return it }
                val names = node.keys()
                while (names.hasNext()) findArray(node.opt(names.next()), key)?.let { return it }
            }
            is JSONArray -> for (i in 0 until node.length()) findArray(node.opt(i), key)?.let { return it }
        }
        return null
    }

    private fun findObject(node: Any?, key: String): JSONObject? {
        when (node) {
            is JSONObject -> {
                node.optJSONObject(key)?.let { return it }
                val names = node.keys()
                while (names.hasNext()) findObject(node.opt(names.next()), key)?.let { return it }
            }
            is JSONArray -> for (i in 0 until node.length()) findObject(node.opt(i), key)?.let { return it }
        }
        return null
    }
}
