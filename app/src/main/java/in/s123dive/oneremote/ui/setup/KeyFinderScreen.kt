package `in`.s123dive.oneremote.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import `in`.s123dive.oneremote.core.Command
import `in`.s123dive.oneremote.devices.DeviceConfig
import `in`.s123dive.oneremote.ir.CodesetScanner
import `in`.s123dive.oneremote.ir.NecSweep
import `in`.s123dive.oneremote.ui.KeyValue
import `in`.s123dive.oneremote.ui.SectionCard
import `in`.s123dive.oneremote.ui.appGraph
import kotlinx.coroutines.launch

/**
 * Once Power is known, every other key lives on the same NEC address with an unknown command
 * byte. Pick a key, sweep the 256 commands (about 80 s), tap when the box reacts, confirm,
 * saved. Repeat for the keys you use.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyFinderScreen(instanceId: String, onBack: () -> Unit, onDone: () -> Unit) {
    val graph = appGraph()
    val vm: SetupViewModel = viewModel { SetupViewModel(graph) }
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val instance = state.device(instanceId)
    val config = instance?.let { DeviceConfig(it.config) }
    val known = config?.customCodes ?: emptyMap()
    val builtIn = config?.codesetId?.let { `in`.s123dive.oneremote.ir.codesets.Codesets.byId(it) }
    val power = known[Command.Power.key] ?: builtIn?.code(Command.Power)
    var key by remember { mutableStateOf<Command>(Command.VolUp) }
    val assigned = remember(known) { known.values.mapNotNull { NecSweep.commandOf(it) }.toSet() }
    val candidates = remember(power, assigned) { if (power == null) emptyList() else NecSweep.commandSweepFor(power, assigned) }
    val scanner = remember(candidates, key) { CodesetScanner(candidates, send = { c -> graph.sendIrDiagnostic("keyfinder ${key.key} ${c.label}", c.code) }, intervalMs = 320) }
    val scan by scanner.state.collectAsStateWithLifecycle()
    var verifying by remember { mutableStateOf<List<NecSweep.Candidate>?>(null) }
    DisposableEffect(scanner) { onDispose { scanner.pause() } }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Find keys") }, navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            })
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (instance == null || power == null) { Text("This device has no Power code yet. Run the Power sweep first."); return@Column }
            KeyValue("Device", instance.name)
            KeyValue("Protocol / address", power.describe().substringBefore('/'))
            KeyValue("Keys found", "${known.size}: " + known.keys.joinToString(", "))
            builtIn?.let { KeyValue("Built-in code set", it.name) ; Text("Found keys override the built-in ones; use this for any key that does nothing on your model.", style = MaterialTheme.typography.bodySmall) }
            SectionCard("1. Pick the key to find") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(overrideKeysFor(instance.type).filter { it != Command.Power }, key = { it.key }) { c ->
                        FilterChip(selected = key == c, enabled = !scan.running, onClick = { key = c }, label = { Text(c.key + if (known.containsKey(c.key)) " ✓" else if (builtIn?.code(c) != null) " ·" else "") })
                    }
                }
            }
            SectionCard("2. Sweep ${key.key} (${candidates.size} codes, ~${candidates.size * 320 / 1000} s)") {
                Text("Watch the box. Tap \"It reacted!\" as soon as it does what ${key.key} should do (for digits: a number appears on screen).", style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(progress = { scan.progress }, modifier = Modifier.fillMaxWidth())
                Text(scanner.current?.let { "${scan.index + 1} / ${scan.total}: ${it.label}" } ?: "Sweep finished without a tap.")
                scan.lastResult?.takeIf { !it.startsWith("sent") }?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!scan.running) Button(onClick = { if (scan.finished) scanner.seek(0); scanner.start(scope) }) { Text(if (scan.index == 0 && !scan.finished) "Start" else "Resume") }
                    else OutlinedButton(onClick = { scanner.pause() }) { Text("Pause") }
                    OutlinedButton(onClick = { scanner.seek(scan.index - 1) }, enabled = scan.index > 0) { Text("Back one") }
                }
                Button(modifier = Modifier.fillMaxWidth().height(64.dp), enabled = scanner.current != null, onClick = { scanner.lock(); verifying = scanner.recent(4) }) {
                    Text("It reacted!", style = MaterialTheme.typography.titleMedium)
                }
            }
            Button(modifier = Modifier.fillMaxWidth(), onClick = onDone) { Text("Done for now (open the remote)") }
        }
    }
    verifying?.let { recent ->
        VerifyDialog(graph, "keyfinder ${key.key}", recent, onConfirmed = { c ->
            verifying = null
            scope.launch { vm.setCustomCodes(instanceId, known + (key.key to c.code)) }
        }, onNone = { verifying = null })
    }
}
