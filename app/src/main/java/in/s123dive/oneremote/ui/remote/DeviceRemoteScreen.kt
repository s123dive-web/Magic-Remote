package `in`.s123dive.oneremote.ui.remote

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
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.s123dive.oneremote.core.Command
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(instance?.name ?: "Remote") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = { IconButton(onClick = onDeviceSettings) { Icon(Icons.Default.Settings, contentDescription = "Device settings") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                AssistChip(onClick = {}, label = { Text(last?.transport?.badge ?: instance?.transports?.joinToString("/") { it.badge } ?: "–") })
                last?.let { Text(it.text, style = MaterialTheme.typography.bodySmall, color = if (it.ok) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error) }
            }
            vm.unavailableReason()?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            PowerVolumeRow(actions)
            NavPad(actions)
            DigitPad(actions)
            ColourRow(actions)
            PlaybackRow(actions)
            instance?.let { extra(vm, actions) }
            if (instance?.type == DeviceType.AIRTEL_HD) {
                Text("Channel numbers typed on the Scene screen send the digits followed by OK.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            @Suppress("UNUSED_VARIABLE") val unused = Command.Ok // keeps the import for extras that reference Command
        }
    }
}
