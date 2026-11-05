package `in`.s123dive.oneremote.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.s123dive.oneremote.core.DeviceType
import `in`.s123dive.oneremote.core.RouteResult
import `in`.s123dive.oneremote.core.SendResult
import `in`.s123dive.oneremote.core.TransportKind
import `in`.s123dive.oneremote.devices.DeviceConfig
import `in`.s123dive.oneremote.ir.codesets.LgNecCodeset
import `in`.s123dive.oneremote.lg.LgClient
import `in`.s123dive.oneremote.lg.LgTransport
import `in`.s123dive.oneremote.ui.KeyValue
import `in`.s123dive.oneremote.ui.SectionCard
import `in`.s123dive.oneremote.ui.appGraph
import kotlinx.coroutines.launch

/**
 * Creates the LG instance (Wi-Fi first, IR fallback with the LG NEC code set) and runs the SSAP
 * pairing: the TV shows a prompt, the user accepts, the client-key is persisted by the driver.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LgPairScreen(ip: String, suggestedName: String?, roomId: String?, existingId: String?, onBack: () -> Unit, onDone: (String) -> Unit) {
    val graph = appGraph()
    val vm: SetupViewModel = viewModel { SetupViewModel(graph) }
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(existingId?.let { state.device(it)?.name } ?: suggestedName ?: "LG TV") }
    var instanceId by remember { mutableStateOf(existingId) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var diagBusy by remember { mutableStateOf(false) }
    var diag by remember { mutableStateOf<List<String>>(emptyList()) }
    val client = instanceId?.let { id -> state.device(id)?.let { graph.registry.get(it) } }?.transports?.filterIsInstance<LgTransport>()?.firstOrNull()?.client
    val clientState by (client?.state ?: remember { kotlinx.coroutines.flow.MutableStateFlow<LgClient.State>(LgClient.State.Disconnected) }).collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Pair LG TV") }, navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            })
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
            KeyValue("IP", ip)
            SectionCard("Pairing") {
                Text("The TV will show \"Allow this remote to connect?\". Choose Yes with the TV remote. If nothing appears, check the TV: Settings → Network (or General → Devices → External devices) → \"LG Connect Apps\" / \"Mobile Connection\" must be On. For power-on over Wi-Fi also enable \"Turn on via Wi-Fi\" / \"TV On with Mobile\".", style = MaterialTheme.typography.bodySmall)
                when (val s = clientState) {
                    LgClient.State.Connecting -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Connecting (ws:3000, then wss:3001)…") }
                    LgClient.State.WaitingForPrompt -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Accept the prompt on the TV now.", color = MaterialTheme.colorScheme.tertiary) }
                    is LgClient.State.Connected -> {
                        Text("Paired.", color = MaterialTheme.colorScheme.tertiary)
                        s.info.modelName?.let { KeyValue("Model", it) }
                        s.info.webosVersion?.let { KeyValue("webOS", it) }
                        s.info.firmware?.let { KeyValue("Firmware", it) }
                        KeyValue("Port", if (s.info.secure) "wss:3001" else "ws:3000")
                        KeyValue("MAC", s.info.macAddress ?: "unknown – enter it in device settings for Wake-on-LAN", highlight = s.info.macAddress == null)
                    }
                    is LgClient.State.Failed -> Text(s.reason, color = MaterialTheme.colorScheme.error)
                    LgClient.State.Disconnected -> {}
                }
                status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Button(
                    modifier = Modifier.fillMaxWidth(), enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            val id = instanceId ?: vm.addDevice(
                                DeviceType.LG_WEBOS, name.ifBlank { "LG TV" }, roomId, listOf(TransportKind.WIFI, TransportKind.IR),
                                DeviceConfig.empty().with(DeviceConfig.IP, ip).with(DeviceConfig.CODESET_ID, LgNecCodeset.ID),
                            ).also { instanceId = it }
                            if (existingId != null) { vm.rename(id, name); vm.updateConfig(id) { it.with(DeviceConfig.IP, ip) } }
                            val inst = vm.state.value.device(id)
                            val transport = inst?.let { graph.registry.get(it) }?.transports?.filterIsInstance<LgTransport>()?.firstOrNull()
                            if (transport == null) { status = "No Wi-Fi driver for this instance"; busy = false; return@launch }
                            transport.client.resetBreaker()
                            val r = graph.router.diagnostic("LG pair $ip", TransportKind.WIFI, "ssap register") {
                                when (val c = transport.client.connect()) {
                                    is LgClient.Result.Ok -> SendResult.Ok("registered; webOS ${c.value.webosVersion ?: "?"}")
                                    is LgClient.Result.Err -> SendResult.Failed("ssap register", c.reason)
                                }
                            }
                            status = when (r) { is RouteResult.Sent -> "Paired in ${r.ms} ms"; is RouteResult.Failed -> r.reason; is RouteResult.Unsupported -> r.reason }
                            busy = false
                        }
                    },
                ) { Text(if (clientState is LgClient.State.Connected) "Pair again" else "Pair") }
                if (instanceId != null) Button(modifier = Modifier.fillMaxWidth(), onClick = { onDone(instanceId!!) }) { Text("Done") }
            }
            SectionCard("Diagnose") {
                Text("Probes TCP, HTTP, TLS and the WebSocket upgrade on ports 3000/3001 and writes each line to the Debug console.", style = MaterialTheme.typography.bodySmall)
                androidx.compose.material3.OutlinedButton(enabled = !diagBusy, onClick = {
                    diagBusy = true; diag = emptyList()
                    scope.launch {
                        val lines = `in`.s123dive.oneremote.lg.LgDiagnostics.run(ip)
                        lines.forEach { line -> graph.log.add(`in`.s123dive.oneremote.core.LogEntry(System.currentTimeMillis(), "-", "DEBUG", "lg diag $ip", "Wi-Fi", line.substringBefore(':'), line.substringAfter(": "), 0)) }
                        diag = lines; diagBusy = false
                    }
                }) { Text(if (diagBusy) "Probing…" else "Diagnose connection") }
                diag.forEach { Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace) }
            }
        }
    }
}
