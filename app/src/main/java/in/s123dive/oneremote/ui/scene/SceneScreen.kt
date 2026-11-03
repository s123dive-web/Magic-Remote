package `in`.s123dive.oneremote.ui.scene

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

/**
 * Phase 0: the Scene screen shows what the phone can do and that no room exists yet.
 * The dual-pane TV/STB remote replaces the placeholder in Phase 4.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SceneScreen(onOpenSettings: () -> Unit) {
    val graph = appGraph()
    val caps by graph.capabilities.state.collectAsStateWithLifecycle()
    val state by graph.repository.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OneRemote") },
                actions = {
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CapabilitiesCard(caps)

            SectionCard("Rooms") {
                if (state.rooms.isEmpty()) {
                    Text("No rooms yet. Device setup (Airtel box, LG TV, Sony Bravia) is added in the next phases of this build.")
                } else {
                    state.rooms.forEach { room ->
                        KeyValue(room.name, "${state.devicesIn(room.id).size} device(s)")
                    }
                }
            }
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
