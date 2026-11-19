@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.example.proekt.shared

import platform.AVFoundation.*
import platform.AVFAudio.*
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.*
import platform.darwin.NSObjectProtocol

internal class IosAudioEngine(private val storage: IosStorage) : AudioEngine {
    private val player = AVPlayer()
    private var endObserver: NSObjectProtocol? = null
    private var errorObserver: NSObjectProtocol? = null
    override var onEnded: () -> Unit = {}
    override var onError: (String) -> Unit = {}
    private var requestedPlayback = false
    private var savedPosition = 0L
    private var opening = false
    private var reportedFailure = false

    override val positionMs: Long
        get() {
            if (opening) return savedPosition
            val seconds = CMTimeGetSeconds(player.currentTime())
            if (!seconds.isFinite()) return savedPosition
            return (seconds * 1000).toLong().coerceAtLeast(0)
        }

    override val durationMs: Long
        get() {
            val item = player.currentItem ?: return 0
            val seconds = CMTimeGetSeconds(item.duration)
            if (!seconds.isFinite()) return 0
            return (seconds * 1000).toLong().coerceAtLeast(0)
        }

    override val isPlaying: Boolean
        get() {
            if (player.currentItem?.status == AVPlayerItemStatusFailed && !reportedFailure) {
                reportedFailure = true
                requestedPlayback = false
                onError("Этот аудиофайл недоступен или его формат не поддерживается.")
            }
            return requestedPlayback
        }
    override val isLoading: Boolean get() = player.currentItem != null && player.currentItem?.status == AVPlayerItemStatusUnknown
    override val ready: Boolean get() = player.currentItem?.status == AVPlayerItemStatusReadyToPlay

    override fun open(track: Track, positionMs: Long) {
        pause()
        reportedFailure = false
        val center = NSNotificationCenter.defaultCenter
        endObserver?.let { center.removeObserver(it) }
        errorObserver?.let { center.removeObserver(it) }
        val item = AVPlayerItem.playerItemWithURL(storage.audioUrl(track.uri))
        endObserver = center.addObserverForName(
            AVPlayerItemDidPlayToEndTimeNotification, item, NSOperationQueue.mainQueue
        ) { onEnded() }
        errorObserver = center.addObserverForName(
            AVPlayerItemFailedToPlayToEndTimeNotification, item, NSOperationQueue.mainQueue
        ) {
            requestedPlayback = false
            onError("Не удалось прочитать аудио. Выберите файл заново.")
        }
        player.replaceCurrentItemWithPlayerItem(item)
        savedPosition = positionMs
        opening = true
        player.seekToTime(CMTimeMakeWithSeconds(positionMs / 1000.0, 1000)) {
            opening = false
        }
    }

    override fun play() {
        val session = AVAudioSession.sharedInstance()
        val categorySet = session.setCategory(AVAudioSessionCategoryPlayback, null)
        val activated = session.setActive(true, null)
        if (!categorySet || !activated) {
            onError("Не удалось включить аудио. Повторите запуск.")
            return
        }
        if (player.currentItem?.status == AVPlayerItemStatusFailed) {
            onError("Этот файл недоступен или его формат не поддерживается.")
            return
        }
        requestedPlayback = true
        player.play()
    }

    override fun pause() {
        requestedPlayback = false
        player.pause()
    }

    override fun seek(positionMs: Long) {
        savedPosition = positionMs
        player.seekToTime(CMTimeMakeWithSeconds(positionMs / 1000.0, 1000))
    }

    override fun updateMetadata(track: Track) {
        // Control Center metadata is updated by IosPlaybackHost.
    }
}

