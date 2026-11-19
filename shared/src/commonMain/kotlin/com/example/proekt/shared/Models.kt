package com.example.proekt.shared

import kotlinx.serialization.Serializable

@Serializable
data class Track(
    val id: Int = 0,
    val title: String,
    val artist: String,
    val uri: String,
    val durationMs: Long = 0,
    val isFavorite: Boolean = false
)

@Serializable
data class Playlist(
    val id: Int,
    val name: String,
    val trackIds: List<Int> = emptyList(),
    val description: String = "",
    val createdAt: Long = 0
)

@Serializable
data class Library(
    val tracks: List<Track> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val nextTrackId: Int = 1,
    val nextPlaylistId: Int = 1
)

@Serializable
data class PlaybackSnapshot(
    val ids: List<Int> = emptyList(),
    val index: Int = 0,
    val positionMs: Long = 0,
    val repeatMode: Int = 0,
    val shuffle: Boolean = false
)

data class PlaybackState(
    val queue: List<Track> = emptyList(),
    val index: Int = -1,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val ready: Boolean = false,
    val repeatMode: Int = 0,
    val shuffle: Boolean = false,
    val sleepUntilMs: Long? = null,
    val sleepAtEnd: Boolean = false,
    val error: String? = null
) {
    val track: Track? get() = queue.getOrNull(index)
}

fun formatTime(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0) / 1000
    val minutes = totalSeconds / 60
    val seconds = (totalSeconds % 60).toString().padStart(2, '0')
    return "$minutes:$seconds"
}

fun findTracks(tracks: List<Track>, query: String): List<Track> {
    val search = query.trim()
    if (search.isEmpty()) return tracks
    return tracks.filter { track ->
        track.title.contains(search, ignoreCase = true) ||
            track.artist.contains(search, ignoreCase = true)
    }
}

