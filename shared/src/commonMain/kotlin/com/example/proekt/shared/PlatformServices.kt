package com.example.proekt.shared

// Each platform reads its own files and stores data using this interface.
interface LibraryStorage {
    suspend fun loadLibrary(): Library
    suspend fun saveLibrary(library: Library)
    suspend fun loadPlayback(): PlaybackSnapshot?
    suspend fun savePlayback(snapshot: PlaybackSnapshot)
}

interface AudioEngine {
    var onEnded: () -> Unit
    var onError: (String) -> Unit
    val positionMs: Long
    val durationMs: Long
    val isPlaying: Boolean
    val isLoading: Boolean
    val ready: Boolean
    fun open(track: Track, positionMs: Long)
    fun play()
    fun pause()
    fun seek(positionMs: Long)
    fun updateMetadata(track: Track)
}

// Import returns null when the user closes the file picker.
interface AudioImporter {
    fun pickFile(onResult: (Track?, String?) -> Unit)
}

interface PlaybackHost {
    fun requestPlay()
    fun nowMs(): Long
}

