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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import `in`.s123dive.oneremote.core.TransportKind
import `in`.s123dive.oneremote.devices.DeviceConfig
import `in`.s123dive.oneremote.ir.IrCode
import `in`.s123dive.oneremote.ui.appGraph
import kotlinx.coroutines.launch

/** The commands an STB / IR remote can carry, in the order shown in expert mode. */
val EXPERT_COMMANDS: List<Command> = listOf(
    Command.Power, Command.Mute, Command.VolUp, Command.VolDown, Command.ChUp, Command.ChDown,
) + (0..9).map { Command.Digit(it) } + listOf(
    Command.Ok, Command.Up, Command.Down, Command.Left, Command.Right, Command.Back, Command.Exit, Command.Menu,
    Command.Guide, Command.Info, Command.Home, Command.Input, Command.Play, Command.Pause, Command.Stop, Command.Rew, Command.Fwd,
    Command.Red, Command.Green, Command.Yellow, Command.Blue, Command.PowerOn, Command.PowerOff,
)

/**
 * Expert mode: type a NEC address once, then a command byte per key and test each live.
 * Saving stores the table as custom codes on a new or existing instance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpertCodesScreen(type: DeviceType, roomId: String?, instanceId: String?, onBack: () -> Unit, onDone: (String) -> Unit) {
    val graph = appGraph()
    val vm: SetupViewModel = viewModel { SetupViewModel(graph) }
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val existing = remember(instanceId, state) { instanceId?.let { id -> state.device(id) }?.let { DeviceConfig(it.config).customCodes } ?: emptyMap() }
    val firstNec = existing.values.filterIsInstance<IrCode.Nec>().firstOrNull()
    var address by remember { mutableStateOf(firstNec?.address?.let { "%X".format(it) } ?: "") }
    var extended by remember { mutableStateOf(firstNec?.extended ?: false) }
    var name by remember { mutableStateOf(instanceId?.let { id -> state.device(id)?.name } ?: "Airtel box (expert)") }
    val codes = remember { mutableStateMapOf<String, String>().apply { existing.forEach { (k, v) -> if (v is IrCode.Nec) put(k, "%02X".format(v.command)) } } }
    var lastResult by remember { mutableStateOf<String?>(null) }

    fun parseAddress(): Int? = address.trim().removePrefix("0x").removePrefix("0X").toIntOrNull(16)?.takeIf { it in 0..(if (extended) 0xFFFF else 0xFF) }
    fun code(cmd: Command): IrCode.Nec? {
        val a = parseAddress() ?: return null
        val c = codes[cmd.key]?.trim()?.removePrefix("0x")?.toIntOrNull(16)?.takeIf { it in 0..0xFF } ?: return null
        return IrCode.Nec(a, c, extended)
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Expert mode") }, navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            })
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("NEC codes only (38 kHz). Address is hex (e.g. 04, or A1DE with Extended on); each command is one hex byte. Test sends the code once.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Device name") }, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("NEC address (hex)") }, modifier = Modifier.width(200.dp), isError = address.isNotBlank() && parseAddress() == null)
                        Text("Extended"); Switch(checked = extended, onCheckedChange = { extended = it })
                    }
                    lastResult?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (it.startsWith("sent")) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error) }
                }
            }
            items(EXPERT_COMMANDS, key = { it.key }) { cmd ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(cmd.key, Modifier.width(110.dp), style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(value = codes[cmd.key] ?: "", onValueChange = { codes[cmd.key] = it }, label = { Text("cmd hex") }, modifier = Modifier.width(120.dp), singleLine = true)
                    OutlinedButton(enabled = code(cmd) != null, onClick = {
                        val c = code(cmd) ?: return@OutlinedButton
                        scope.launch {
                            val r = graph.router.diagnostic("expert ${cmd.key}", TransportKind.IR, c.describe()) { graph.ir.send(c.carrierHz, c.pattern(), c.describe()) }
                            lastResult = when (r) {
                                is RouteResult.Sent -> "sent ${r.payload} in ${r.ms} ms"
                                is RouteResult.Failed -> "failed: ${r.reason}"
                                is RouteResult.Unsupported -> "unsupported: ${r.reason}"
                            }
                        }
                    }) { Text("Test") }
                }
            }
            item {
                val table = EXPERT_COMMANDS.mapNotNull { cmd -> code(cmd)?.let { cmd.key to it } }.toMap()
                Button(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    enabled = table.containsKey(Command.Power.key),
                    onClick = {
                        scope.launch {
                            val id = instanceId ?: vm.addDevice(type, name, roomId, listOf(TransportKind.IR), DeviceConfig.empty())
                            if (instanceId != null) vm.rename(id, name)
                            vm.setCustomCodes(id, table)
                            onDone(id)
                        }
                    },
                ) { Text("Save ${table.size} code(s)") }
            }
        }
    }
}
