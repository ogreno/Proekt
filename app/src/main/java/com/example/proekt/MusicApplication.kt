package com.example.proekt

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.proekt.playback.PlaybackService
import com.example.proekt.shared.LibraryController
import com.example.proekt.shared.PlaybackHost
import com.example.proekt.shared.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class MusicApplication : Application(), PlaybackHost {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var importer: AndroidAudioImporter
        private set
    lateinit var library: LibraryController
        private set
    lateinit var player: PlayerController
        private set

    override fun onCreate() {
        super.onCreate()
        val storage = AndroidStorage(this)
        importer = AndroidAudioImporter(this, scope)
        library = LibraryController(storage, importer, scope)
        player = PlayerController(AndroidAudioEngine(this), storage, this, library, scope)
    }

    override fun requestPlay() {
        ContextCompat.startForegroundService(this, Intent(this, PlaybackService::class.java).setAction("play"))
    }

    override fun nowMs(): Long = System.currentTimeMillis()
}

