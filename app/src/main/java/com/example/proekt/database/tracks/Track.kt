package com.example.proekt.database.tracks

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracks")
data class Track(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "trackId")
    val id: Int = 0,
    val title: String,
    val artist: String,
    val uri: String,
    val durationMs: Long = 0L,
    val isFavorite: Boolean = false
)