package com.example.proekt

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.example.proekt.shared.AudioImporter
import com.example.proekt.shared.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AndroidAudioImporter(
    private val context: Context,
    private val scope: CoroutineScope
) : AudioImporter {
    var launchPicker: (() -> Unit)? = null
    private var result: ((Track?, String?) -> Unit)? = null

    override fun pickFile(onResult: (Track?, String?) -> Unit) {
        val picker = launchPicker
        if (picker == null) {
            onResult(null, "Откройте приложение, чтобы выбрать файл.")
            return
        }
        result = onResult
        picker()
    }

    fun selected(uri: Uri?) {
        val callback = result ?: return
        result = null
        if (uri == null) {
            callback(null, null)
            return
        }
        scope.launch {
            try {
                val track = withContext(Dispatchers.IO) {
                    val metadata = readAudioFile(uri)
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    metadata
                }
                callback(track, null)
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                callback(null, "Не удалось прочитать аудио или сохранить доступ к файлу.")
            }
        }
    }

    private fun readAudioFile(uri: Uri): Track {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            var title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            var artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val durationText = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
            val duration = durationText?.toLongOrNull() ?: 0L
            if (hasAudio != "yes" && duration <= 0) error("No audio stream")

            if (title.isNullOrBlank()) title = readFilename(uri)
            if (title.isNullOrBlank()) title = "Без названия"
            if (artist.isNullOrBlank()) {
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            }
            if (artist.isNullOrBlank()) artist = "Неизвестный исполнитель"

            return Track(title = title.trim(), artist = artist.trim(), uri = uri.toString(), durationMs = duration)
        } finally {
            retriever.release()
        }
    }

    private fun readFilename(uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        if (cursor == null) return null
        cursor.use {
            if (!cursor.moveToFirst()) return null
            val filename = cursor.getString(0)
            return filename.substringBeforeLast('.')
        }
    }
}

