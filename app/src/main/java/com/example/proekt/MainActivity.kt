package com.example.proekt

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.example.proekt.shared.MusicApp
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val openPlayer = MutableStateFlow(false)
    private val music get() = application as MusicApplication
    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        music.importer.selected(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        music.importer.launchPicker = { filePicker.launch(arrayOf("audio/*")) }
        if (savedInstanceState == null && intent.action == OPEN_PLAYER) openPlayer.value = true
        setContent {
            val showPlayer by openPlayer.collectAsState()
            MusicApp(
                music.library, music.player, showPlayer,
                onPlayerOpened = { openPlayer.value = false },
                exit = { finish() }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == OPEN_PLAYER) openPlayer.value = true
    }

    override fun onStop() {
        music.player.save()
        super.onStop()
    }

    override fun onDestroy() {
        music.importer.launchPicker = null
        super.onDestroy()
    }

    companion object { const val OPEN_PLAYER = "com.example.proekt.OPEN_PLAYER" }
}

