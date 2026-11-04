package `in`.s123dive.oneremote.ui.rooms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.s123dive.oneremote.core.Favourite
import `in`.s123dive.oneremote.core.Role
import `in`.s123dive.oneremote.core.Room
import `in`.s123dive.oneremote.core.Rooms
import `in`.s123dive.oneremote.ui.SectionCard
import `in`.s123dive.oneremote.ui.appGraph
import `in`.s123dive.oneremote.ui.setup.SetupViewModel
import kotlinx.coroutines.launch

/** Rooms: add / rename / delete, assign devices to roles, edit favourite channel chips. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomsScreen(onBack: () -> Unit, onAddDevice: (roomId: String) -> Unit, onOpenDevice: (String) -> Unit) {
    val graph = appGraph()
    val state by graph.repository.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var newRoom by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf<Room?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Rooms") }, navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            })
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.rooms.forEach { room ->
                RoomCard(room, state, onAddDevice = { onAddDevice(room.id) }, onOpenDevice = onOpenDevice, onDelete = { confirmDelete = room })
            }
            SectionCard("Add room") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = newRoom, onValueChange = { newRoom = it }, label = { Text("Room name") }, modifier = Modifier.weight(1f), singleLine = true)
                    Button(onClick = { scope.launch { graph.repository.update { Rooms.addRoom(it, newRoom, SetupViewModel.newId()) }; newRoom = "" } }) { Text("Add") }
                }
            }
        }
    }
    confirmDelete?.let { room ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete ${room.name}?") },
            text = { Text("Its ${state.devicesIn(room.id).size} device(s) and macros are deleted too.") },
            confirmButton = { TextButton(onClick = { scope.launch { graph.repository.update { Rooms.removeRoom(it, room.id) } }; confirmDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RoomCard(room: Room, state: `in`.s123dive.oneremote.core.AppState, onAddDevice: () -> Unit, onOpenDevice: (String) -> Unit, onDelete: () -> Unit) {
    val graph = appGraph()
    val scope = rememberCoroutineScope()
    var name by remember(room.name) { mutableStateOf(room.name) }
    var favName by remember { mutableStateOf("") }
    var favCh by remember { mutableStateOf("") }
    SectionCard(room.name) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedButton(onClick = { scope.launch { graph.repository.update { Rooms.renameRoom(it, room.id, name) } } }) { Text("Rename") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete room") }
        }
        Text("Devices", style = MaterialTheme.typography.labelLarge)
        val devices = state.devices
        if (devices.isEmpty()) Text("No devices yet.", style = MaterialTheme.typography.bodySmall)
        devices.forEach { d ->
            val role = Rooms.roleOf(room, d.id)
            Column(Modifier.fillMaxWidth()) {
                TextButton(onClick = { onOpenDevice(d.id) }) { Text("${d.name} · ${d.type.displayName}${if (d.roomId != room.id) " (in ${state.room(d.roomId)?.name ?: "?"})" else ""}") }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Role.entries.forEach { r ->
                        FilterChip(selected = role == r && d.roomId == room.id, onClick = { scope.launch { graph.repository.update { Rooms.assign(it, d.id, room.id, r) } } }, label = { Text(if (r == Role.EXTRA) "Extra" else r.name) })
                    }
                }
            }
        }
        OutlinedButton(onClick = onAddDevice) { Text("Add device to ${room.name}") }
        Text("Favourite channels", style = MaterialTheme.typography.labelLarge)
        room.favourites.forEach { f ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${f.name} → ${f.channel}")
                IconButton(onClick = { scope.launch { graph.repository.update { Rooms.setFavourites(it, room.id, room.favourites - f) } } }) { Icon(Icons.Default.Delete, contentDescription = "Remove") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = favName, onValueChange = { favName = it }, label = { Text("Name") }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(value = favCh, onValueChange = { favCh = it.filter(Char::isDigit) }, label = { Text("Ch") }, modifier = Modifier.weight(0.6f), singleLine = true)
            Button(enabled = favName.isNotBlank() && favCh.toIntOrNull() != null, onClick = {
                scope.launch { graph.repository.update { Rooms.setFavourites(it, room.id, room.favourites + Favourite(favName.trim(), favCh.toInt())) }; favName = ""; favCh = "" }
            }) { Text("Add") }
        }
    }
}
