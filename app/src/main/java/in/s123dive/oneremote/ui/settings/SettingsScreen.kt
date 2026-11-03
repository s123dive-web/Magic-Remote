package `in`.s123dive.oneremote.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.s123dive.oneremote.BuildConfig
import `in`.s123dive.oneremote.data.SettingsStore
import `in`.s123dive.oneremote.ui.appGraph
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenDebug: () -> Unit) {
    val graph = appGraph()
    val settings by graph.settings.settings.collectAsStateWithLifecycle(SettingsStore.Settings())
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ListItem(
                headlineContent = { Text("Haptic tick on every press") },
                leadingContent = { Icon(Icons.Default.Vibration, contentDescription = null) },
                trailingContent = {
                    Switch(checked = settings.haptics, onCheckedChange = { v -> scope.launch { graph.settings.setHaptics(v) } })
                },
            )
            HorizontalDivider()
            ListItem(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenDebug),
                headlineContent = { Text("Debug console") },
                supportingContent = { Text("Send log, IR carrier test, network state") },
                leadingContent = { Icon(Icons.Default.BugReport, contentDescription = null) },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("About") },
                supportingContent = {
                    Text("OneRemote ${BuildConfig.VERSION_NAME}. LAN-only, no account, no ads, no analytics.")
                },
            )
        }
    }
}
