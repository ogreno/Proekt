package com.example.proekt.shared

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

class PlayerController(
    private val engine: AudioEngine,
    private val storage: LibraryStorage,
    private val host: PlaybackHost,
    private val library: LibraryController,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(PlaybackState())
    val state = _state.asStateFlow()
    private var queue = emptyList<Track>()
    private var index = -1
    private var repeatMode = 0
    private var shuffle = false
    private var sleepUntil: Long? = null
    private var sleepAtEnd = false
    private var timer: Job? = null
    private var error: String? = null
    private var restoring = true
    private var userChangedQueue = false
    private var opening = false
    private val saveLock = Mutex()
    private var lastSaved: PlaybackSnapshot? = null

    init {
        engine.onEnded = ::trackEnded
        engine.onError = { message ->
            error = message
            publish()
        }
        scope.launch {
            library.loading.collect { loading ->
                if (!loading && library.error.value == null && restoring) {
                    restore()
                }
            }
        }
        scope.launch {
            library.library.collect { current ->
                if (!restoring && queue.isNotEmpty()) reconcile(current.tracks)
            }
        }
        scope.launch {
            var ticks = 0
            while (true) {
                delay(250)
                publish()
                ticks++
                if (ticks % 4 == 0 && engine.isPlaying) save()
            }
        }
    }

    private suspend fun restore() {
        try {
            val saved = storage.loadPlayback()
            if (saved != null && !userChangedQueue) {
                val tracks = library.library.value.tracks.associateBy { it.id }
                val available = saved.ids.mapIndexedNotNull { oldIndex, id ->
                    val track = tracks[id]
                    if (track == null) null else oldIndex to track
                }
                if (available.isNotEmpty()) {
                    var restoredIndex = available.indexOfFirst { it.first >= saved.index }
                    if (restoredIndex < 0) restoredIndex = available.lastIndex
                    queue = available.map { it.second }
                    index = restoredIndex
                    repeatMode = saved.repeatMode.coerceIn(0, 2)
                    shuffle = saved.shuffle
                    var position = 0L
                    if (available[index].first == saved.index) position = saved.positionMs.coerceAtLeast(0)
                    openCurrent(position)
                }
            }
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            error = "Не удалось восстановить очередь. Выберите трек."
        } finally {
            restoring = false
            publish()
        }
    }

    private fun reconcile(tracks: List<Track>) {
        val currentId = queue.getOrNull(index)?.id
        val oldTrack = queue.getOrNull(index)
        val wasPlaying = engine.isPlaying
        val position = engine.positionMs
        val byId = tracks.associateBy { it.id }
        val updated = queue.mapNotNull { byId[it.id] }
        if (updated == queue) return
        queue = updated
        var updatedIndex = queue.indexOfFirst { it.id == currentId }
        if (updatedIndex < 0) updatedIndex = index.coerceAtMost(queue.lastIndex)
        index = updatedIndex
        val currentTrack = queue.getOrNull(index)
        if (currentTrack?.id != currentId || currentTrack?.uri != oldTrack?.uri) {
            openCurrent(if (currentTrack?.id == currentId) position else 0)
            if (wasPlaying && currentTrack != null) host.requestPlay()
        } else if (currentTrack != null) {
            engine.updateMetadata(currentTrack)
        }
        publish()
        save()
    }

    private fun openCurrent(position: Long = 0) {
        val track = queue.getOrNull(index)
        engine.pause()
        error = null
        if (track == null) {
            publish()
            return
        }
        opening = true
        engine.open(track, position)
        opening = false
        publish()
    }

    fun playSelection(tracks: List<Track>, trackId: Int) {
        val selected = tracks.indexOfFirst { it.id == trackId }
        if (selected < 0) return
        userChangedQueue = true
        cancelEndTimer()
        queue = tracks.toList()
        index = selected
        openCurrent()
        host.requestPlay()
        save()
    }

    fun playFromHost() {
        if (queue.getOrNull(index) == null) return
        if (error != null) openCurrent(engine.positionMs)
        error = null
        engine.play()
        publish()
    }

    fun togglePlay() {
        if (engine.isPlaying) pause() else host.requestPlay()
    }

    fun pause() {
        engine.pause()
        publish()
        save()
    }

    fun seek(positionMs: Long) {
        engine.seek(positionMs.coerceIn(0, state.value.durationMs.coerceAtLeast(0)))
        publish()
        save()
    }

    fun previous() {
        if (index <= 0) return
        cancelEndTimer()
        jumpTo(index - 1)
    }

    fun next() {
        if (queue.isEmpty()) return
        val nextIndex = chooseNext()
        if (nextIndex == null) return
        cancelEndTimer()
        jumpTo(nextIndex)
    }

    fun jumpTo(newIndex: Int) {
        if (newIndex !in queue.indices) return
        userChangedQueue = true
        cancelEndTimer()
        index = newIndex
        openCurrent()
        host.requestPlay()
        save()
    }

    private fun chooseNext(): Int? {
        if (shuffle && queue.size > 1) {
            val offset = Random.nextInt(1, queue.size)
            return (index + offset) % queue.size
        }
        if (index < queue.lastIndex) return index + 1
        if (repeatMode == 2) return 0
        return null
    }

    private fun trackEnded() {
        if (opening || queue.isEmpty() || !engine.isPlaying) return
        if (sleepAtEnd) {
            setSleepTimer()
            engine.pause()
            engine.seek(0)
            publish()
            save()
            return
        }
        if (repeatMode == 1) {
            openCurrent()
            engine.play()
        } else {
            val next = chooseNext()
            if (next == null) {
                engine.pause()
                engine.seek(0)
            } else {
                index = next
                openCurrent()
                engine.play()
            }
        }
        publish()
        save()
    }

    fun enqueue(track: Track, next: Boolean) {
        userChangedQueue = true
        val insertion = if (next && index >= 0) index + 1 else queue.size
        queue = queue.toMutableList().apply { add(insertion, track) }
        if (next) {
            shuffle = false
            if (repeatMode == 1) repeatMode = 0
        }
        if (index < 0) {
            index = 0
            openCurrent()
        }
        publish()
        save()
    }

    fun removeFromQueue(position: Int) {
        if (position !in queue.indices || position == index) return
        queue = queue.toMutableList().apply { removeAt(position) }
        if (position < index) index--
        publish()
        save()
    }

    fun cycleRepeat() {
        repeatMode = (repeatMode + 1) % 3
        publish()
        save()
    }

    fun toggleShuffle() {
        shuffle = !shuffle
        publish()
        save()
    }

    fun setSleepTimer(minutes: Int? = null, atEnd: Boolean = false) {
        timer?.cancel()
        timer = null
        sleepUntil = null
        sleepAtEnd = atEnd
        if (minutes != null) {
            sleepUntil = host.nowMs() + minutes * 60_000L
            timer = scope.launch {
                delay(minutes * 60_000L)
                sleepUntil = null
                pause()
            }
        }
        publish()
    }

    private fun cancelEndTimer() {
        if (sleepAtEnd) setSleepTimer()
    }

    private fun publish() {
        val track = queue.getOrNull(index)
        var duration = engine.durationMs
        if (duration <= 0) duration = track?.durationMs ?: 0
        if (track != null && duration > 0 && track.durationMs <= 0) {
            queue = queue.map { if (it.id == track.id) it.copy(durationMs = duration) else it }
            library.fillDuration(track.id, duration)
        }
        _state.value = PlaybackState(
            queue = queue,
            index = index,
            positionMs = engine.positionMs.coerceAtLeast(0),
            durationMs = duration,
            isPlaying = engine.isPlaying,
            isLoading = engine.isLoading,
            ready = engine.ready,
            repeatMode = repeatMode,
            shuffle = shuffle,
            sleepUntilMs = sleepUntil,
            sleepAtEnd = sleepAtEnd,
            error = error
        )
    }

    fun save() {
        if (restoring && !userChangedQueue) return
        val snapshot = PlaybackSnapshot(queue.map { it.id }, index.coerceAtLeast(0), engine.positionMs.coerceAtLeast(0), repeatMode, shuffle)
        scope.launch {
            saveLock.withLock {
                if (snapshot != lastSaved) {
                    try {
                        storage.savePlayback(snapshot)
                        lastSaved = snapshot
                    } catch (exception: Exception) {
                        if (exception is CancellationException) throw exception
                        error = "Не удалось сохранить позицию воспроизведения."
                        publish()
                    }
                }
            }
        }
    }
}

