package com.example.proekt.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.time.Clock

@Composable
internal fun PlayerScreen(player: PlayerController) {
    val state by player.state.collectAsState()
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    if (state.track == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Выберите трек в библиотеке") }
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 500.dp && maxHeight < maxWidth
        val coverSize = minOf(150.dp, maxHeight * 0.65f)
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(0.7f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Artwork(coverSize)
                }
                Column(Modifier.weight(1.5f).fillMaxHeight()) {
                    PlayerDetails(player, state, Modifier.weight(1f), showCover = false)
                    PlayerControls(player, state) { dialog = it }
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                PlayerDetails(player, state, Modifier.weight(1f), showCover = true)
                PlayerControls(player, state) { dialog = it }
            }
        }
    }
    if (dialog == "queue") QueueDialog(player, state) { dialog = null }
    if (dialog == "timer") TimerDialog(player, state) { dialog = null }
}

@Composable
private fun Artwork(size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.size(size).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text("♫", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun PlayerDetails(player: PlayerController, state: PlaybackState, modifier: Modifier, showCover: Boolean) {
    val track = state.track ?: return
    var scrub by remember(state.index, track.uri) { mutableStateOf<Float?>(null) }
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (showCover) Artwork(144.dp)
        Text(track.title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Text(track.artist, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val duration = state.durationMs.coerceAtLeast(0)
        val position = (scrub?.toLong() ?: state.positionMs).coerceIn(0, duration)
        Slider(
            value = position.toFloat(),
            valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
            enabled = state.ready && duration > 0,
            onValueChange = { scrub = it },
            onValueChangeFinished = {
                val selected = scrub
                if (selected != null) player.seek(selected.toLong())
                scrub = null
            }
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(position))
            Text(if (duration > 0) formatTime(duration) else "—")
        }
        if (state.isLoading) Text("Загрузка…", style = MaterialTheme.typography.bodySmall)
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
        val deadline = state.sleepUntilMs
        if (deadline != null) {
            Text("Таймер сна · ${formatTime(deadline - Clock.System.now().toEpochMilliseconds())}", style = MaterialTheme.typography.bodySmall)
        } else if (state.sleepAtEnd) {
            Text("Таймер сна · до конца трека", style = MaterialTheme.typography.bodySmall)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(
                selected = state.repeatMode != 0, onClick = player::cycleRepeat,
                label = { Text(when (state.repeatMode) {
                    1 -> "Повтор трека"
                    2 -> "Повтор очереди"
                    else -> "Без повтора"
                }) }
            )
            FilterChip(selected = state.shuffle, onClick = player::toggleShuffle, label = { Text("Перемешать") })
        }
    }
}

@Composable
private fun PlayerControls(player: PlayerController, state: PlaybackState, open: (String) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = player::previous, enabled = state.index > 0) { Text("Пред.") }
                FilledTonalButton(onClick = player::togglePlay) { Text(if (state.isPlaying) "Пауза" else "Играть") }
                TextButton(
                    onClick = player::next,
                    enabled = state.index < state.queue.lastIndex || state.repeatMode == 2 || state.shuffle
                ) { Text("След.") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = { open("queue") }) { Text("Очередь") }
                TextButton(onClick = { open("timer") }) { Text("Таймер сна") }
            }
        }
    }
}

@Composable
private fun QueueDialog(player: PlayerController, state: PlaybackState, close: () -> Unit) {
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Очередь воспроизведения") },
        text = {
            LazyColumn(
                Modifier.heightIn(max = 300.dp),
                state = rememberLazyListState(initialFirstVisibleItemIndex = state.index.coerceAtLeast(0))
            ) {
                itemsIndexed(state.queue) { index, track ->
                    ListItem(
                        headlineContent = { Text(track.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(if (index == state.index) "Текущий трек" else track.artist, maxLines = 1) },
                        modifier = Modifier.clickable { if (index != state.index) player.jumpTo(index) },
                        trailingContent = {
                            if (index != state.index) TextButton(onClick = { player.removeFromQueue(index) }) { Text("×") }
                        }
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text("Готово") } }
    )
}

@Composable
private fun TimerDialog(player: PlayerController, state: PlaybackState, close: () -> Unit) {
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Таймер сна") },
        text = {
            LazyColumn(Modifier.heightIn(max = 300.dp)) {
                items(3) { index ->
                    val minutes = listOf(15, 30, 60)[index]
                    ListItem(
                        headlineContent = { Text("Через $minutes мин") },
                        modifier = Modifier.clickable { player.setSleepTimer(minutes); close() }
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text("В конце текущего трека") },
                        modifier = Modifier.clickable { player.setSleepTimer(atEnd = true); close() }
                    )
                }
                if (state.sleepAtEnd || state.sleepUntilMs != null) {
                    item {
                        ListItem(headlineContent = { Text("Выключить таймер") },
                            modifier = Modifier.clickable { player.setSleepTimer(); close() })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text("Закрыть") } }
    )
}

