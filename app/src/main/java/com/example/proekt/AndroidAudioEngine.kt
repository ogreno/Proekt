package com.example.proekt

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.proekt.shared.AudioEngine
import com.example.proekt.shared.Track

@OptIn(UnstableApi::class)
class AndroidAudioEngine(context: Context) : AudioEngine {
    private val player = ExoPlayer.Builder(context.applicationContext).build()
    override var onEnded: () -> Unit = {}
    override var onError: (String) -> Unit = {}

    init {
        player.setHandleAudioBecomingNoisy(true)
        player.setWakeMode(C.WAKE_MODE_LOCAL)
        val attributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build()
        player.setAudioAttributes(attributes, true)
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) onEnded()
            }
            override fun onPlayerError(error: PlaybackException) {
                onError("Не удалось воспроизвести файл. Выберите его заново в меню трека.")
            }
        })
    }

    override val positionMs: Long get() = player.currentPosition.coerceAtLeast(0)
    override val durationMs: Long get() = player.duration.coerceAtLeast(0)
    override val isPlaying: Boolean get() = player.playWhenReady && player.playerError == null
    override val isLoading: Boolean get() = player.playbackState == Player.STATE_BUFFERING
    override val ready: Boolean get() = player.playbackState == Player.STATE_READY

    override fun open(track: Track, positionMs: Long) {
        player.setMediaItem(item(track), positionMs)
        player.prepare()
    }

    private fun item(track: Track): MediaItem {
        val metadata = MediaMetadata.Builder().setTitle(track.title).setArtist(track.artist).build()
        return MediaItem.Builder().setMediaId(track.id.toString()).setUri(track.uri).setMediaMetadata(metadata).build()
    }

    override fun play() {
        if (player.playerError != null || player.playbackState == Player.STATE_IDLE) player.prepare()
        player.play()
    }

    override fun pause() { player.pause() }
    override fun seek(positionMs: Long) { player.seekTo(positionMs) }

    override fun updateMetadata(track: Track) {
        if (player.currentMediaItem != null) player.replaceMediaItem(0, item(track))
    }
}

