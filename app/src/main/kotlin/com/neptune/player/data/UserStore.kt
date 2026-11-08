package com.neptune.player.data

import android.content.Context
import com.neptune.player.model.Playlist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class UserData(
    val liked: Set<String> = emptySet(),
    val playlists: List<Playlist> = emptyList(),
    val recent: List<String> = emptyList(),
    val searches: List<String> = emptyList(),
) {
    fun toggleLike(id: String) = copy(liked = if (id in liked) liked - id else liked + id)
    fun rememberTrack(id: String) = copy(recent = (listOf(id) + recent.filterNot { it == id }).take(60))
    fun rememberSearch(query: String): UserData {
        val q = query.trim().take(100)
        return if (q.isBlank()) this else copy(searches = (listOf(q) + searches.filterNot { it.equals(q, true) }).take(8))
    }
    fun createPlaylist(name: String): Pair<UserData, Playlist> {
        val p = Playlist(UUID.randomUUID().toString(), name.trim().take(40).ifBlank { "Новый плейлист" })
        return copy(playlists = playlists + p) to p
    }
    fun addToPlaylist(id: String, trackId: String) = copy(playlists = playlists.map {
        if (it.id == id && trackId !in it.trackIds) it.copy(trackIds = it.trackIds + trackId) else it
    })
    fun removeFromPlaylist(id: String, trackId: String) = copy(playlists = playlists.map {
        if (it.id == id) it.copy(trackIds = it.trackIds - trackId) else it
    })
}

class UserStore(context: Context) {
    private val prefs = context.getSharedPreferences("neptune.v2.user", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(runCatching { decode(prefs.getString("data", "") ?: "") }.getOrDefault(UserData()))
    val state = _state
    private fun edit(block: (UserData) -> UserData) = _state.update {
        val next = block(it)
        prefs.edit().putString("data", encode(next)).apply()
        next
    }
    fun toggleLike(id: String) = edit { it.toggleLike(id) }
    fun rememberTrack(id: String) = edit { it.rememberTrack(id) }
    fun rememberSearch(q: String) = edit { it.rememberSearch(q) }
    fun clearSearches() = edit { it.copy(searches = emptyList()) }
    fun clearHistory() = edit { it.copy(recent = emptyList()) }
    fun createPlaylist(name: String): Playlist {
        var created: Playlist? = null
        edit { val (data, p) = it.createPlaylist(name); created = p; data }
        return created!!
    }
    fun renamePlaylist(id: String, name: String) = edit {
        it.copy(playlists = it.playlists.map { p -> if (p.id == id) p.copy(name = name.trim().take(40).ifBlank { p.name }) else p })
    }
    fun deletePlaylist(id: String) = edit { it.copy(playlists = it.playlists.filterNot { p -> p.id == id }) }
    fun addToPlaylist(id: String, trackId: String) = edit { it.addToPlaylist(id, trackId) }
    fun removeFromPlaylist(id: String, trackId: String) = edit { it.removeFromPlaylist(id, trackId) }

    companion object {
        fun encode(d: UserData): String = JSONObject().apply {
            put("liked", JSONArray(d.liked.toList())); put("recent", JSONArray(d.recent)); put("searches", JSONArray(d.searches))
            put("playlists", JSONArray().apply { d.playlists.forEach { p -> put(JSONObject().put("id", p.id).put("name", p.name).put("tracks", JSONArray(p.trackIds))) } })
        }.toString()
        fun decode(raw: String): UserData {
            if (raw.isBlank()) return UserData()
            val j = JSONObject(raw)
            fun strings(a: JSONArray?) = (0 until (a?.length() ?: 0)).map { a!!.getString(it) }
            val a = j.optJSONArray("playlists")
            val playlists = (0 until (a?.length() ?: 0)).mapNotNull {
                val p = a!!.optJSONObject(it) ?: return@mapNotNull null
                Playlist(p.getString("id"), p.getString("name"), strings(p.optJSONArray("tracks")).distinct())
            }
            return UserData(strings(j.optJSONArray("liked")).toSet(), playlists,
                strings(j.optJSONArray("recent")).distinct(), strings(j.optJSONArray("searches")))
        }
    }
}
