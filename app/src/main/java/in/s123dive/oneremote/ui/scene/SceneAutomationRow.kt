package `in`.s123dive.oneremote.ui.scene

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.s123dive.oneremote.data.SettingsStore
import `in`.s123dive.oneremote.ui.appGraph
import `in`.s123dive.oneremote.ui.macros.MacroRow
import kotlinx.coroutines.launch

/** Macro chips + the sleep timer chip at the top of the Scene. */
@Composable
fun SceneAutomationRow(roomId: String, onEditMacros: () -> Unit) {
    val graph = appGraph()
    val settings by graph.settings.settings.collectAsStateWithLifecycle(SettingsStore.Settings())
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    val pendingAt = settings.sleepTimerAt?.takeIf { settings.sleepTimerRoom == roomId && it > System.currentTimeMillis() }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = { if (pendingAt != null) { graph.sleepTimer.cancel(roomId); scope.launch { graph.settings.clearSleepTimer() } } else dialog = true },
                label = { Text(if (pendingAt != null) "Sleep: off in ${((pendingAt - System.currentTimeMillis()) / 60_000) + 1} min (tap to cancel)" else "Sleep timer") },
            )
        }
        MacroRow(roomId, onEdit = onEditMacros)
        note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
    if (dialog) AlertDialog(
        onDismissRequest = { dialog = false },
        title = { Text("TV off in…") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 60, 90).forEach { m ->
                    TextButton(onClick = {
                        note = graph.sleepTimer.schedule(roomId, m)
                        scope.launch { graph.settings.setSleepTimer(roomId, System.currentTimeMillis() + m * 60_000L) }
                        dialog = false
                    }) { Text("$m min") }
                }
            }
        },
        confirmButton = { TextButton(onClick = { dialog = false }) { Text("Cancel") } },
    )
}
