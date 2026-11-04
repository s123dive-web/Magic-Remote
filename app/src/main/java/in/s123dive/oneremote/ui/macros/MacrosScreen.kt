package `in`.s123dive.oneremote.ui.macros

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.s123dive.oneremote.automation.Macros
import `in`.s123dive.oneremote.core.Command
import `in`.s123dive.oneremote.core.Macro
import `in`.s123dive.oneremote.core.MacroStep
import `in`.s123dive.oneremote.core.Role
import `in`.s123dive.oneremote.ui.SectionCard
import `in`.s123dive.oneremote.ui.appGraph
import `in`.s123dive.oneremote.ui.setup.SetupViewModel
import kotlinx.coroutines.launch

/** The macro row on the Scene: built-ins + user macros for the room, with run progress. */
@Composable
fun MacroRow(roomId: String, onEdit: () -> Unit) {
    val graph = appGraph()
    val state by graph.repository.state.collectAsStateWithLifecycle()
    val running by graph.macros.running.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val macros = state.macros.filter { it.roomId == roomId }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            items(macros, key = { it.id }) { m ->
                AssistChip(onClick = { scope.launch { graph.macros.run(m) } }, label = { Text(m.name) }, leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) }, enabled = running?.done != false)
            }
            item { AssistChip(onClick = onEdit, label = { Text("Macros…") }) }
        }
        running?.takeIf { !it.done }?.let { p ->
            LinearProgressIndicator(progress = { p.step.toFloat() / p.macro.steps.size.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
            Text("${p.macro.name}: step ${p.step}/${p.macro.steps.size}${if (p.failures > 0) " · ${p.failures} failed" else ""}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private val STEP_COMMANDS: List<Command> = listOf(Command.PowerOn, Command.PowerOff, Command.Power, Command.Mute, Command.VolUp, Command.VolDown, Command.ChUp, Command.ChDown, Command.Ok, Command.Home, Command.Back, Command.Exit, Command.Input, Command.Play, Command.Pause, Command.Stop)

/** Macro editor: list, run, add, edit steps (role + command | delay | channel | input | app), delete. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacrosScreen(roomId: String?, onBack: () -> Unit) {
    val graph = appGraph()
    val state by graph.repository.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val room = state.room(roomId ?: "") ?: state.rooms.firstOrNull()
    var editing by remember { mutableStateOf<Macro?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Macros${room?.let { " · ${it.name}" } ?: ""}") }, navigationIcon = {
                IconButton(onClick = { if (editing != null) editing = null else onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            })
        },
    ) { padding ->
        if (room == null) { Text("Create a room first.", Modifier.padding(padding).padding(16.dp)); return@Scaffold }
        val m = editing
        if (m != null) {
            MacroEditor(m, Modifier.padding(padding), onSave = { saved -> scope.launch { graph.repository.update { Macros.upsert(it, saved) } }; editing = null })
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.macros.filter { it.roomId == room.id }.forEach { macro ->
                SectionCard(macro.name + if (macro.builtIn) " (built-in)" else "") {
                    macro.steps.forEachIndexed { i, step -> Text("${i + 1}. ${describe(step)}", style = MaterialTheme.typography.bodySmall) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { scope.launch { graph.macros.run(macro) } }) { Text("Run") }
                        OutlinedButton(onClick = { editing = macro }) { Text("Edit") }
                        IconButton(onClick = { scope.launch { graph.repository.update { Macros.remove(it, macro.id) } } }) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                    }
                }
            }
            Button(onClick = { editing = Macro(SetupViewModel.newId(), room.id, "New macro", emptyList()) }) { Text("New macro") }
            Text("Built-ins are regenerated if deleted. Watch TV switches the TV to the box's input only when the TV's \"box input id\" is set in its device settings.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun describe(step: MacroStep) = when (step) {
    is MacroStep.Delay -> "wait ${step.ms} ms"
    is MacroStep.Send -> "${step.role}: ${step.cmd.key}"
}

@Composable
private fun MacroEditor(macro: Macro, modifier: Modifier, onSave: (Macro) -> Unit) {
    var name by remember { mutableStateOf(macro.name) }
    var steps by remember { mutableStateOf(macro.steps) }
    var role by remember { mutableStateOf(Role.TV) }
    var delayMs by remember { mutableStateOf("2000") }
    var channel by remember { mutableStateOf("") }
    var inputId by remember { mutableStateOf("") }
    var appId by remember { mutableStateOf("") }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
        SectionCard("Steps") {
            if (steps.isEmpty()) Text("No steps yet.", style = MaterialTheme.typography.bodySmall)
            steps.forEachIndexed { i, step ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}. ${describe(step)}")
                    IconButton(onClick = { steps = steps.filterIndexed { j, _ -> j != i } }) { Icon(Icons.Default.Delete, contentDescription = "Remove step") }
                }
            }
        }
        SectionCard("Add a step") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Role.entries.forEach { r -> FilterChip(selected = role == r, onClick = { role = r }, label = { Text(r.name) }) }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(STEP_COMMANDS, key = { it.key }) { c -> AssistChip(onClick = { steps = steps + MacroStep.Send(role, c) }, label = { Text(c.key) }) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = delayMs, onValueChange = { delayMs = it.filter(Char::isDigit) }, label = { Text("Delay ms") }, modifier = Modifier.width(130.dp), singleLine = true)
                OutlinedButton(enabled = delayMs.toLongOrNull() != null, onClick = { steps = steps + MacroStep.Delay(delayMs.toLong()) }) { Text("Add delay") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = channel, onValueChange = { channel = it.filter(Char::isDigit) }, label = { Text("Channel") }, modifier = Modifier.width(130.dp), singleLine = true)
                OutlinedButton(enabled = channel.toIntOrNull() != null, onClick = { steps = steps + MacroStep.Send(role, Command.Channel(channel.toInt())) }) { Text("Add channel") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = inputId, onValueChange = { inputId = it }, label = { Text("Input id") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedButton(enabled = inputId.isNotBlank(), onClick = { steps = steps + MacroStep.Send(role, Command.InputSelect(inputId.trim())) }) { Text("Add input") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = appId, onValueChange = { appId = it }, label = { Text("App id") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedButton(enabled = appId.isNotBlank(), onClick = { steps = steps + MacroStep.Send(role, Command.LaunchApp(appId.trim())) }) { Text("Add app") }
            }
        }
        Button(modifier = Modifier.fillMaxWidth(), enabled = name.isNotBlank(), onClick = { onSave(macro.copy(name = name.trim(), steps = steps)) }) { Text("Save") }
    }
}
