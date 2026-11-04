package `in`.s123dive.oneremote.ui.setup

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import `in`.s123dive.oneremote.core.TransportKind
import `in`.s123dive.oneremote.core.TransportKind.IR
import `in`.s123dive.oneremote.core.SendResult
import `in`.s123dive.oneremote.devices.DeviceConfig
import `in`.s123dive.oneremote.ir.CodesetScanner
import `in`.s123dive.oneremote.ir.codesets.Codesets
import `in`.s123dive.oneremote.ui.SectionCard
import `in`.s123dive.oneremote.ui.appGraph
import kotlinx.coroutines.launch

/**
 * "Point the phone at the box; tap when it turns on/off." Sends Power from each candidate every
 * second. Locking creates the instance. Every send is logged through the Router as a DEBUG entry.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodesetScanScreen(type: DeviceType, roomId: String?, onBack: () -> Unit, onDone: (instanceId: String) -> Unit, onExpert: () -> Unit) {
    val graph = appGraph()
    val vm: SetupViewModel = viewModel { SetupViewModel(graph) }
    val caps by vm.capabilities.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val candidates = remember(type) { Codesets.scanCandidates(type) }
    val scanner = remember(type) {
        CodesetScanner(candidates, send = { cs, code ->
            val r = graph.router.diagnostic("scan ${cs.id}", IR, code.describe()) { graph.ir.send(code.carrierHz, code.pattern(), code.describe()) }
            when (r) {
                is `in`.s123dive.oneremote.core.RouteResult.Sent -> SendResult.Ok(r.payload)
                is `in`.s123dive.oneremote.core.RouteResult.Failed -> SendResult.Failed(code.describe(), r.reason)
                is `in`.s123dive.oneremote.core.RouteResult.Unsupported -> SendResult.Unsupported(r.reason)
            }
        })
    }
    val state by scanner.state.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf(if (type == DeviceType.AIRTEL_HD) "Airtel box" else type.displayName) }
    DisposableEffect(scanner) { onDispose { scanner.pause() } }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Find code set") }, navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            })
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!caps.ir.hasIr) {
                Text("This phone has no IR blaster, so an IR-only device cannot be set up here.", color = MaterialTheme.colorScheme.error)
                return@Column
            }
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Device name") }, modifier = Modifier.fillMaxWidth())
            SectionCard("Scanning") {
                Text("Point the phone at the box. Each candidate's Power code is sent once a second. Tap \"It responded\" the moment the box turns on or off.", style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                val current = candidates.getOrNull(state.index)
                Text(if (current != null) "Candidate ${state.index + 1} of ${state.total}: ${current.name}" else "All candidates tried.")
                current?.let { Text(it.source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                state.lastResult?.let { Text("Last: $it", style = MaterialTheme.typography.bodySmall) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!state.running) Button(onClick = { if (state.finished) scanner.seek(0); scanner.start(scope) }) { Text(if (state.index == 0 && !state.finished) "Start" else "Resume") }
                    else OutlinedButton(onClick = { scanner.pause() }) { Text("Pause") }
                    OutlinedButton(onClick = { scanner.seek(state.index + 1) }, enabled = !state.finished) { Text("Skip") }
                    OutlinedButton(onClick = { scanner.seek(state.index - 1) }, enabled = state.index > 0) { Text("Back one") }
                }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = current != null,
                    onClick = {
                        val cs = scanner.lock() ?: return@Button
                        scope.launch {
                            val id = vm.addDevice(type, name.ifBlank { cs.name }, roomId, listOf(TransportKind.IR), DeviceConfig.empty().with(DeviceConfig.CODESET_ID, cs.id))
                            onDone(id)
                        }
                    },
                ) { Text("It responded!") }
            }
            SectionCard("Nothing works?") {
                Text("Expert mode lets you type NEC address + command bytes and test them live; the codes you confirm become this device's code set.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { scanner.pause(); onExpert() }) { Text("Open expert mode") }
            }
        }
    }
}
