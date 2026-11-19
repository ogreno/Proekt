package com.example.proekt.database.crossrefs

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PlaylistTrackDao {
    @Query("SELECT * FROM playlist_track ORDER BY playlistId, position, rowid")
    suspend fun getAll(): List<PlaylistTrack>

    @Insert
    suspend fun insertAll(links: List<PlaylistTrack>)

    @Query("DELETE FROM playlist_track")
    suspend fun deleteAll()
}

