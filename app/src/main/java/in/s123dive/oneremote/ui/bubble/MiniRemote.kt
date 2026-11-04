package `in`.s123dive.oneremote.ui.bubble

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `in`.s123dive.oneremote.AppGraph
import `in`.s123dive.oneremote.core.Command
import `in`.s123dive.oneremote.core.Role
import `in`.s123dive.oneremote.core.Rooms
import `in`.s123dive.oneremote.core.RouteRequest
import `in`.s123dive.oneremote.core.RouteResult
import `in`.s123dive.oneremote.core.Source
import `in`.s123dive.oneremote.ui.remote.Key
import `in`.s123dive.oneremote.ui.remote.PadActions
import kotlinx.coroutines.launch

/** The compact remote shown inside the bubble: TV power/volume, box channels, OK/Back. */
@Composable
fun MiniRemote(graph: AppGraph, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var last by remember { mutableStateOf<String?>(null) }
    val state = graph.repository.current()
    val room = state.room(graph.lastRoomId() ?: "") ?: state.rooms.firstOrNull()
    fun actions(zone: Role) = PadActions(
        supports = { room != null },
        send = { cmd ->
            val r = room ?: return@PadActions
            scope.launch {
                val res = graph.router.send(RouteRequest(r.id, Rooms.targetRole(r, zone, cmd), cmd, Source.SCREEN))
                last = when (res) { is RouteResult.Sent -> "${cmd.key} via ${res.transport.badge}"; is RouteResult.Failed -> "${cmd.key}: ${res.reason}"; is RouteResult.Unsupported -> res.reason }
            }
        },
        onUnsupported = { last = "No room set up" },
    )
    val tv = actions(Role.TV); val box = actions(if (room?.stbId != null) Role.STB else Role.TV)
    Column(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(room?.name ?: "OneRemote", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Key(tv, Command.Power, "Power", Modifier.weight(1f), Icons.Default.PowerSettingsNew, accent = true)
            Key(tv, Command.VolDown, "Vol−", Modifier.weight(1f), Icons.AutoMirrored.Filled.VolumeDown, repeat = true)
            Key(tv, Command.Mute, "Mute", Modifier.weight(1f), Icons.AutoMirrored.Filled.VolumeMute)
            Key(tv, Command.VolUp, "Vol+", Modifier.weight(1f), Icons.AutoMirrored.Filled.VolumeUp, repeat = true)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Key(box, Command.ChDown, "Ch−", Modifier.weight(1f), Icons.Default.KeyboardArrowDown, repeat = true)
            Key(box, Command.Ok, "OK", Modifier.weight(1f), accent = true)
            Key(box, Command.ChUp, "Ch+", Modifier.weight(1f), Icons.Default.KeyboardArrowUp, repeat = true)
            Key(box, Command.Back, "Back", Modifier.weight(1f), Icons.AutoMirrored.Filled.ArrowBack)
        }
        last?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
    }
}
