package com.example.proekt.shared

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicApp(
    library: LibraryController,
    player: PlayerController,
    openPlayer: Boolean = false,
    onPlayerOpened: () -> Unit = {},
    exit: () -> Unit = {}
) {
    val data by library.library.collectAsState()
    val loading by library.loading.collectAsState()
    val error by library.error.collectAsState()
    val playback by player.state.collectAsState()
    var screen by rememberSaveable { mutableStateOf("home") }
    var previous by rememberSaveable { mutableStateOf("home") }
    var playlistId by rememberSaveable { mutableIntStateOf(0) }
    val savedScreens = rememberSaveableStateHolder()

    fun goBack() {
        screen = when (screen) {
            "player" -> previous
            "detail" -> "playlists"
            else -> "home"
        }
    }
    fun showPlayer() {
        if (screen != "player") previous = screen
        screen = "player"
    }

    LaunchedEffect(openPlayer) {
        if (openPlayer) {
            showPlayer()
            onPlayerOpened()
        }
    }
    PlatformBackHandler(enabled = true) {
        if (screen == "home") exit() else goBack()
    }

    val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colors) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(when (screen) {
                        "tracks" -> "Мои треки"
                        "favorites" -> "Избранное"
                        "playlists" -> "Плейлисты"
                        "detail" -> data.playlists.find { it.id == playlistId }?.name ?: "Плейлист"
                        "player" -> "Плеер"
                        else -> "Музыка"
                    }, maxLines = 1) },
                    navigationIcon = {
                        if (screen != "home") {
                            TextButton(onClick = { goBack() }, modifier = Modifier.testTag("go-back")) { Text("Назад") }
                        }
                    }
                )
            },
            bottomBar = {
                val track = playback.track
                if (screen != "player" && track != null) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Row(
                            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { showPlayer() }, modifier = Modifier.weight(1f).testTag("open-player")) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(track.title, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    Text(
                                        if (playback.error != null) "Ошибка воспроизведения" else track.artist,
                                        maxLines = 1, style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            TextButton(onClick = player::togglePlay) {
                                Text(if (playback.isPlaying) "Пауза" else "Играть")
                            }
                        }
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                if (loading) {
                    CircularProgressIndicator(Modifier.align(androidx.compose.ui.Alignment.Center))
                } else {
                    savedScreens.SaveableStateProvider("$screen/$playlistId") {
                        when (screen) {
                            "home" -> HomeScreen { screen = it }
                            "tracks", "favorites" -> TracksScreen(
                                screen == "favorites", data, library, player,
                                onPlay = { tracks, id -> player.playSelection(tracks, id); showPlayer() }
                            )
                            "playlists" -> PlaylistsScreen(data, library) { id ->
                                playlistId = id
                                screen = "detail"
                            }
                            "detail" -> PlaylistScreen(playlistId, data, library, player) { tracks, id ->
                                player.playSelection(tracks, id)
                                showPlayer()
                            }
                            "player" -> PlayerScreen(player)
                        }
                    }
                }
            }
        }
        if (error != null) {
            AlertDialog(
                onDismissRequest = library::clearError,
                title = { Text("Не удалось выполнить действие") },
                text = { Text(error.orEmpty()) },
                confirmButton = {
                    TextButton(onClick = { library.clearError(); library.reload() }) { Text("Понятно") }
                }
            )
        }
    }
}

@Composable
private fun HomeScreen(navigate: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, androidx.compose.ui.Alignment.CenterVertically)
    ) {
        Button(onClick = { navigate("tracks") }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("home-tracks")) { Text("Все треки") }
        Button(onClick = { navigate("favorites") }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("home-favorites")) { Text("Избранное") }
        Button(onClick = { navigate("playlists") }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("home-playlists")) { Text("Мои плейлисты") }
    }
}

