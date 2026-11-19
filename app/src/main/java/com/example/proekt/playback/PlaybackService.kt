package com.example.proekt.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState as SessionState
import android.os.Build
import android.os.IBinder
import com.example.proekt.MainActivity
import com.example.proekt.shared.PlaybackState
import com.example.proekt.shared.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PlaybackService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: PlayerController
    private lateinit var session: MediaSession
    private lateinit var openPlayer: PendingIntent
    private lateinit var notifications: NotificationManager
    private var foreground = false

    override fun onCreate() {
        super.onCreate()
        player = (application as com.example.proekt.MusicApplication).player
        notifications = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            notifications.createNotificationChannel(
                NotificationChannel(CHANNEL, "Воспроизведение музыки", NotificationManager.IMPORTANCE_LOW)
            )
        }
        openPlayer = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).setAction(OPEN_PLAYER)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        session = MediaSession(this, "Proekt").apply {
            setSessionActivity(openPlayer)
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = startPlayback()
                override fun onPause() = player.pause()
                override fun onStop() {
                    player.pause()
                    stopSelf()
                }
                override fun onSkipToNext() = player.next()
                override fun onSkipToPrevious() = player.previous()
                override fun onSeekTo(pos: Long) = player.seek(pos)
            })
            isActive = true
        }
        scope.launch { player.state.collect(::updateNotification) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (player.state.value.track == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            PREVIOUS -> player.previous()
            NEXT -> player.next()
            TOGGLE -> if (player.state.value.isPlaying) player.pause() else startPlayback()
            DISMISS -> {
                player.pause()
                stopSelf()
            }
            else -> {
                promote(player.state.value)
                player.playFromHost()
                updateNotification(player.state.value)
            }
        }
        return START_NOT_STICKY
    }

    private fun startPlayback() {
        if (player.state.value.track == null) return
        if (!foreground) promote(player.state.value)
        player.playFromHost()
    }

    private fun promote(state: PlaybackState) {
        startForeground(NOTIFICATION_ID, notification(state))
        foreground = true
    }

    private fun updateNotification(state: PlaybackState) {
        val track = state.track
        if (track == null) {
            if (foreground) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                foreground = false
            }
            session.setMetadata(null)
            notifications.cancel(NOTIFICATION_ID)
            return
        }
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, track.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, track.artist)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, state.durationMs)
                .build()
        )
        var actions = SessionState.ACTION_PLAY or SessionState.ACTION_PAUSE or
            SessionState.ACTION_PLAY_PAUSE or SessionState.ACTION_STOP
        if ((state.index > 0)) actions = actions or SessionState.ACTION_SKIP_TO_PREVIOUS
        if ((state.index < state.queue.lastIndex || state.repeatMode == 2 || state.shuffle)) actions = actions or SessionState.ACTION_SKIP_TO_NEXT
        if (state.ready) actions = actions or SessionState.ACTION_SEEK_TO
        val status = when {
            state.error != null -> SessionState.STATE_ERROR
            state.isLoading -> SessionState.STATE_BUFFERING
            state.isPlaying -> SessionState.STATE_PLAYING
            else -> SessionState.STATE_PAUSED
        }
        session.setPlaybackState(
            SessionState.Builder().setActions(actions)
                .setState(status, state.positionMs, if (state.isPlaying) 1f else 0f)
                .build()
        )
        if (!state.isPlaying && foreground) {
            stopForeground(STOP_FOREGROUND_DETACH)
            foreground = false
        }
        if (foreground || !state.isPlaying) {
            notifications.notify(NOTIFICATION_ID, notification(state))
        }
    }

    @Suppress("DEPRECATION")
    private fun notification(state: PlaybackState): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL)
            else Notification.Builder(this)
        val compact = mutableListOf<Int>()
        if ((state.index > 0)) {
            compact.add(compact.size)
            builder.addAction(android.R.drawable.ic_media_previous, "Предыдущий", action(PREVIOUS))
        }
        compact.add(compact.size)
        builder.addAction(
            if (state.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            if (state.isPlaying) "Пауза" else "Играть",
            action(TOGGLE)
        )
        if ((state.index < state.queue.lastIndex || state.repeatMode == 2 || state.shuffle)) {
            compact.add(compact.size)
            builder.addAction(android.R.drawable.ic_media_next, "Следующий", action(NEXT))
        }
        return builder
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(state.track?.title ?: "Музыка")
            .setContentText(state.track?.artist)
            .setContentIntent(openPlayer)
            .setDeleteIntent(action(DISMISS))
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setOngoing(state.isPlaying)
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken)
                .setShowActionsInCompactView(*compact.toIntArray()))
            .build()
    }

    private fun action(name: String): PendingIntent = PendingIntent.getService(
        this, 0, Intent(this, PlaybackService::class.java).setAction(name), PendingIntent.FLAG_IMMUTABLE
    )

    override fun onTaskRemoved(rootIntent: Intent?) {
        player.pause()
        player.setSleepTimer()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        scope.cancel()
        player.pause()
        session.release()
        notifications.cancel(NOTIFICATION_ID)
        super.onDestroy()
    }

    companion object {
        const val OPEN_PLAYER = "com.example.proekt.OPEN_PLAYER"
        private const val CHANNEL = "music_playback"
        private const val NOTIFICATION_ID = 101
        private const val PREVIOUS = "previous"
        private const val NEXT = "next"
        private const val TOGGLE = "toggle"
        private const val DISMISS = "dismiss"
    }
}

