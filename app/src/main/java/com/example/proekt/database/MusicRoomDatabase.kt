package com.example.proekt.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.proekt.database.crossrefs.PlaylistTrack
import com.example.proekt.database.crossrefs.PlaylistTrackDao
import com.example.proekt.database.playlists.Playlist
import com.example.proekt.database.playlists.PlaylistDao
import com.example.proekt.database.tracks.Track
import com.example.proekt.database.tracks.TrackDao

@Database(
    entities = [
        Track::class,
        Playlist::class,
        PlaylistTrack::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MusicRoomDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playlistTrackDao(): PlaylistTrackDao

    companion object {
        @Volatile
        private var INSTANCE: MusicRoomDatabase? = null

        fun getInstance(context: Context): MusicRoomDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MusicRoomDatabase::class.java,
                    "music_database"
                ).build().also { INSTANCE = it }
            }
        }
    }
}