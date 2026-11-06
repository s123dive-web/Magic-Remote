package `in`.s123dive.oneremote.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import `in`.s123dive.oneremote.core.SendResult
import `in`.s123dive.oneremote.core.TransportKind
import `in`.s123dive.oneremote.devices.DeviceConfig
import `in`.s123dive.oneremote.ir.CodeText
import `in`.s123dive.oneremote.ir.CodesetScanner
import `in`.s123dive.oneremote.ir.IrCode
import `in`.s123dive.oneremote.ir.NecSweep
import `in`.s123dive.oneremote.ui.SectionCard
import `in`.s123dive.oneremote.ui.appGraph
import kotlinx.coroutines.launch

/** The commands an STB / IR remote can carry, in the order shown in expert mode and the key finder. */
val EXPERT_COMMANDS: List<Command> = listOf(
    Command.Power, Command.Mute, Command.VolUp, Command.VolDown, Command.ChUp, Command.ChDown,
) + (0..9).map { Command.Digit(it) } + listOf(
    Command.Ok, Command.Up, Command.Down, Command.Left, Command.Right, Command.Back, Command.Exit, Command.Menu,
    Command.Guide, Command.Info, Command.Home, Command.Input, Command.ChannelList, Command.Fav, Command.Prev, Command.Record,
    Command.Play, Command.Pause, Command.Stop, Command.Rew, Command.Fwd, Command.Subtitle, Command.Audio, Command.Sleep, Command.Help,
    Command.Red, Command.Green, Command.Yellow, Command.Blue, Command.PowerOn, Command.PowerOff,
)

private enum class Proto(val title: String) { NEC("NEC"), NECX("NEC ext"), NOKIA32("Nokia32"), RC5("RC5"), RC6("RC6"), SIRC12("SIRC 12"), SIRC15("SIRC 15"), SIRC20("SIRC 20"), SAMSUNG("Samsung32"), RAW("Raw µs"), PRONTO("Pronto hex"), FLIPPER("Flipper .ir") }

