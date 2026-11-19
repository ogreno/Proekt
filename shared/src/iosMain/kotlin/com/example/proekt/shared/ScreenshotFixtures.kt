@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.example.proekt.shared

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Foundation.writeToFile

// Тестовая библиотека создаётся в отдельной папке только при запуске UI-теста.
internal fun createScreenshotLibrary(directory: String): Library {
    writeSilentWav("$directory/audio/demo-one.wav", 45)
    writeSilentWav("$directory/audio/demo-two.wav", 70)
    writeSilentWav("$directory/audio/demo-three.wav", 92)
    val tracks = listOf(
        Track(1, "Demo — тестовый трек", "Учебная библиотека", "audio/demo-one.wav", 45_000, true),
        Track(2, "Длинное название композиции для проверки переноса текста", "Демонстрационный исполнитель", "audio/demo-two.wav", 70_000),
        Track(3, "Вечерний ритм", "Демо-исполнитель", "audio/demo-three.wav", 92_000, true)
    )
    val playlists = listOf(
        Playlist(1, "Mix для прогулки", listOf(1, 2)),
        Playlist(2, "Избранная подборка", listOf(3, 1))
    )
    return Library(tracks, playlists, nextTrackId = 4, nextPlaylistId = 3)
}

// PCM WAV с тишиной позволяет проверить настоящий плеер без чужой музыки.
private fun writeSilentWav(path: String, seconds: Int) {
    val sampleRate = 8000
    val audioSize = seconds * sampleRate * 2
    val bytes = ByteArray(44 + audioSize)
    fun text(offset: Int, value: String) {
        for (index in value.indices) bytes[offset + index] = value[index].code.toByte()
    }
    fun number(offset: Int, value: Int, size: Int) {
        for (index in 0 until size) {
            bytes[offset + index] = (value shr (index * 8)).toByte()
        }
    }
    text(0, "RIFF")
    number(4, bytes.size - 8, 4)
    text(8, "WAVE")
    text(12, "fmt ")
    number(16, 16, 4)
    number(20, 1, 2)
    number(22, 1, 2)
    number(24, sampleRate, 4)
    number(28, sampleRate * 2, 4)
    number(32, 2, 2)
    number(34, 16, 2)
    text(36, "data")
    number(40, audioSize, 4)
    bytes.usePinned { pinned ->
        val data = NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        check(data.writeToFile(path, true)) { "Could not create screenshot audio" }
    }
}

