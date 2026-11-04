package `in`.s123dive.oneremote.ui.scene

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import `in`.s123dive.oneremote.core.Role
import `in`.s123dive.oneremote.core.Rooms
import `in`.s123dive.oneremote.data.SettingsStore
import `in`.s123dive.oneremote.system.PhoneCapabilities
import `in`.s123dive.oneremote.ui.KeyValue
import `in`.s123dive.oneremote.ui.SectionCard
import `in`.s123dive.oneremote.ui.appGraph
import `in`.s123dive.oneremote.ui.remote.ColourRow
import `in`.s123dive.oneremote.ui.remote.DeviceRemotePage
import `in`.s123dive.oneremote.ui.remote.DigitPad
import `in`.s123dive.oneremote.ui.remote.Key
import `in`.s123dive.oneremote.ui.remote.NavPad
import `in`.s123dive.oneremote.ui.remote.PadActions
import `in`.s123dive.oneremote.ui.remote.PlaybackRow
import `in`.s123dive.oneremote.ui.remote.PowerVolumeRow
import kotlinx.coroutines.launch

/**
 * The default screen: one room, both devices live at once. Page 0 is the dual-pane Scene;
 * swiping left reaches the full per-device remotes (TV, STB, extras).
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SceneScreen(
    onOpenSettings: () -> Unit,
    onAddDevice: (roomId: String?) -> Unit,
    onOpenRooms: () -> Unit,
    onDeviceSettings: (instanceId: String) -> Unit,
    extras: @Composable (`in`.s123dive.oneremote.ui.remote.RemoteViewModel, PadActions) -> Unit,
    macroRow: @Composable (roomId: String) -> Unit = {},
    micFab: @Composable (roomId: String) -> Unit = {},
) {
    val graph = appGraph()
    val vm: SceneViewModel = viewModel { SceneViewModel(graph) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val last by vm.last.collectAsStateWithLifecycle()
    val caps by graph.capabilities.state.collectAsStateWithLifecycle()
    val settings by graph.settings.settings.collectAsStateWithLifecycle(SettingsStore.Settings())
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var roomMenu by remember { mutableStateOf(false) }
    var overrideFor by remember { mutableStateOf<Pair<Role, Command>?>(null) }
    val room = ui.room
    val pagerDevices = remember(ui.state, room) { room?.let { Rooms.pagerDevices(ui.state, it) } ?: emptyList() }
    val pager = rememberPagerState(pageCount = { 1 + pagerDevices.size })

    LaunchedEffect(last) { last?.let { if (!it.ok) snackbar.showSnackbar(it.text) } }

    fun actions(zone: Role) = PadActions(
        supports = { vm.supports(zone, it) },
        send = { vm.send(zone, it) },
        onUnsupported = { cmd -> scope.launch { snackbar.showSnackbar(vm.unsupportedReason(zone, cmd)) } },
        onLongPress = { cmd -> overrideFor = zone to cmd },
        haptics = settings.haptics,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (ui.roomCount >= 2 && room != null) {
                        Box {
                            TextButton(onClick = { roomMenu = true }) { Icon(Icons.Default.MeetingRoom, contentDescription = null); Text("  ${room.name}") }
                            DropdownMenu(expanded = roomMenu, onDismissRequest = { roomMenu = false }) {
                                ui.state.rooms.forEach { r -> DropdownMenuItem(text = { Text(r.name) }, onClick = { vm.selectRoom(r.id); roomMenu = false }) }
                            }
                        }
                    } else Text(room?.name ?: "OneRemote")
                },
                actions = {
                    IconButton(onClick = onOpenRooms) { Icon(Icons.Default.MeetingRoom, contentDescription = "Rooms") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                },
            )
        },
        floatingActionButton = {
            if (room == null || (ui.tv == null && ui.stb == null)) FloatingActionButton(onClick = { onAddDevice(room?.id) }) { Icon(Icons.Default.Add, contentDescription = "Add device") }
            else micFab(room.id)
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (room == null || (ui.tv == null && ui.stb == null)) {
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionCard("Get started") { Text("Tap + to add your Airtel box, LG TV or Sony Bravia. Rooms are created automatically; manage them from the room icon.") }
                CapabilitiesCard(caps)
            }
            return@Scaffold
        }
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize().padding(padding)) { page ->
            if (page == 0) {
                val tvZone: @Composable () -> Unit = {
                    ui.tv?.let { tv ->
                        val a = actions(Role.TV)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("TV · ${tv.name}", style = MaterialTheme.typography.titleSmall)
                            Row {
                                IconButton(onClick = { onDeviceSettings(tv.id) }) { Icon(Icons.Default.Settings, contentDescription = "TV settings") }
                                if (ui.stb != null) IconButton(onClick = { vm.setTvCollapsed(!settings.tvZoneCollapsed) }) {
                                    Icon(if (settings.tvZoneCollapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess, contentDescription = "Collapse TV")
                                }
                            }
                        }
                        PowerVolumeRow(a)
                        if (!settings.tvZoneCollapsed || ui.stb == null) {
                            if (tv.type != DeviceType.AIRTEL_HD) AppChips(tv.id, a)
                            if (ui.stb == null) { NavPad(a); DigitPad(a); ColourRow(a); PlaybackRow(a) }
                        }
                    }
                }
                val stbZone: @Composable () -> Unit = {
                    ui.stb?.let { stb ->
                        val a = actions(Role.STB)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Box · ${stb.name}", style = MaterialTheme.typography.titleSmall)
                            IconButton(onClick = { onDeviceSettings(stb.id) }) { Icon(Icons.Default.Settings, contentDescription = "Box settings") }
                        }
                        if (ui.tv == null) PowerVolumeRow(a) else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Key(a, Command.Power, "Box power", Modifier.weight(1f), accent = true)
                            Key(a, Command.Guide, "Guide", Modifier.weight(1f))
                            Key(a, Command.Info, "Info", Modifier.weight(1f))
                        }
                        FavouritesRow(room.favourites, a)
                        DigitPad(a)
                        NavPad(a)
                        ColourRow(a)
                    }
                }
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val landscape = maxWidth > maxHeight && ui.tv != null && ui.stb != null
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            AssistChip(onClick = {}, label = { Text(last?.transport?.badge ?: "–") })
                            last?.let { Text(it.text, style = MaterialTheme.typography.bodySmall, color = if (it.ok) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error, maxLines = 1) }
                        }
                        macroRow(room.id)
                        if (landscape) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) { tvZone() }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) { stbZone() }
                            }
                        } else {
                            tvZone()
                            if (ui.tv != null && ui.stb != null) HorizontalDivider()
                            stbZone()
                        }
                        Text("Swipe left for the full remotes · long-press a key to change which device it goes to", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                val device = pagerDevices[page - 1]
                DeviceRemotePage(device.id, onDeviceSettings = { onDeviceSettings(device.id) }, extra = extras)
            }
        }
    }

    overrideFor?.let { (zone, cmd) ->
        val current = room?.routingOverrides?.get(cmd.key)
        AlertDialog(
            onDismissRequest = { overrideFor = null },
            title = { Text("Send ${cmd.key} to") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Default for this key: ${zone.name}. Current: ${current?.name ?: "default"}.", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { vm.setOverride(cmd, Role.TV); overrideFor = null }, enabled = ui.tv != null) { Text("TV · ${ui.tv?.name ?: "none"}") }
                    TextButton(onClick = { vm.setOverride(cmd, Role.STB); overrideFor = null }, enabled = ui.stb != null) { Text("Box · ${ui.stb?.name ?: "none"}") }
                    TextButton(onClick = { vm.setOverride(cmd, null); overrideFor = null }) { Text("Use default") }
                }
            },
            confirmButton = { TextButton(onClick = { overrideFor = null }) { Text("Close") } },
        )
    }
}

@Composable
private fun FavouritesRow(favourites: List<`in`.s123dive.oneremote.core.Favourite>, a: PadActions) {
    if (favourites.isEmpty()) return
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(favourites, key = { it.name + it.channel }) { f ->
            AssistChip(onClick = { if (a.supports(Command.Channel(f.channel))) a.send(Command.Channel(f.channel)) else a.onUnsupported(Command.Channel(f.channel)) }, label = { Text("${f.name} · ${f.channel}") })
        }
    }
}

/** App launcher chips for the TV zone (LG / Sony), loaded from the live Wi-Fi driver. */
@Composable
private fun AppChips(instanceId: String, a: PadActions) {
    val graph = appGraph()
    var apps by remember(instanceId) { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(instanceId) {
        val inst = graph.repository.current().device(instanceId) ?: return@LaunchedEffect
        val device = graph.registry.get(inst) ?: return@LaunchedEffect
        apps = device.transports.filterIsInstance<`in`.s123dive.oneremote.lg.LgTransport>().firstOrNull()?.apps()?.map { it.id to it.title }
            ?: device.transports.filterIsInstance<`in`.s123dive.oneremote.sony.SonyTransport>().firstOrNull()?.apps()?.map { it.uri to it.title }
            ?: emptyList()
    }
    if (apps.isEmpty()) return
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(apps.take(12), key = { it.first }) { (id, title) -> AssistChip(onClick = { a.send(Command.LaunchApp(id)) }, label = { Text(title) }) }
    }
}

@Composable
fun CapabilitiesCard(caps: PhoneCapabilities) {
    SectionCard("This phone") {
        KeyValue("IR blaster", if (caps.ir.hasIr) "present" else "none", highlight = !caps.ir.hasIr)
        KeyValue("IR carrier ranges", caps.ir.describeRanges())
        KeyValue("38 kHz (Airtel, LG IR)", yesNo(caps.ir.supports38k), highlight = !caps.ir.supports38k)
        KeyValue("40 kHz (Sony IR)", yesNo(caps.ir.supports40k), highlight = !caps.ir.supports40k)
        KeyValue("Wi-Fi / LAN", if (caps.wifiConnected) "connected" else "not connected", highlight = !caps.wifiConnected)
        KeyValue("Phone IP", caps.ipv4 ?: "–")
        listOfNotNull(caps.airtelMessage, caps.lgIrMessage, caps.sonyIrMessage).forEach {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

private fun yesNo(b: Boolean) = if (b) "yes" else "no"
