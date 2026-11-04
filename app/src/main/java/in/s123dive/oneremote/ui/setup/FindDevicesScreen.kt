package `in`.s123dive.oneremote.ui.setup

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.s123dive.oneremote.core.DeviceType
import `in`.s123dive.oneremote.ui.SectionCard
import `in`.s123dive.oneremote.ui.appGraph
import kotlinx.coroutines.launch

/** SSDP + subnet scan results for one TV type, and a manual IP box that is always there. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindDevicesScreen(type: DeviceType, onBack: () -> Unit, onPick: (ip: String, name: String?) -> Unit) {
    val graph = appGraph()
    val disc by graph.discovery.state.collectAsStateWithLifecycle()
    val caps by graph.capabilities.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var manualIp by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { if (!disc.running) graph.discovery.run(caps.ipv4) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Find ${type.displayName}") }, navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            })
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!caps.wifiConnected) Text("Phone is not on Wi-Fi. Discovery needs the same LAN as the TV.", color = MaterialTheme.colorScheme.error)
            SectionCard("Found on the network") {
                if (disc.running) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Searching (SSDP, then subnet scan)…", style = MaterialTheme.typography.bodySmall) }
                val matches = disc.found.filter { it.type == type }
                if (matches.isEmpty() && !disc.running) Text("Nothing found. Make sure the TV is on and on the same Wi-Fi, or enter its IP below.", style = MaterialTheme.typography.bodySmall)
                matches.forEach { f ->
                    ListItem(
                        modifier = Modifier.fillMaxWidth().clickable { onPick(f.ip, f.name) },
                        headlineContent = { Text(f.name) },
                        supportingContent = { Text("${f.ip} · ${f.model ?: f.type.displayName} · via ${f.via}") },
                    )
                }
                disc.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                OutlinedButton(enabled = !disc.running, onClick = { scope.launch { graph.discovery.run(caps.ipv4) } }) { Text("Search again") }
            }
            SectionCard("Manual IP") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = manualIp, onValueChange = { manualIp = it.trim() }, label = { Text("TV IP address") }, modifier = Modifier.weight(1f), singleLine = true)
                    Button(enabled = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$").matches(manualIp), onClick = { onPick(manualIp, null) }) { Text("Use") }
                }
            }
        }
    }
}
