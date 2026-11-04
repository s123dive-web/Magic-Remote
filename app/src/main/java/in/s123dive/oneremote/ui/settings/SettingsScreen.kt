package `in`.s123dive.oneremote.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.s123dive.oneremote.BuildConfig
import `in`.s123dive.oneremote.data.SettingsStore
import `in`.s123dive.oneremote.system.RemoteNotificationService
import `in`.s123dive.oneremote.ui.appGraph
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenDebug: () -> Unit, onOpenBackup: () -> Unit, onOpenMacros: () -> Unit) {
    val graph = appGraph()
    val context = LocalContext.current
    val settings by graph.settings.settings.collectAsStateWithLifecycle(SettingsStore.Settings())
    val scope = rememberCoroutineScope()

    fun startNotificationRemote() { scope.launch { graph.settings.setNotificationRemote(true) }; RemoteNotificationService.start(context) }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) startNotificationRemote() }
    fun toggleNotificationRemote(on: Boolean) {
        if (!on) { scope.launch { graph.settings.setNotificationRemote(false) }; RemoteNotificationService.stop(context); return }
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        else startNotificationRemote()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            ListItem(
                headlineContent = { Text("Notification remote") },
                supportingContent = { Text("Power, volume, mute, channels, input and mic in the notification shade") },
                leadingContent = { Icon(Icons.Default.Notifications, contentDescription = null) },
                trailingContent = { Switch(checked = settings.notificationRemote, onCheckedChange = ::toggleNotificationRemote) },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Haptic tick on every press") },
                leadingContent = { Icon(Icons.Default.Vibration, contentDescription = null) },
                trailingContent = { Switch(checked = settings.haptics, onCheckedChange = { v -> scope.launch { graph.settings.setHaptics(v) } }) },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Phone volume keys control the TV") },
                supportingContent = { Text("While OneRemote is open: tap = volume, hold = mute") },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null) },
                trailingContent = { Switch(checked = settings.volumeKeys, onCheckedChange = { v -> scope.launch { graph.settings.setVolumeKeys(v) } }) },
            )
            HorizontalDivider()
            ListItem(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenMacros),
                headlineContent = { Text("Macros") },
                supportingContent = { Text("Watch TV, All off and your own sequences") },
                leadingContent = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
            )
            HorizontalDivider()
            ListItem(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenBackup),
                headlineContent = { Text("Backup & restore") },
                supportingContent = { Text("Export / import rooms, devices, macros, favourites") },
                leadingContent = { Icon(Icons.Default.Backup, contentDescription = null) },
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
                headlineContent = { Text("Intent API") },
                supportingContent = { Text("adb shell am broadcast -a in.s123dive.oneremote.SEND -p ${BuildConfig.APPLICATION_ID} --es room Hall --es role TV --es cmd VOL_UP\n…or --es macro \"Watch TV\". See README.") },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("About") },
                supportingContent = { Text("OneRemote ${BuildConfig.VERSION_NAME}. LAN-only, no account, no ads, no analytics.") },
            )
        }
    }
}
