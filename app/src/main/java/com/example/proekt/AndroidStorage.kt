package com.example.proekt

import android.content.Context
import androidx.room.withTransaction
import com.example.proekt.database.MusicRoomDatabase
import com.example.proekt.database.crossrefs.PlaylistTrack
import com.example.proekt.database.tracks.Track as StoredTrack
import com.example.proekt.database.playlists.Playlist as StoredPlaylist
import com.example.proekt.shared.Library
import com.example.proekt.shared.LibraryStorage
import com.example.proekt.shared.PlaybackSnapshot
import com.example.proekt.shared.Playlist
import com.example.proekt.shared.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.json.JSONObject

class AndroidStorage(context: Context) : LibraryStorage {
    private val database = MusicRoomDatabase.getInstance(context)
    private val preferences = context.getSharedPreferences("playback", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun loadLibrary(): Library = withContext(Dispatchers.IO) {
        database.withTransaction {
            val tracks = database.trackDao().getAll().map { stored ->
                Track(stored.id, stored.title, stored.artist, stored.uri, stored.durationMs, stored.isFavorite)
            }
            val links = database.playlistTrackDao().getAll()
            val playlists = database.playlistDao().getAll().map { stored ->
                val trackIds = links.filter { it.playlistId == stored.id }.map { it.trackId }
                Playlist(stored.id, stored.name, trackIds, stored.description, stored.createdAt)
            }
            Library(tracks, playlists, nextId("tracks"), nextId("playlists"))
        }
    }

    private fun nextId(table: String): Int {
        val db = database.openHelper.readableDatabase
        db.query("SELECT seq FROM sqlite_sequence WHERE name = ?", arrayOf(table)).use { cursor ->
            if (cursor.moveToFirst()) return cursor.getInt(0) + 1
        }
        return 1
    }

    override suspend fun saveLibrary(library: Library) {
        // Сохраняем все связанные таблицы в одной транзакции.
        // При ошибке Room откатит изменения и оставит прежнюю библиотеку.
        database.withTransaction {
            val tracks = library.tracks.map { track ->
                StoredTrack(track.id, track.title, track.artist, track.uri, track.durationMs, track.isFavorite)
            }
            val playlists = library.playlists.map { playlist ->
                StoredPlaylist(playlist.id, playlist.name, playlist.description, playlist.createdAt)
            }
            val links = mutableListOf<PlaylistTrack>()
            for (playlist in library.playlists) {
                playlist.trackIds.forEachIndexed { position, trackId ->
                    links.add(PlaylistTrack(playlist.id, trackId, position))
                }
            }
            database.playlistTrackDao().deleteAll()
            database.trackDao().deleteAll()
            database.playlistDao().deleteAll()
            database.trackDao().insertAll(tracks)
            database.playlistDao().insertAll(playlists)
            database.playlistTrackDao().insertAll(links)
        }
    }

    override suspend fun loadPlayback(): PlaybackSnapshot? = withContext(Dispatchers.IO) {
        val saved = preferences.getString("snapshot", null)
        if (saved != null) return@withContext json.decodeFromString<PlaybackSnapshot>(saved)

        // Читаем очередь, сохранённую предыдущей Android-версией.
        val old = preferences.getString("queue", null) ?: return@withContext null
        val data = JSONObject(old)
        val ids = data.getJSONArray("ids")
        val trackIds = mutableListOf<Int>()
        for (index in 0 until ids.length()) trackIds.add(ids.getInt(index))
        PlaybackSnapshot(
            trackIds, data.optInt("index"), data.optLong("position"),
            data.optInt("repeat"), data.optBoolean("shuffle")
        )
    }

    override suspend fun savePlayback(snapshot: PlaybackSnapshot) = withContext(Dispatchers.IO) {
        val saved = preferences.edit().putString("snapshot", json.encodeToString(snapshot)).commit()
        check(saved) { "Could not save playback" }
    }
}

