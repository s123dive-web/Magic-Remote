package `in`.s123dive.oneremote.ui.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.s123dive.oneremote.core.DeviceType
import `in`.s123dive.oneremote.core.capability
import `in`.s123dive.oneremote.data.SettingsStore
import `in`.s123dive.oneremote.ui.appGraph
import kotlinx.coroutines.launch

/**
 * Full per-device remote. The pad is the same for all types; keys the live driver cannot
 * carry are greyed and explain themselves when tapped. LG/Sony add touchpad, text and apps
 * in their own phases via [extra].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceRemoteScreen(
    instanceId: String,
    onBack: () -> Unit,
    onDeviceSettings: () -> Unit,
    extra: @Composable (RemoteViewModel, PadActions) -> Unit = { _, _ -> },
    onHome: (() -> Unit)? = null,
) {
    val graph = appGraph()
    val instance by graph.repository.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(instance.device(instanceId)?.name ?: "Remote") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    if (onHome != null) IconButton(onClick = onHome) { Icon(Icons.Default.Home, contentDescription = "Home: all remotes, add device") }
                    IconButton(onClick = onDeviceSettings) { Icon(Icons.Default.Settings, contentDescription = "Device settings") }
                },
            )
        },
    ) { padding ->
        DeviceRemotePage(instanceId, onDeviceSettings, extra, Modifier.padding(padding))
    }
}

/** Hotkeys (Netflix, Prime Video…) launch by matching the TV's app list; the result shows in the last-send line. */
private fun launchAppByName(vm: RemoteViewModel, name: String) = vm.launchAppByName(name)

/** The remote itself: used by the stand-alone screen and by the Scene pager. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceRemotePage(
    instanceId: String,
    onDeviceSettings: () -> Unit,
    extra: @Composable (RemoteViewModel, PadActions) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val graph = appGraph()
    val vm: RemoteViewModel = viewModel(key = "remote-$instanceId") { RemoteViewModel(graph, instanceId) }
    val instance by vm.instance.collectAsStateWithLifecycle()
    val caps by vm.capabilities.collectAsStateWithLifecycle()
    val last by vm.last.collectAsStateWithLifecycle()
    val settings by graph.settings.settings.collectAsStateWithLifecycle(SettingsStore.Settings())
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(last) { last?.let { if (!it.ok) snackbar.showSnackbar(it.text) } }

    val actions = remember(caps, settings.haptics) {
        PadActions(
            supports = { it.capability in caps },
            send = { vm.send(it) },
            onUnsupported = { cmd -> scope.launch { snackbar.showSnackbar(vm.unavailableReason() ?: "${instance?.name} cannot ${cmd.key} on any transport") } },
            haptics = settings.haptics,
        )
    }

    var sheet by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(last?.text ?: (vm.unavailableReason() ?: "Ready"), style = MaterialTheme.typography.labelSmall, color = if (last?.ok == false || vm.unavailableReason() != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, modifier = Modifier.weight(1f))
                AssistChip(onClick = {}, label = { Text(last?.transport?.badge ?: instance?.transports?.joinToString("/") { it.badge } ?: "–") })
                if (instance?.type == DeviceType.LG_WEBOS || instance?.type == DeviceType.SONY_BRAVIA) IconButton(onClick = { sheet = true }) { Icon(androidx.compose.material.icons.Icons.Default.MoreVert, contentDescription = "Apps, inputs, typing") }
                IconButton(onClick = onDeviceSettings) { Icon(Icons.Default.Settings, contentDescription = "Device settings") }
            }
            // Design widths of the physical layouts; heights are measured by FitScale.
            val w = when (instance?.type) {
                DeviceType.AIRTEL_HD -> 250.dp
                DeviceType.LG_WEBOS -> 220.dp
                DeviceType.SONY_BRAVIA -> 230.dp
                DeviceType.ATOMBERG_FAN -> 230.dp
                null -> 200.dp
            }
            `in`.s123dive.oneremote.ui.remote.layouts.FitScale(w, Modifier.weight(1f)) {
                when (instance?.type) {
                    DeviceType.AIRTEL_HD -> `in`.s123dive.oneremote.ui.remote.layouts.AirtelRemote(actions, tv = vm.roomTvActions(actions))
                    DeviceType.LG_WEBOS -> `in`.s123dive.oneremote.ui.remote.layouts.LgMagicRemote(
                        actions,
                        onMic = { context.startActivity(android.content.Intent(context, `in`.s123dive.oneremote.voice.VoiceActivity::class.java)) },
                        onApp = { name -> launchAppByName(vm, name) },
                    )
                    DeviceType.SONY_BRAVIA -> `in`.s123dive.oneremote.ui.remote.layouts.SonyBraviaRemote(actions, onApp = { name -> launchAppByName(vm, name) })
                    DeviceType.ATOMBERG_FAN -> `in`.s123dive.oneremote.ui.remote.layouts.AtombergRemote(actions)
                    null -> {}
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
    if (sheet) {
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { sheet = false }) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                instance?.let { extra(vm, actions) }
            }
        }
    }
}
