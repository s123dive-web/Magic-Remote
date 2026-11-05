package `in`.s123dive.oneremote.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import `in`.s123dive.oneremote.core.Command
import `in`.s123dive.oneremote.core.DeviceType
import `in`.s123dive.oneremote.core.TransportKind
import `in`.s123dive.oneremote.devices.DeviceConfig
import `in`.s123dive.oneremote.ir.CodesetScanner
import `in`.s123dive.oneremote.ir.IrCode
import `in`.s123dive.oneremote.ir.NecSweep
import `in`.s123dive.oneremote.ir.codesets.Codesets
import `in`.s123dive.oneremote.ui.SectionCard
import `in`.s123dive.oneremote.ui.appGraph
import kotlinx.coroutines.launch

private enum class Mode(val title: String, val intervalMs: Long) { KNOWN("Known code sets", 1200), SWEEP("Deep Power sweep", 350) }

/**
 * Finds the box's code set without an IR receiver: first the known code sets (one Power code
 * every 1.2 s), then, if none works, a sweep of 6,000+ NEC address/command pairs at 350 ms.
 * Tapping "It reacted!" opens a verify step that replays the last four codes one by one.
 * A confirmed sweep hit stores the NEC address and sends the user to the key finder.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodesetScanScreen(type: DeviceType, roomId: String?, onBack: () -> Unit, onDone: (instanceId: String) -> Unit, onKeyFinder: (instanceId: String) -> Unit, onExpert: () -> Unit) {
    val graph = appGraph()
    val vm: SetupViewModel = viewModel { SetupViewModel(graph) }
    val caps by vm.capabilities.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(Mode.KNOWN) }
    val candidates = remember(type, mode) { if (mode == Mode.KNOWN) CodesetScanner.fromCodesets(Codesets.scanCandidates(type)) else NecSweep.powerSweep() }
    val scanner = remember(candidates) { CodesetScanner(candidates, send = { c -> graph.sendIrDiagnostic("scan ${c.label}", c.code) }, intervalMs = mode.intervalMs) }
    val state by scanner.state.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf(if (type == DeviceType.AIRTEL_HD) "Airtel box" else type.displayName) }
    var verifying by remember { mutableStateOf<List<NecSweep.Candidate>?>(null) }
    DisposableEffect(scanner) { onDispose { scanner.pause() } }

    fun save(c: NecSweep.Candidate) {
        scope.launch {
            if (c.codesetId != null) {
                val id = vm.addDevice(type, name.ifBlank { c.label }, roomId, listOf(TransportKind.IR), DeviceConfig.empty().with(DeviceConfig.CODESET_ID, c.codesetId))
                onDone(id)
            } else {
                val nec = c.code as IrCode.Nec
                val cfg = DeviceConfig.empty().with(DeviceConfig.NEC_ADDRESS, nec.address).with(DeviceConfig.NEC_EXTENDED, nec.extended)
                    .withCustomCodes(mapOf(Command.Power.key to nec))
                val id = vm.addDevice(type, name.ifBlank { "Airtel box" }, roomId, listOf(TransportKind.IR), cfg)
                onKeyFinder(id)
            }
        }
    }

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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Mode.entries.forEach { m -> FilterChip(selected = mode == m, onClick = { if (!state.running) mode = m }, label = { Text(m.title) }) }
            }
            SectionCard(if (mode == Mode.KNOWN) "Known code sets (${candidates.size})" else "Deep sweep (${candidates.size} codes, about ${candidates.size * mode.intervalMs / 60000} min)") {
                Text(
                    if (mode == Mode.KNOWN) "Point the phone at the box, 1–2 m away, with a clear line of sight. Each known code set's Power code is sent every ${mode.intervalMs / 1000.0} s. Watch the BOX (its light or the picture it sends), not the TV: the TV reacting to a code means that code is the TV's, not the box's. Tap \"It reacted!\" the moment the box turns on or off; the app then replays the last codes to confirm which one it was."
                    else "Tries every common NEC address and Power command. Keep the phone pointed at the box and tap \"It reacted!\" when it turns on or off. Pause any time; the position is kept.",
                    style = MaterialTheme.typography.bodySmall,
                )
                LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                val current = scanner.current
                Text(if (current != null) "${state.index + 1} / ${state.total}: ${current.label}" + (if (state.running) " · ~${scanner.etaSeconds() / 60} min left" else "") else "All candidates tried.")
                state.lastResult?.let { Text("Last: $it", style = MaterialTheme.typography.bodySmall, color = if (it.startsWith("sent")) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!state.running) Button(onClick = { if (state.finished) scanner.seek(0); scanner.start(scope) }) { Text(if (state.index == 0 && !state.finished) "Start" else "Resume") }
                    else OutlinedButton(onClick = { scanner.pause() }) { Text("Pause") }
                    OutlinedButton(onClick = { scanner.seek(state.index + 1) }, enabled = !state.finished) { Text("Skip") }
                    OutlinedButton(onClick = { scanner.seek(state.index - 1) }, enabled = state.index > 0) { Text("Back one") }
                }
                Button(
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    enabled = current != null,
                    onClick = { scanner.lock(); verifying = scanner.recent(4) },
                ) { Text("It reacted!", style = MaterialTheme.typography.titleMedium) }
            }
            SectionCard("Nothing works?") {
                Text("Expert mode lets you type NEC address + command bytes and test them live.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { scanner.pause(); onExpert() }) { Text("Open expert mode") }
            }
        }
    }
    verifying?.let { recent ->
        VerifyDialog(graph, "scan", recent, onConfirmed = { verifying = null; save(it) }, onNone = { verifying = null })
    }
}
