package `in`.s123dive.oneremote.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.s123dive.oneremote.core.Command
import `in`.s123dive.oneremote.core.DeviceType
import `in`.s123dive.oneremote.core.RouteResult
import `in`.s123dive.oneremote.devices.DeviceConfig
import `in`.s123dive.oneremote.ir.IrCode
import `in`.s123dive.oneremote.ir.codesets.Codesets
import `in`.s123dive.oneremote.ui.appGraph
import kotlinx.coroutines.launch

/** The keys an override screen lists for a device type. */
fun overrideKeysFor(type: DeviceType): List<Command> = when (type) {
    DeviceType.ATOMBERG_FAN -> listOf(Command.Power) + (1..5).map { Command.FanSpeed(it) } + listOf(Command.Boost, Command.Timer, Command.Sleep, Command.Light)
    else -> EXPERT_COMMANDS
}

/**
 * Parses what a user pastes for one key. Accepts `91`, `0x91`, `F300/91`, `0xF300 0x91`,
 * `F3 00 91` (Flipper order is not assumed: the first value is the address when two are given).
 * With one byte the device's default address is used.
 */
fun parseOverride(text: String, defaultAddress: Int?, defaultExtended: Boolean): IrCode.Nec? {
    val parts = text.trim().replace("/", " ").replace(",", " ").replace("0x", "", ignoreCase = true).split(Regex("\\s+")).filter { it.isNotBlank() }
    if (parts.isEmpty()) return null
    return runCatching {
        when (parts.size) {
            1 -> { val cmd = parts[0].toInt(16); val a = defaultAddress ?: return null; IrCode.Nec(a, cmd, defaultExtended).takeIf { cmd in 0..0xFF } }
            2 -> { val a = parts[0].toInt(16); val cmd = parts[1].toInt(16); IrCode.Nec(a, cmd, extended = a > 0xFF || defaultExtended && parts[0].length > 2).takeIf { cmd in 0..0xFF && a in 0..0xFFFF } }
            3 -> { val a = (parts[0].toInt(16) shl 8) or parts[1].toInt(16); val cmd = parts[2].toInt(16); IrCode.Nec(a, cmd, extended = true).takeIf { cmd in 0..0xFF } }
            else -> null
        }
    }.getOrNull()
}

/**
 * "Learn / Override codes": the default code per key is shown; a pasted NEC code overrides it
 * (override wins, persisted in the device config); "Reset" returns to the default.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverrideCodesScreen(instanceId: String, onBack: () -> Unit) {
    val graph = appGraph()
    val vm: SetupViewModel = viewModel { SetupViewModel(graph) }
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val instance = state.device(instanceId)
    val config = instance?.let { DeviceConfig(it.config) }
    val builtIn = config?.codesetId?.let { Codesets.byId(it) }
    val overrides = config?.customCodes ?: emptyMap()
    val defaultNec = builtIn?.codes?.values?.filterIsInstance<IrCode.Nec>()?.firstOrNull()
    val drafts = remember { mutableStateMapOf<String, String>() }
    var status by remember { mutableStateOf<String?>(null) }

    fun test(code: IrCode) = scope.launch {
        val r = graph.sendIrDiagnostic("override test", code)
        status = when (r) { is `in`.s123dive.oneremote.core.SendResult.Ok -> "Sent ${r.payload}"; is `in`.s123dive.oneremote.core.SendResult.Failed -> "Failed: ${r.reason}"; is `in`.s123dive.oneremote.core.SendResult.Unsupported -> r.reason }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Learn / override codes") }, navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            })
        },
    ) { padding ->
        if (instance == null) { Text("Device not found.", Modifier.padding(padding).padding(16.dp)); return@Scaffold }
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${instance.name} · ${builtIn?.name ?: "no built-in code set"}", style = MaterialTheme.typography.titleMedium)
                    Text("Paste a NEC code per key when the default does not work on your unit: a command byte like 91 uses the default address${defaultNec?.let { " (0x%X)".format(it.address) } ?: ""}; F300/91 sets both. Test sends it once; Save keeps it; Reset returns to the default. The key finder (device settings) can discover a code instead.", style = MaterialTheme.typography.bodySmall)
                    status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (it.startsWith("Sent")) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error) }
                }
            }
            items(overrideKeysFor(instance.type), key = { it.key }) { cmd ->
                val default = builtIn?.code(cmd)
                val override = overrides[cmd.key]
                val draft = drafts[cmd.key] ?: ""
                val parsed = parseOverride(draft, defaultNec?.address, defaultNec?.extended ?: false)
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(cmd.key, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            when { override != null -> "override: ${override.describe()}"; default != null -> "default: ${default.describe()}"; else -> "no code" },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (override != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(value = draft, onValueChange = { drafts[cmd.key] = it }, label = { Text("NEC hex") }, modifier = Modifier.width(130.dp), singleLine = true, isError = draft.isNotBlank() && parsed == null)
                        OutlinedButton(enabled = parsed != null, onClick = { parsed?.let { test(it) } }) { Text("Test") }
                        OutlinedButton(enabled = parsed != null, onClick = {
                            scope.launch { vm.setCustomCodes(instanceId, overrides + (cmd.key to parsed!!)); drafts.remove(cmd.key); status = "Saved ${cmd.key} = ${parsed.describe()}" }
                        }) { Text("Save") }
                        TextButton(enabled = override != null, onClick = {
                            scope.launch { vm.setCustomCodes(instanceId, overrides - cmd.key); status = "${cmd.key} reset to default" }
                        }) { Text("Reset") }
                        (override ?: default)?.let { TextButton(onClick = { test(it) }) { Text("Send") } }
                    }
                }
            }
            item { Text("Overrides are stored with the device (and included in backups).", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 12.dp)) }
        }
    }
}
