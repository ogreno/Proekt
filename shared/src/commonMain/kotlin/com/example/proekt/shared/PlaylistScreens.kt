package com.example.proekt.shared

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.time.Clock

@Composable
internal fun PlaylistsScreen(data: Library, library: LibraryController, open: (Int) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var deleteId by rememberSaveable { mutableStateOf<Int?>(null) }
    val shown = data.playlists.filter { it.name.contains(query.trim(), ignoreCase = true) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Button(onClick = { showCreate = true }, modifier = Modifier.fillMaxWidth().testTag("create-playlist")) { Text("Создать плейлист") }
        SearchField(query, { query = it }, "Название плейлиста")
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            if (shown.isEmpty()) item { Text(if (query.isBlank()) "Нет плейлистов" else "Ничего не найдено") }
            items(shown, key = { it.id }) { playlist ->
                Card {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable { open(playlist.id) }.testTag("playlist-${playlist.id}").padding(16.dp)) {
                            Text(playlist.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("Треков · ${playlist.trackIds.size}", style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { deleteId = playlist.id }) { Text("Удалить") }
                    }
                }
            }
        }
    }
    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("Новый плейлист") },
            text = { OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    library.addPlaylist(name, Clock.System.now().toEpochMilliseconds())
                    name = ""
                    showCreate = false
                }) { Text("Создать") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Отмена") } }
        )
    }
    deleteId?.let { id ->
        ConfirmDelete(
            "Удалить плейлист?", "Будет удалён плейлист и его список треков.",
            dismiss = { deleteId = null }, confirm = { library.deletePlaylist(id); deleteId = null }
        )
    }
}

@Composable
internal fun PlaylistScreen(
    playlistId: Int, data: Library, library: LibraryController,
    player: PlayerController, onPlay: (List<Track>, Int) -> Unit
) {
    val playlist = data.playlists.find { it.id == playlistId }
    var query by rememberSaveable { mutableStateOf("") }
    var addQuery by rememberSaveable { mutableStateOf("") }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    if (playlist == null) {
        Text("Плейлист удалён", Modifier.padding(16.dp))
        return
    }
    val byId = data.tracks.associateBy { it.id }
    val tracks = playlist.trackIds.mapNotNull { byId[it] }
    val shown = findTracks(tracks, query)
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = tracks.isNotEmpty(), onClick = { onPlay(tracks, tracks.first().id) }) { Text("Слушать") }
            OutlinedButton(onClick = { showAdd = true }, modifier = Modifier.testTag("add-playlist-track")) { Text("Добавить трек") }
        }
        SearchField(query, { query = it }, "Название или исполнитель")
        TrackRows(
            shown, Modifier.weight(1f), if (query.isBlank()) "В плейлисте нет треков" else "Ничего не найдено",
            play = { onPlay(tracks, it.id) }
        ) { track, close ->
            QueueActions(track, player, close)
            DropdownMenuItem(
                text = { Text("Убрать из плейлиста") },
                onClick = { close(); library.removeFromPlaylist(playlistId, track.id) }
            )
        }
    }
    if (showAdd) {
        val available = findTracks(data.tracks.filter { it.id !in playlist.trackIds }, addQuery)
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Добавить трек") },
            text = {
                Column {
                    SearchField(addQuery, { addQuery = it }, "Поиск трека")
                    LazyColumn(Modifier.heightIn(max = 280.dp)) {
                        if (available.isEmpty()) item { Text("Нет подходящих треков") }
                        items(available, key = { it.id }) { track ->
                            ListItem(
                                headlineContent = { Text(track.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                modifier = Modifier.clickable { library.addToPlaylist(playlistId, track.id); showAdd = false }
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAdd = false }) { Text("Закрыть") } }
        )
    }
}

