package `in`.s123dive.oneremote.ui.setup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.s123dive.oneremote.core.DeviceType
import `in`.s123dive.oneremote.ui.appGraph

/** Pick what to add. Each type goes to its own flow; IR-only types are greyed without a blaster. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDeviceScreen(roomId: String?, onBack: () -> Unit, onPick: (DeviceType) -> Unit) {
    val caps by appGraph().capabilities.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Add device") }, navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            })
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TypeCard(
                title = DeviceType.AIRTEL_HD.displayName,
                subtitle = "IR only. The wizard finds the code set by trying candidates until the box reacts.",
                blocked = caps.airtelMessage,
                onClick = { onPick(DeviceType.AIRTEL_HD) },
            )
            TypeCard(
                title = DeviceType.LG_WEBOS.displayName,
                subtitle = "Wi-Fi (pair with PIN on screen) with IR fallback.",
                blocked = null,
                onClick = { onPick(DeviceType.LG_WEBOS) },
            )
            TypeCard(
                title = DeviceType.SONY_BRAVIA.displayName,
                subtitle = "Wi-Fi (pre-shared key) with IR fallback.",
                blocked = null,
                onClick = { onPick(DeviceType.SONY_BRAVIA) },
            )
            if (roomId != null) Text("Will be added to the current room.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TypeCard(title: String, subtitle: String, blocked: String?, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().then(if (blocked == null) Modifier.clickable(onClick = onClick) else Modifier)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = if (blocked == null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
            if (blocked != null) Text(blocked, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}
