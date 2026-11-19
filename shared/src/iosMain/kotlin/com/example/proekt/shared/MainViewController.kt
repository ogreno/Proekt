@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.example.proekt.shared

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.coroutines.*
import platform.AVFoundation.*
import platform.AVFAudio.*
import platform.Foundation.*
import platform.MediaPlayer.*
import platform.UIKit.*
import platform.darwin.NSObjectProtocol

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // The common top bar provides the Back button on iPhone.
}

private class IosMusic(screenshotMode: Boolean) : PlaybackHost {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val storage = IosStorage(screenshotMode)
    val importer = IosAudioImporter(storage, scope)
    val library = LibraryController(storage, importer, scope)
    val engine = IosAudioEngine(storage)
    val player = PlayerController(engine, storage, this, library, scope)
    private val observers = mutableListOf<NSObjectProtocol>()
    private val targets = mutableListOf<Any>()

    init {
        val commands = MPRemoteCommandCenter.sharedCommandCenter()
        targets.add(commands.playCommand.addTargetWithHandler {
            player.playFromHost()
            MPRemoteCommandHandlerStatusSuccess
        })
        targets.add(commands.pauseCommand.addTargetWithHandler {
            player.pause()
            MPRemoteCommandHandlerStatusSuccess
        })
        targets.add(commands.togglePlayPauseCommand.addTargetWithHandler {
            player.togglePlay()
            MPRemoteCommandHandlerStatusSuccess
        })
        targets.add(commands.nextTrackCommand.addTargetWithHandler {
            player.next()
            MPRemoteCommandHandlerStatusSuccess
        })
        targets.add(commands.previousTrackCommand.addTargetWithHandler {
            player.previous()
            MPRemoteCommandHandlerStatusSuccess
        })
        targets.add(commands.changePlaybackPositionCommand.addTargetWithHandler { event ->
            val position = event as? MPChangePlaybackPositionCommandEvent
            if (position != null) player.seek((position.positionTime * 1000).toLong())
            MPRemoteCommandHandlerStatusSuccess
        })
        val center = NSNotificationCenter.defaultCenter
        observers.add(center.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, NSOperationQueue.mainQueue) {
            player.save()
        })
        observers.add(center.addObserverForName(AVAudioSessionInterruptionNotification, null, NSOperationQueue.mainQueue) { notification ->
            val type = notification?.userInfo?.get(AVAudioSessionInterruptionTypeKey) as? NSNumber
            if (type?.unsignedIntegerValue == AVAudioSessionInterruptionTypeBegan) player.pause()
        })
        observers.add(center.addObserverForName(AVAudioSessionRouteChangeNotification, null, NSOperationQueue.mainQueue) { notification ->
            val reason = notification?.userInfo?.get(AVAudioSessionRouteChangeReasonKey) as? NSNumber
            if (reason?.unsignedIntegerValue == AVAudioSessionRouteChangeReasonOldDeviceUnavailable) player.pause()
        })
        scope.launch {
            player.state.collect { state ->
                val track = state.track
                if (track != null) {
                    MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = mapOf(
                        MPMediaItemPropertyTitle to track.title,
                        MPMediaItemPropertyArtist to track.artist,
                        MPMediaItemPropertyPlaybackDuration to state.durationMs / 1000.0,
                        MPNowPlayingInfoPropertyElapsedPlaybackTime to state.positionMs / 1000.0,
                        MPNowPlayingInfoPropertyPlaybackRate to if (state.isPlaying) 1.0 else 0.0
                    )
                } else {
                    MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = null
                }
                commands.previousTrackCommand.enabled = state.index > 0
                commands.nextTrackCommand.enabled = state.index < state.queue.lastIndex || state.repeatMode == 2 || state.shuffle
                commands.changePlaybackPositionCommand.enabled = state.ready
            }
        }
    }

    override fun requestPlay() { player.playFromHost() }
    override fun nowMs(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()
}

private var musicInstance: IosMusic? = null

fun MainViewController(screenshotMode: Boolean): UIViewController {
    if (musicInstance == null) musicInstance = IosMusic(screenshotMode)
    val music = requireNotNull(musicInstance)
    val controller = ComposeUIViewController {
        MusicApp(music.library, music.player)
    }
    music.importer.presenter = controller
    return controller
}

