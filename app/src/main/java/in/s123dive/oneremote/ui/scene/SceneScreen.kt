package `in`.s123dive.oneremote.ui.scene

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.s123dive.oneremote.system.PhoneCapabilities
import `in`.s123dive.oneremote.ui.KeyValue
import `in`.s123dive.oneremote.ui.SectionCard
import `in`.s123dive.oneremote.ui.appGraph
import `in`.s123dive.oneremote.ui.setup.SetupViewModel

/**
 * Phase 1 Scene: phone capabilities, rooms with their devices, and a way to add one.
 * The dual-pane TV/STB remote replaces the device list in Phase 4.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SceneScreen(onOpenSettings: () -> Unit, onAddDevice: (roomId: String?) -> Unit, onOpenRemote: (instanceId: String) -> Unit) {
    val graph = appGraph()
    val caps by graph.capabilities.state.collectAsStateWithLifecycle()
    val state by graph.repository.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OneRemote") },
                actions = { IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onAddDevice(state.rooms.firstOrNull()?.id) }) { Icon(Icons.Default.Add, contentDescription = "Add device") }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.devices.isEmpty()) {
                SectionCard("Get started") { Text("Tap + to add your Airtel box, LG TV or Sony Bravia.") }
            }
            state.rooms.forEach { room ->
                SectionCard(room.name) {
                    val devices = state.devicesIn(room.id)
                    if (devices.isEmpty()) Text("No devices in this room yet.", style = MaterialTheme.typography.bodySmall)
                    devices.forEach { d ->
                        ListItem(
                            modifier = Modifier.fillMaxWidth().clickable { onOpenRemote(d.id) },
                            headlineContent = { Text(d.name) },
                            supportingContent = { Text("${d.type.displayName} · ${SetupViewModel.roleOf(state, d.id)} · ${d.transports.joinToString("/") { it.badge }}") },
                        )
                    }
                }
            }
            CapabilitiesCard(caps)
        }
    }
}

@Composable
fun CapabilitiesCard(caps: PhoneCapabilities) {
    SectionCard("This phone") {
        KeyValue("IR blaster", if (caps.ir.hasIr) "present" else "none", highlight = !caps.ir.hasIr)
        KeyValue("IR carrier ranges", caps.ir.describeRanges())
        KeyValue("38 kHz (Airtel, LG IR)", yesNo(caps.ir.supports38k), highlight = !caps.ir.supports38k)
        KeyValue("40 kHz (Sony IR)", yesNo(caps.ir.supports40k), highlight = !caps.ir.supports40k)
        KeyValue("Wi-Fi / LAN", if (caps.wifiConnected) "connected" else "not connected", highlight = !caps.wifiConnected)
        KeyValue("Phone IP", caps.ipv4 ?: "–")
        listOfNotNull(caps.airtelMessage, caps.lgIrMessage, caps.sonyIrMessage).forEach {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

private fun yesNo(b: Boolean) = if (b) "yes" else "no"
