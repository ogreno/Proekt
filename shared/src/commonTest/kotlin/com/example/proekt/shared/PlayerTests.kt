package com.example.proekt.shared

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerTests {
    private val first = Track(1, "Первый", "Автор", "file-one", 180_000)
    private val second = Track(2, "Второй", "Автор", "file-two", 240_000)

    @Test
    fun restoreSkipsMissingFilesAndStaysPaused() = runTest {
        val store = MemoryStorage(Library(listOf(first, second)))
        store.playback = PlaybackSnapshot(listOf(99, 2, 1), 1, 42_000, 2, true)
        val importer = TestImporter()
        val library = LibraryController(store, importer, backgroundScope)
        val engine = TestEngine()
        val host = TestHost()
        val player = PlayerController(engine, store, host, library, backgroundScope)
        runCurrent()

        assertEquals(listOf(2, 1), player.state.value.queue.map { it.id })
        assertEquals(2, player.state.value.track?.id)
        assertEquals(42_000L, engine.positionMs)
        assertEquals(2, player.state.value.repeatMode)
        assertTrue(player.state.value.shuffle)
        assertFalse(engine.isPlaying)
        assertEquals(0, host.playRequests)
    }

    @Test
    fun importSameUriKeepsEditedMetadataAndPlaylist() = runTest {
        val original = first.copy(title = "Моё название", isFavorite = true)
        val store = MemoryStorage(Library(listOf(original), listOf(Playlist(1, "Любимое", listOf(1))), 2, 2))
        val importer = TestImporter()
        val library = LibraryController(store, importer, backgroundScope)
        runCurrent()
        library.importTrack()
        importer.finish(first.copy(title = "Тег из файла"))
        runCurrent()

        assertEquals(1, library.library.value.tracks.size)
        assertEquals("Моё название", library.library.value.tracks.single().title)
        assertTrue(library.library.value.tracks.single().isFavorite)
        assertEquals(listOf(1), library.library.value.playlists.single().trackIds)
    }

    @Test
    fun nextInsertionOverridesShuffleAndRepeatOne() = runTest {
        val store = MemoryStorage(Library(listOf(first, second)))
        val library = LibraryController(store, TestImporter(), backgroundScope)
        val engine = TestEngine()
        val host = TestHost()
        val player = PlayerController(engine, store, host, library, backgroundScope)
        host.play = player::playFromHost
        runCurrent()
        player.playSelection(listOf(first), first.id)
        player.cycleRepeat()
        player.toggleShuffle()
        player.enqueue(second, true)

        assertFalse(player.state.value.shuffle)
        assertEquals(0, player.state.value.repeatMode)
        player.next()
        assertEquals(2, player.state.value.track?.id)
        assertTrue(engine.isPlaying)
    }

    @Test
    fun endOfTrackTimerStopsBeforeNextTrack() = runTest {
        val store = MemoryStorage(Library(listOf(first, second)))
        val library = LibraryController(store, TestImporter(), backgroundScope)
        val engine = TestEngine()
        val host = TestHost()
        val player = PlayerController(engine, store, host, library, backgroundScope)
        host.play = player::playFromHost
        runCurrent()
        player.playSelection(listOf(first, second), first.id)
        player.cycleRepeat()
        player.setSleepTimer(atEnd = true)
        engine.onEnded()

        assertEquals(1, player.state.value.track?.id)
        assertFalse(engine.isPlaying)
        assertFalse(player.state.value.sleepAtEnd)
        assertEquals(0L, engine.positionMs)
    }

    @Test
    fun deletingTrackUpdatesQueueAndPlaylist() = runTest {
        val store = MemoryStorage(Library(listOf(first, second), listOf(Playlist(1, "Список", listOf(1, 2))), 3, 2))
        val library = LibraryController(store, TestImporter(), backgroundScope)
        val engine = TestEngine()
        val host = TestHost()
        val player = PlayerController(engine, store, host, library, backgroundScope)
        host.play = player::playFromHost
        runCurrent()
        player.playSelection(listOf(first, second), first.id)
        library.deleteTrack(1)
        runCurrent()

        assertEquals(listOf(2), player.state.value.queue.map { it.id })
        assertEquals(2, player.state.value.track?.id)
        assertEquals(listOf(2), library.library.value.playlists.single().trackIds)
    }

    @Test
    fun failedSaveKeepsPreviousLibrary() = runTest {
        val store = MemoryStorage(Library(listOf(first)))
        val library = LibraryController(store, TestImporter(), backgroundScope)
        runCurrent()
        store.fail = true
        library.deleteTrack(1)
        runCurrent()
        assertEquals(listOf(first), library.library.value.tracks)
        assertTrue(library.error.value != null)
    }
}

private class MemoryStorage(var library: Library) : LibraryStorage {
    var playback: PlaybackSnapshot? = null
    var fail = false
    override suspend fun loadLibrary() = library
    override suspend fun saveLibrary(library: Library) {
        check(!fail)
        this.library = library
    }
    override suspend fun loadPlayback() = playback
    override suspend fun savePlayback(snapshot: PlaybackSnapshot) { playback = snapshot }
}

private class TestImporter : AudioImporter {
    private var result: ((Track?, String?) -> Unit)? = null
    override fun pickFile(onResult: (Track?, String?) -> Unit) { result = onResult }
    fun finish(track: Track) { result?.invoke(track, null) }
}

private class TestHost : PlaybackHost {
    var play: () -> Unit = {}
    var playRequests = 0
    override fun requestPlay() { playRequests++; play() }
    override fun nowMs() = 0L
}

private class TestEngine : AudioEngine {
    override var onEnded: () -> Unit = {}
    override var onError: (String) -> Unit = {}
    override var positionMs: Long = 0
    override var durationMs: Long = 0
    override var isPlaying = false
    override val isLoading = false
    override val ready = true
    override fun open(track: Track, positionMs: Long) { this.positionMs = positionMs; durationMs = track.durationMs }
    override fun play() { isPlaying = true }
    override fun pause() { isPlaying = false }
    override fun seek(positionMs: Long) { this.positionMs = positionMs }
    override fun updateMetadata(track: Track) {}
}

