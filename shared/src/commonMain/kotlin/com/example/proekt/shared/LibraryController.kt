package com.example.proekt.shared

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LibraryController(
    private val storage: LibraryStorage,
    private val importer: AudioImporter,
    private val scope: CoroutineScope
) {
    private val changes = Mutex()
    private val _library = MutableStateFlow(Library())
    val library = _library.asStateFlow()
    private val _loading = MutableStateFlow(true)
    val loading = _loading.asStateFlow()
    private val _importing = MutableStateFlow(false)
    val importing = _importing.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private var loaded = false

    init { reload() }

    fun reload() {
        if (loaded) return
        scope.launch {
            _loading.value = true
            try {
                _library.value = storage.loadLibrary()
                loaded = true
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                _error.value = "Не удалось прочитать библиотеку. Попробуйте ещё раз."
            } finally {
                _loading.value = false
            }
        }
    }

    fun clearError() { _error.value = null }

    private fun change(update: (Library) -> Library) {
        if (!loaded) return
        scope.launch {
            changes.withLock {
                try {
                    val updated = update(_library.value)
                    storage.saveLibrary(updated)
                    _library.value = updated
                } catch (exception: Exception) {
                    if (exception is CancellationException) throw exception
                    _error.value = "Не удалось сохранить изменения. Проверьте свободное место."
                }
            }
        }
    }

    fun importTrack(replacementId: Int? = null) {
        if (_importing.value || !loaded) return
        _importing.value = true
        importer.pickFile { imported, error ->
            _importing.value = false
            if (error != null) _error.value = error
            if (imported != null) saveImportedTrack(imported, replacementId)
        }
    }

    private fun saveImportedTrack(imported: Track, replacementId: Int?) {
        change { current ->
            val existing = if (replacementId == null) {
                current.tracks.find { it.uri == imported.uri }
            } else {
                current.tracks.find { it.id == replacementId }
            }
            if (replacementId != null && existing == null) return@change current
            if (existing != null) {
                var title = existing.title
                var artist = existing.artist
                if (title.isBlank() || title == "Без названия") title = imported.title
                if (artist.isBlank() || artist == "Неизвестен" || artist == "Неизвестный исполнитель") {
                    artist = imported.artist
                }
                val updated = existing.copy(
                    title = title, artist = artist, uri = imported.uri, durationMs = imported.durationMs
                )
                current.copy(tracks = current.tracks.map { if (it.id == existing.id) updated else it })
            } else {
                val newTrack = imported.copy(id = current.nextTrackId)
                current.copy(tracks = current.tracks + newTrack, nextTrackId = current.nextTrackId + 1)
            }
        }
    }

    fun editTrack(id: Int, title: String, artist: String) {
        if (title.isBlank()) return
        var author = artist.trim()
        if (author.isEmpty()) author = "Неизвестный исполнитель"
        change { current ->
            current.copy(tracks = current.tracks.map { track ->
                if (track.id == id) track.copy(title = title.trim(), artist = author) else track
            })
        }
    }

    fun setFavorite(id: Int, favorite: Boolean) {
        change { current ->
            current.copy(tracks = current.tracks.map { track ->
                if (track.id == id) track.copy(isFavorite = favorite) else track
            })
        }
    }

    fun fillDuration(id: Int, duration: Long) {
        if (duration <= 0) return
        change { current ->
            current.copy(tracks = current.tracks.map { track ->
                if (track.id == id && track.durationMs <= 0) track.copy(durationMs = duration) else track
            })
        }
    }

    fun deleteTrack(id: Int) {
        change { current ->
            current.copy(
                tracks = current.tracks.filter { it.id != id },
                playlists = current.playlists.map { it.copy(trackIds = it.trackIds.filter { trackId -> trackId != id }) }
            )
        }
    }

    fun addPlaylist(name: String, createdAt: Long) {
        if (name.isBlank()) return
        change { current ->
            val playlist = Playlist(current.nextPlaylistId, name.trim(), createdAt = createdAt)
            current.copy(playlists = listOf(playlist) + current.playlists, nextPlaylistId = current.nextPlaylistId + 1)
        }
    }

    fun deletePlaylist(id: Int) {
        change { current -> current.copy(playlists = current.playlists.filter { it.id != id }) }
    }

    fun addToPlaylist(playlistId: Int, trackId: Int) {
        change { current ->
            current.copy(playlists = current.playlists.map { playlist ->
                if (playlist.id == playlistId && trackId !in playlist.trackIds) {
                    playlist.copy(trackIds = playlist.trackIds + trackId)
                } else playlist
            })
        }
    }

    fun removeFromPlaylist(playlistId: Int, trackId: Int) {
        change { current ->
            current.copy(playlists = current.playlists.map { playlist ->
                if (playlist.id == playlistId) playlist.copy(trackIds = playlist.trackIds.filter { it != trackId }) else playlist
            })
        }
    }
}

