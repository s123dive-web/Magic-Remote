package `in`.s123dive.oneremote.ui.debug

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.s123dive.oneremote.core.LogEntry
import `in`.s123dive.oneremote.ui.KeyValue
import `in`.s123dive.oneremote.ui.SectionCard
import `in`.s123dive.oneremote.ui.appGraph
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TABS = listOf("Log", "IR", "Network", "Devices")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugConsoleScreen(onBack: () -> Unit) {
    val graph = appGraph()
    val vm: DebugViewModel = viewModel { DebugViewModel(graph) }
    var tab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debug console") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = tab) {
                TABS.forEachIndexed { i, title ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
                }
            }
            when (tab) {
                0 -> LogTab(vm)
                1 -> IrTab(vm)
                2 -> NetworkTab(vm)
                else -> DevicesTab(vm)
            }
        }
    }
}

private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
private fun fmt(t: Long): String = timeFormat.format(Date(t))

@Composable
private fun LogTab(vm: DebugViewModel) {
    val entries by vm.log.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var filter by remember { mutableStateOf<String?>(null) }
    val roles = remember(entries) { entries.map { it.role.substringBefore('/') }.distinct().sorted() }
    val shown = remember(entries, filter) { if (filter == null) entries else entries.filter { it.role.substringBefore('/') == filter } }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("All") })
            roles.forEach { r ->
                FilterChip(selected = filter == r, onClick = { filter = if (filter == r) null else r }, label = { Text(r) })
            }
        }
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.clearLog() }, enabled = entries.isNotEmpty()) { Text("Clear") }
            OutlinedButton(
                enabled = entries.isNotEmpty(),
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "OneRemote log")
                        putExtra(Intent.EXTRA_TEXT, vm.exportText(::fmt))
                    }
                    context.startActivity(Intent.createChooser(send, "Export log"))
                },
            ) { Text("Export log") }
        }
        if (shown.isEmpty()) {
            Text(
                "No sends yet. Every command, from any screen, notification, tile, widget, voice or intent, appears here.",
                Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(shown) { e -> LogRow(e); HorizontalDivider() }
        }
    }
}

@Composable
private fun LogRow(e: LogEntry) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${fmt(e.time)}  ${e.room} · ${e.role}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${e.transport} · ${e.ms} ms", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("${e.cmd}  →  ${e.payload}", style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
        Text(
            e.result, style = MaterialTheme.typography.bodySmall,
            color = if (e.ok) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun IrTab(vm: DebugViewModel) {
    val caps by vm.capabilities.collectAsStateWithLifecycle()
    val results by vm.carrierResults.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard("IR blaster") {
            KeyValue("hasIr", caps.ir.hasIr.toString(), highlight = !caps.ir.hasIr)
            KeyValue("Carrier ranges", caps.ir.describeRanges())
            KeyValue("38 kHz", if (caps.ir.supports38k) "supported" else "not supported", highlight = !caps.ir.supports38k)
            KeyValue("40 kHz", if (caps.ir.supports40k) "supported" else "not supported", highlight = !caps.ir.supports40k)
            if (caps.ir.hasIr && !caps.ir.rangesKnown) {
                Text("The HAL did not report ranges; the carrier tests below are the only way to know.", style = MaterialTheme.typography.bodySmall)
            }
        }
        SectionCard("Carrier test") {
            Text("Point the phone at the TV. Each test sends that TV's real Power code once; the TV toggling proves the carrier works.", style = MaterialTheme.typography.bodySmall)
            CARRIER_TESTS.forEach { test ->
                Button(
                    onClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); vm.runCarrierTest(test) },
                    enabled = running == null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (running == test.id) "Sending…" else test.label) }
                results[test.id]?.let { KeyValue(test.payload, it, highlight = !it.startsWith("sent")) }
            }
        }
    }
}

@Composable
private fun NetworkTab(vm: DebugViewModel) {
    val caps by vm.capabilities.collectAsStateWithLifecycle()
    val graph = appGraph()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard("Network") {
            KeyValue("Wi-Fi / LAN", if (caps.wifiConnected) "connected" else "not connected", highlight = !caps.wifiConnected)
            KeyValue("Phone IP", caps.ipv4 ?: "–")
        }
        val disc by graph.discovery.state.collectAsStateWithLifecycle()
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        SectionCard("Discovery") {
            Button(enabled = !disc.running, onClick = { scope.launch { graph.discovery.run(caps.ipv4) } }) { Text(if (disc.running) "Searching…" else "Run discovery") }
            if (disc.found.isEmpty()) Text("No devices found yet.", style = MaterialTheme.typography.bodySmall)
            disc.found.forEach { KeyValue("${it.type.displayName} @ ${it.ip}", "${it.name} · ${it.via}") }
            disc.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
        SectionCard("Raw SSDP responses (${disc.rawSsdp.size})") {
            disc.rawSsdp.forEach { Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
        }
    }
}

@Composable
private fun DevicesTab(vm: DebugViewModel) {
    val state by vm.appState.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard("Device info") {
            if (state.devices.isEmpty()) {
                Text("No devices configured.", style = MaterialTheme.typography.bodySmall)
            }
            state.devices.forEach { d ->
                val cfg = `in`.s123dive.oneremote.devices.DeviceConfig(d.config)
                KeyValue(d.name, "${d.type.displayName} · ${d.transports.joinToString("/") { it.badge }}")
                KeyValue("  codeset", (cfg.codesetId ?: "custom") + if (cfg.customCodes.isEmpty()) "" else " +${cfg.customCodes.size} expert")
                cfg.ip?.let { KeyValue("  ip", it) }
                cfg.webosVersion?.let { KeyValue("  webOS", it) }
                cfg.model?.let { KeyValue("  model", it + (cfg.firmware?.let { f -> " / $f" } ?: "")) }
            }
        }
    }
}
