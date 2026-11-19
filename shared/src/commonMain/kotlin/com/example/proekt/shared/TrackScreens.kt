package com.example.proekt.shared

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun SearchField(query: String, change: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = query, onValueChange = change,
        label = { Text(label) }, singleLine = true,
        trailingIcon = {
            if (query.isNotEmpty()) TextButton(onClick = { change("") }) { Text("×") }
        },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    )
}

@Composable
internal fun TrackRows(
    tracks: List<Track>,
    modifier: Modifier,
    empty: String,
    play: (Track) -> Unit,
    actions: @Composable (Track, () -> Unit) -> Unit
) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
        if (tracks.isEmpty()) item { Text(empty, Modifier.padding(12.dp)) }
        items(tracks, key = { it.id }) { track ->
            var expanded by remember { mutableStateOf(false) }
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable { play(track) }.padding(12.dp)) {
                        Text(track.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                        Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(if (track.durationMs > 0) formatTime(track.durationMs) else "—", style = MaterialTheme.typography.labelMedium)
                    Box {
                        TextButton(onClick = { expanded = true }, contentPadding = PaddingValues(0.dp), modifier = Modifier.width(48.dp)) { Text("⋮") }
                        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                            actions(track) { expanded = false }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun QueueActions(track: Track, player: PlayerController, close: () -> Unit) {
    DropdownMenuItem(
        text = { Text("Играть следующим") },
        onClick = { close(); player.enqueue(track, true) }
    )
    DropdownMenuItem(
        text = { Text("В конец очереди") },
        onClick = { close(); player.enqueue(track, false) }
    )
}

@Composable
internal fun TracksScreen(
    favorites: Boolean,
    data: Library,
    library: LibraryController,
    player: PlayerController,
    onPlay: (List<Track>, Int) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var editId by rememberSaveable { mutableStateOf<Int?>(null) }
    var title by rememberSaveable { mutableStateOf("") }
    var artist by rememberSaveable { mutableStateOf("") }
    var deleteId by rememberSaveable { mutableStateOf<Int?>(null) }
    val importing by library.importing.collectAsState()
    val tracks = data.tracks.filter { !favorites || it.isFavorite }.sortedBy { it.title.lowercase() }
    val shown = findTracks(tracks, query)

    Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp)) {
        if (!favorites) {
            Button(
                onClick = { library.importTrack() }, enabled = !importing,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (importing) "Чтение файла…" else "Добавить трек") }
        }
        SearchField(query, { query = it }, "Название или исполнитель")
        TrackRows(
            shown, Modifier.weight(1f), if (query.isBlank()) "Пока нет треков" else "Ничего не найдено",
            play = { onPlay(tracks, it.id) }
        ) { track, close ->
            QueueActions(track, player, close)
            DropdownMenuItem(text = { Text("Изменить название и исполнителя") }, onClick = {
                close()
                title = track.title
                artist = track.artist
                editId = track.id
            })
            DropdownMenuItem(
                text = { Text(if (track.isFavorite) "Убрать из избранного" else "В избранное") },
                onClick = { close(); library.setFavorite(track.id, !track.isFavorite) }
            )
            DropdownMenuItem(
                text = { Text("Выбрать файл заново") }, enabled = !importing,
                onClick = { close(); library.importTrack(track.id) }
            )
            DropdownMenuItem(text = { Text("Удалить трек") }, onClick = { close(); deleteId = track.id })
        }
    }

    editId?.let { id ->
        AlertDialog(
            onDismissRequest = { editId = null },
            title = { Text("Сведения о треке") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text("Название") }, singleLine = true)
                    OutlinedTextField(artist, { artist = it }, label = { Text("Исполнитель") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(enabled = title.isNotBlank(), onClick = { library.editTrack(id, title, artist); editId = null }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { editId = null }) { Text("Отмена") } }
        )
    }
    deleteId?.let { id ->
        ConfirmDelete(
            "Удалить трек?", "Он будет убран из библиотеки, избранного и плейлистов.",
            dismiss = { deleteId = null },
            confirm = { library.deleteTrack(id); deleteId = null }
        )
    }
}

@Composable
internal fun ConfirmDelete(title: String, text: String, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = confirm) { Text("Удалить") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Отмена") } }
    )
}

