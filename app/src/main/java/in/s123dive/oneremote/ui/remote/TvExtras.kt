package `in`.s123dive.oneremote.ui.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import `in`.s123dive.oneremote.core.Capability
import `in`.s123dive.oneremote.core.Command
import `in`.s123dive.oneremote.lg.LgTransport
import `in`.s123dive.oneremote.sony.SonyTransport
import `in`.s123dive.oneremote.ui.SectionCard
import `in`.s123dive.oneremote.ui.appGraph
import kotlinx.coroutines.launch

/**
 * LG Magic-Remote touchpad: one finger moves the pointer, a tap clicks, two fingers scroll.
 * Moves are sent as deltas (scaled) through the Router like every other command.
 */
@Composable
fun Touchpad(a: PadActions, modifier: Modifier = Modifier) {
    val enabled = a.supports(Command.PointerClick)
    Box(
        modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var moved = false
                    var accX = 0f; var accY = 0f
                    var last: Map<Long, Offset> = mapOf(down.id.value to down.position)
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        val now = pressed.associate { it.id.value to it.position }
                        val common = now.keys.intersect(last.keys)
                        if (common.isNotEmpty()) {
                            val dx = common.map { now[it]!!.x - last[it]!!.x }.average().toFloat()
                            val dy = common.map { now[it]!!.y - last[it]!!.y }.average().toFloat()
                            accX += dx; accY += dy
                            if (kotlin.math.abs(accX) >= 2f || kotlin.math.abs(accY) >= 2f) {
                                moved = true
                                if (pressed.size >= 2) a.send(Command.Scroll(0, (accY / 4f).toInt().coerceIn(-10, 10).let { if (it == 0) if (accY > 0) 1 else -1 else it }))
                                else a.send(Command.PointerMove((accX * 1.5f).toInt(), (accY * 1.5f).toInt()))
                                accX = 0f; accY = 0f
                            }
                        }
                        last = now
                        event.changes.forEach { it.consume() }
                    }
                    if (!moved) a.send(Command.PointerClick)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(if (enabled) "Touchpad · tap = click · two fingers = scroll" else "Pointer not available (Wi-Fi only)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun TextInputRow(a: PadActions, supported: Boolean, unsupportedText: String) {
    var text by remember { mutableStateOf("") }
    SectionCard("Type on the TV") {
        if (!supported) { Text(unsupportedText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error); return@SectionCard }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("Text") })
            OutlinedButton(onClick = { a.send(Command.Text(text)) }, enabled = text.isNotEmpty()) { Text("Type") }
            Button(onClick = { a.send(Command.Text(text + "\n")); text = "" }) { Text("Enter") }
        }
    }
}

@Composable
fun ChipRow(title: String, items: List<Pair<String, String>>, empty: String, onPick: (String) -> Unit, onRefresh: () -> Unit) {
    SectionCard(title) {
        if (items.isEmpty()) Text(empty, style = MaterialTheme.typography.bodySmall)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items, key = { it.first }) { (id, label) -> AssistChip(onClick = { onPick(id) }, label = { Text(label) }) }
        }
        OutlinedButton(onClick = onRefresh) { Text("Refresh") }
    }
}

/** LG: touchpad, text, inputs, apps. */
@Composable
fun LgExtras(vm: RemoteViewModel, a: PadActions) {
    val graph = appGraph()
    val scope = rememberCoroutineScope()
    val transport = vm.instance.value?.let { graph.registry.get(it) }?.transports?.filterIsInstance<LgTransport>()?.firstOrNull()
    var inputs by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var apps by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    fun refresh() { scope.launch { transport?.let { t -> inputs = t.inputs().map { it.id to it.title }; apps = t.apps().map { it.id to it.title } } } }
    LaunchedEffect(transport) { refresh() }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Touchpad(a)
        TextInputRow(a, supported = a.supports(Command.Text("")), unsupportedText = "Text input needs the Wi-Fi connection.")
        ChipRow("Inputs", inputs, "Connect over Wi-Fi to list inputs (IR Input key cycles them).", onPick = { a.send(Command.InputSelect(it)) }, onRefresh = ::refresh)
        ChipRow("Apps", apps, "Connect over Wi-Fi to list apps.", onPick = { a.send(Command.LaunchApp(it)) }, onRefresh = ::refresh)
    }
}

/** Sony: inputs, apps, text when the TV supports setTextForm. */
@Composable
fun SonyExtras(vm: RemoteViewModel, a: PadActions) {
    val graph = appGraph()
    val scope = rememberCoroutineScope()
    val transport = vm.instance.value?.let { graph.registry.get(it) }?.transports?.filterIsInstance<SonyTransport>()?.firstOrNull()
    var inputs by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var apps by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    fun refresh() { scope.launch { transport?.let { t -> inputs = t.inputs().map { it.uri to it.title }; apps = t.apps().map { it.uri to it.title } } } }
    LaunchedEffect(transport) { refresh() }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TextInputRow(a, supported = Capability.TEXT_INPUT in vm.capabilities.value, unsupportedText = "This TV reported no setTextForm support; typing is not available.")
        ChipRow("Inputs", inputs, "No inputs listed (TV off or Wi-Fi unavailable).", onPick = { a.send(Command.InputSelect(it)) }, onRefresh = ::refresh)
        ChipRow("Apps", apps, "No apps listed (TV off or Wi-Fi unavailable).", onPick = { a.send(Command.LaunchApp(it)) }, onRefresh = ::refresh)
    }
}
