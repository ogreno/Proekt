@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.example.proekt.shared

import kotlinx.cinterop.*
import kotlinx.coroutines.*
import platform.AVFoundation.*
import platform.CommonCrypto.*
import platform.CoreMedia.CMTimeGetSeconds
import platform.Foundation.*
import platform.UIKit.*
import platform.UniformTypeIdentifiers.UTTypeAudio
import platform.darwin.NSObject

internal class IosAudioImporter(
    private val storage: IosStorage,
    private val scope: CoroutineScope
) : NSObject(), AudioImporter, UIDocumentPickerDelegateProtocol {
    var presenter: UIViewController? = null
    private var result: ((Track?, String?) -> Unit)? = null

    override fun pickFile(onResult: (Track?, String?) -> Unit) {
        val parent = presenter
        if (parent == null || parent.presentedViewController != null) {
            onResult(null, "Закройте открытое окно и повторите выбор файла.")
            return
        }
        result = onResult
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeAudio), asCopy = true)
        picker.delegate = this
        picker.allowsMultipleSelection = false
        parent.presentViewController(picker, true, null)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        val callback = result
        result = null
        callback?.invoke(null, null)
    }

    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val callback = result ?: return
        result = null
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
        if (url == null) {
            callback(null, null)
            return
        }
        scope.launch {
            try {
                val track = withContext(Dispatchers.Default) { readAndCopy(url) }
                callback(track, null)
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                callback(null, "Не удалось импортировать аудио. Проверьте файл и свободное место.")
            }
        }
    }

    private fun readAndCopy(url: NSURL): Track {
        val access = url.startAccessingSecurityScopedResource()
        try {
            val asset = AVURLAsset(uRL = url, options = null)
            val seconds = CMTimeGetSeconds(asset.duration)
            check(seconds.isFinite() && seconds > 0) { "Audio duration unavailable" }
            var title: String? = null
            var artist: String? = null
            for (metadata in asset.commonMetadata) {
                val item = metadata as AVMetadataItem
                if (item.commonKey == AVMetadataCommonKeyTitle) title = item.stringValue
                if (item.commonKey == AVMetadataCommonKeyArtist) artist = item.stringValue
            }
            if (title.isNullOrBlank()) title = url.lastPathComponent?.substringBeforeLast('.')
            if (title.isNullOrBlank()) title = "Без названия"
            if (artist.isNullOrBlank()) artist = "Неизвестный исполнитель"
            // A content hash gives repeated imports of the same audio the same URI.
            val digest = contentHash(url)
            val relativePath = "audio/$digest.${url.pathExtension.orEmpty().lowercase()}"
            val destination = storage.audioUrl(relativePath)
            if (!NSFileManager.defaultManager.fileExistsAtPath(requireNotNull(destination.path))) {
                check(NSFileManager.defaultManager.copyItemAtURL(url, destination, null)) { "Could not copy audio" }
            }
            return Track(title = title.trim(), artist = artist.trim(), uri = relativePath, durationMs = (seconds * 1000).toLong())
        } finally {
            if (access) url.stopAccessingSecurityScopedResource()
        }
    }

    private fun contentHash(url: NSURL): String = memScoped {
        val stream = requireNotNull(NSInputStream.inputStreamWithURL(url))
        val context = alloc<CC_SHA256_CTX>()
        val buffer = allocArray<UByteVar>(65536)
        val digest = allocArray<UByteVar>(CC_SHA256_DIGEST_LENGTH)
        CC_SHA256_Init(context.ptr)
        stream.open()
        try {
            while (true) {
                val count = stream.read(buffer, 65536u)
                check(count >= 0) { "Could not read audio" }
                if (count == 0L) break
                CC_SHA256_Update(context.ptr, buffer, count.toUInt())
            }
            CC_SHA256_Final(digest, context.ptr)
            val text = StringBuilder()
            for (index in 0 until CC_SHA256_DIGEST_LENGTH) {
                text.append(digest[index].toString(16).padStart(2, '0'))
            }
            text.toString()
        } finally {
            stream.close()
        }
    }
}

