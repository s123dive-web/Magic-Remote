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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import `in`.s123dive.oneremote.devices.DeviceConfig
import `in`.s123dive.oneremote.ir.codesets.Codesets
import `in`.s123dive.oneremote.ui.KeyValue
import `in`.s123dive.oneremote.ui.SectionCard
import `in`.s123dive.oneremote.ui.appGraph
import kotlinx.coroutines.launch

/** Device info + per-device settings: name, code set, digit delay, remove. Wi-Fi details are added per driver. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSettingsScreen(
    instanceId: String,
    onBack: () -> Unit,
    onExpert: (DeviceType) -> Unit,
    onKeyFinder: () -> Unit = {},
    onRemoved: () -> Unit,
    wifiSection: @Composable (SetupViewModel, String) -> Unit = { _, _ -> },
) {
    val graph = appGraph()
    val vm: SetupViewModel = viewModel { SetupViewModel(graph) }
    val state by vm.state.collectAsStateWithLifecycle()
    val caps by vm.capabilities.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val instance = state.device(instanceId)
    val config = instance?.let { DeviceConfig(it.config) }
    var name by remember(instance?.name) { mutableStateOf(instance?.name ?: "") }
    var delay by remember(config?.digitDelayMs) { mutableStateOf((config?.digitDelayMs ?: 250).toString()) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Device") }, navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            })
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (instance == null || config == null) { Text("Device not found."); return@Column }
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
            SectionCard("Device info") {
                KeyValue("Type", instance.type.displayName)
                KeyValue("Role", SetupViewModel.roleOf(state, instance.id))
                KeyValue("Room", state.room(instance.roomId)?.name ?: instance.roomId)
                KeyValue("Transports", instance.transports.joinToString(" → ") { it.badge })
                val builtIn = config.codesetId?.let { Codesets.byId(it) }
                val effective = config.effectiveCodeset(builtIn, instance.name, instance.type)
                KeyValue("IR code set", effective?.name ?: "none", highlight = effective == null)
                effective?.let { KeyValue("Code set id", it.id); KeyValue("Source", it.source); KeyValue("Keys", "${it.codes.size}") }
                if (!caps.ir.hasIr && instance.transports.contains(`in`.s123dive.oneremote.core.TransportKind.IR)) {
                    Text("IR transport unavailable: this phone has no IR blaster.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                config.model?.let { KeyValue("Model", it) }
                config.firmware?.let { KeyValue("Firmware", it) }
                config.webosVersion?.let { KeyValue("webOS", it) }
                config.ip?.let { KeyValue("IP", it) }
                (instance.mac ?: config.mac)?.let { KeyValue("MAC", it) }
            }
            wifiSection(vm, instanceId)
            SectionCard("IR") {
                OutlinedTextField(value = delay, onValueChange = { delay = it.filter(Char::isDigit) }, label = { Text("Delay between digits (ms)") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onExpert(instance.type) }) { Text("Expert mode") }
                    if (config.customCodes.containsKey(`in`.s123dive.oneremote.core.Command.Power.key)) OutlinedButton(onClick = onKeyFinder) { Text("Find more keys") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    scope.launch {
                        vm.rename(instanceId, name.ifBlank { instance.name })
                        vm.updateConfig(instanceId) { c -> delay.toIntOrNull()?.let { c.with(DeviceConfig.DIGIT_DELAY_MS, it) } ?: c }
                        onBack()
                    }
                }) { Text("Save") }
                OutlinedButton(onClick = { scope.launch { vm.removeDevice(instanceId); onRemoved() } }) { Text("Remove device") }
            }
        }
    }
}
