package com.example.proekt.database.playlists

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC, id DESC")
    suspend fun getAll(): List<Playlist>

    @Insert
    suspend fun insertAll(playlists: List<Playlist>)

    @Query("DELETE FROM playlists")
    suspend fun deleteAll()
}

