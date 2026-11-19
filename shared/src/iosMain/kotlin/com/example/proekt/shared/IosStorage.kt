@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.example.proekt.shared

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import platform.Foundation.*

internal class IosStorage : LibraryStorage {
    private val json = Json { ignoreUnknownKeys = true }
    private val files = NSFileManager.defaultManager
    val directory: String

    init {
        val documents = files.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask).first() as NSURL
        directory = requireNotNull(documents.path)
        check(files.createDirectoryAtPath("$directory/audio", true, null, null))
    }

    override suspend fun loadLibrary(): Library = withContext(Dispatchers.Default) {
        val path = "$directory/library.json"
        if (!files.fileExistsAtPath(path)) return@withContext Library()
        val text = NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)
        checkNotNull(text) { "Could not read library" }
        json.decodeFromString<Library>(text)
    }

    override suspend fun saveLibrary(library: Library) = withContext(Dispatchers.Default) {
        write("library.json", json.encodeToString(library))
    }

    override suspend fun loadPlayback(): PlaybackSnapshot? = withContext(Dispatchers.Default) {
        val path = "$directory/playback.json"
        if (!files.fileExistsAtPath(path)) return@withContext null
        val text = NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)
        checkNotNull(text) { "Could not read playback" }
        json.decodeFromString<PlaybackSnapshot>(text)
    }

    override suspend fun savePlayback(snapshot: PlaybackSnapshot) = withContext(Dispatchers.Default) {
        write("playback.json", json.encodeToString(snapshot))
    }

    private fun write(filename: String, text: String) {
        val value = text as NSString
        check(value.writeToFile("$directory/$filename", true, NSUTF8StringEncoding, null)) {
            "Could not save $filename"
        }
    }

    fun audioUrl(uri: String): NSURL {
        return NSURL.fileURLWithPath("$directory/$uri")
    }
}