/**
 * Expert mode, a code lab: build a code in any supported protocol, or paste Pronto hex / a
 * Flipper `.ir` block, send it, sweep the command space from it, and assign whatever works to a
 * key. Saves into the device's custom codes (creating the device when there is none yet).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpertCodesScreen(type: DeviceType, roomId: String?, instanceId: String?, onBack: () -> Unit, onDone: (String) -> Unit) {
    val graph = appGraph()
    val vm: SetupViewModel = viewModel { SetupViewModel(graph) }
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var savedId by remember { mutableStateOf(instanceId) }
    val existing = savedId?.let { state.device(it) }?.let { DeviceConfig(it.config).customCodes } ?: emptyMap()
    var name by remember { mutableStateOf(savedId?.let { state.device(it)?.name } ?: "${type.displayName} (expert)") }
    var proto by remember { mutableStateOf(Proto.NEC) }
    var f1 by remember { mutableStateOf("") }   // address / device / carrier
    var f2 by remember { mutableStateOf("") }   // command / sub / data
    var f3 by remember { mutableStateOf("") }   // x / ext
    var f4 by remember { mutableStateOf("") }   // function
    var paste by remember { mutableStateOf("") }
    var hexFields by remember { mutableStateOf(true) }
    var key by remember { mutableStateOf<Command>(Command.Power) }
    var status by remember { mutableStateOf<String?>(null) }
    var sweeping by remember { mutableStateOf<CodesetScanner?>(null) }
    var verifying by remember { mutableStateOf<List<NecSweep.Candidate>?>(null) }
    DisposableEffect(Unit) { onDispose { sweeping?.pause() } }

    fun num(s: String): Int? = s.trim().removePrefix("0x").removePrefix("0X").let { if (hexFields) it.toIntOrNull(16) else it.toIntOrNull() }
    fun built(): IrCode? = runCatching {
        when (proto) {
            Proto.NEC -> IrCode.Nec(num(f1) ?: return null, num(f2) ?: return null, extended = false)
            Proto.NECX -> IrCode.Nec(num(f1) ?: return null, num(f2) ?: return null, extended = true)
            Proto.NOKIA32 -> IrCode.Nokia32(num(f1) ?: return null, num(f2) ?: 0, num(f3) ?: (num(f1) ?: return null) and 0x7F, num(f4) ?: return null)
            Proto.RC5 -> IrCode.Rc5(num(f1) ?: return null, num(f2) ?: return null)
            Proto.RC6 -> IrCode.Rc6(num(f1) ?: return null, num(f2) ?: return null)
            Proto.SIRC12 -> IrCode.Sirc(num(f2) ?: return null, num(f1) ?: return null, 12)
            Proto.SIRC15 -> IrCode.Sirc(num(f2) ?: return null, num(f1) ?: return null, 15)
            Proto.SIRC20 -> IrCode.Sirc(num(f2) ?: return null, num(f1) ?: return null, 20, num(f3) ?: 0)
            Proto.SAMSUNG -> IrCode.Samsung32(num(f1) ?: return null, num(f2) ?: return null)
            Proto.RAW -> {
                val hz = (f1.trim().toDoubleOrNull() ?: return null).let { if (it < 1000) it * 1000 else it }.toInt()
                val us = f2.split(Regex("[\\s,]+")).mapNotNull { it.toIntOrNull() }.let { if (it.size % 2 == 0) it.dropLast(1) else it }
                if (us.isEmpty()) null else IrCode.Raw(hz, us, "raw")
            }
            Proto.PRONTO -> CodeText.parsePronto(paste)
            Proto.FLIPPER -> CodeText.parseFlipper(paste)?.second
        }
    }.getOrNull()
    val code = built()

    fun send(c: IrCode) = scope.launch {
        val r = graph.sendIrDiagnostic("expert ${c.describe()}", c)
        status = when (r) { is SendResult.Ok -> "Sent ${r.payload}"; is SendResult.Failed -> "Failed: ${r.reason}"; is SendResult.Unsupported -> r.reason }
    }
    suspend fun ensureInstance(): String = savedId ?: vm.addDevice(type, name, roomId, listOf(TransportKind.IR), DeviceConfig.empty()).also { savedId = it }
    fun assign(c: IrCode, cmd: Command) = scope.launch {
        val id = ensureInstance()
        vm.setCustomCodes(id, (state.device(id)?.let { DeviceConfig(it.config).customCodes } ?: emptyMap()) + (cmd.key to c))
        status = "Saved ${cmd.key} = ${c.describe()}"
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Expert mode") }, navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            })
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Device name") }, modifier = Modifier.fillMaxWidth())
            SectionCard("1. Build or paste a code") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(Proto.entries, key = { it.name }) { p -> FilterChip(selected = proto == p, onClick = { proto = p }, label = { Text(p.title) }) }
                }
                when (proto) {
                    Proto.PRONTO -> OutlinedTextField(value = paste, onValueChange = { paste = it }, label = { Text("Pronto hex (0000 0073 0000 0012 …)") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                    Proto.FLIPPER -> OutlinedTextField(value = paste, onValueChange = { paste = it }, label = { Text("Flipper .ir block (name:/type:/protocol:/address:/command: or data:)") }, modifier = Modifier.fillMaxWidth(), minLines = 4)
                    Proto.RAW -> {
                        OutlinedTextField(value = f1, onValueChange = { f1 = it }, label = { Text("Carrier kHz (e.g. 38)") }, modifier = Modifier.width(160.dp), singleLine = true)
                        OutlinedTextField(value = f2, onValueChange = { f2 = it }, label = { Text("Timings µs, mark first (9000 4500 560 …)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    }
                    else -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text("Hex fields"); Switch(checked = hexFields, onCheckedChange = { hexFields = it }) }
                        val labels = when (proto) {
                            Proto.NOKIA32 -> listOf("Device", "Sub", "X (blank = device)", "Function")
                            Proto.SIRC12, Proto.SIRC15 -> listOf("Address", "Command", "", "")
                            Proto.SIRC20 -> listOf("Address", "Command", "Extended", "")
                            else -> listOf("Address", "Command", "", "")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(value = f1, onValueChange = { f1 = it }, label = { Text(labels[0]) }, modifier = Modifier.weight(1f), singleLine = true)
                            OutlinedTextField(value = f2, onValueChange = { f2 = it }, label = { Text(labels[1]) }, modifier = Modifier.weight(1f), singleLine = true)
                        }
                        if (labels[2].isNotEmpty() || labels[3].isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (labels[2].isNotEmpty()) OutlinedTextField(value = f3, onValueChange = { f3 = it }, label = { Text(labels[2]) }, modifier = Modifier.weight(1f), singleLine = true)
                            if (labels[3].isNotEmpty()) OutlinedTextField(value = f4, onValueChange = { f4 = it }, label = { Text(labels[3]) }, modifier = Modifier.weight(1f), singleLine = true)
                        }
                    }
                }
                Text(code?.let { "Ready: ${it.describe()} @ ${it.carrierHz / 1000.0} kHz, ${it.pattern().size} samples" } ?: "Fill in the fields (or paste) to build a code.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = code != null, onClick = { code?.let { send(it) } }) { Text("Send once") }
                    OutlinedButton(enabled = code != null && code !is IrCode.Raw, onClick = {
                        val base = code ?: return@OutlinedButton
                        val known = existing.values.mapNotNull { NecSweep.commandOf(it) }.toSet()
                        sweeping?.pause()
                        sweeping = CodesetScanner(NecSweep.commandSweepFor(base, known), send = { c -> graph.sendIrDiagnostic("expert sweep ${c.label}", c.code) }, intervalMs = 320).also { it.start(scope) }
                    }) { Text("Sweep commands from here") }
                }
                status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (it.startsWith("Sent") || it.startsWith("Saved")) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error) }
            }
            sweeping?.let { sc ->
                val st by sc.state.collectAsStateWithLifecycle()
                SectionCard("Sweeping ${st.index + 1} / ${st.total}") {
                    LinearProgressIndicator(progress = { st.progress }, modifier = Modifier.fillMaxWidth())
                    Text(sc.current?.label ?: "finished")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (st.running) OutlinedButton(onClick = { sc.pause() }) { Text("Pause") } else OutlinedButton(onClick = { if (st.finished) sc.seek(0); sc.start(scope) }) { Text("Resume") }
                        OutlinedButton(onClick = { sc.seek(st.index - 1) }, enabled = st.index > 0) { Text("Back one") }
                        OutlinedButton(onClick = { sc.pause(); sweeping = null }) { Text("Close") }
                    }
                    Button(modifier = Modifier.fillMaxWidth().height(56.dp), enabled = sc.current != null, onClick = { sc.lock(); verifying = sc.recent(4) }) { Text("It reacted!") }
                }
            }
            SectionCard("2. Assign to a key") {
                Text("Pick the key this code should drive, then Save. Keys already saved show ✓.", style = MaterialTheme.typography.bodySmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(overrideKeysFor(type), key = { it.key }) { c -> FilterChip(selected = key == c, onClick = { key = c }, label = { Text(c.key + if (existing.containsKey(c.key)) " ✓" else "") }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = code != null, onClick = { code?.let { assign(it, key) } }) { Text("Save as ${key.key}") }
                    if (savedId != null) OutlinedButton(onClick = { onDone(savedId!!) }) { Text("Open remote (${existing.size} keys)") }
                }
            }
            SectionCard("Where to find codes") {
                Text("Search for \"<brand> <box> ir codes\" together with \"irplus\", \"Flipper-IRDB\", \"Pronto\" or \"lirc\". irplus XML gives Pronto hex (paste the hex); Flipper gives name/type/protocol/address/command lines (paste the block). A code that works for one key means the others are usually on the same address: use \"Sweep commands from here\".", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    verifying?.let { recent ->
        VerifyDialog(graph, "expert", recent, onConfirmed = { c -> verifying = null; assign(c.code, key) }, onNone = { verifying = null })
    }
}
