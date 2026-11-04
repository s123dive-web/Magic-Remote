package `in`.s123dive.oneremote.voice

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import `in`.s123dive.oneremote.OneRemoteApplication
import `in`.s123dive.oneremote.data.SettingsStore
import `in`.s123dive.oneremote.ui.theme.OneRemoteTheme
import kotlinx.coroutines.launch

/** Voice capture sheet: opened by the Scene mic FAB and the notification mic. Asks for RECORD_AUDIO lazily. */
class VoiceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = (application as OneRemoteApplication).graph
        setContent { OneRemoteTheme { VoiceSheet(graph, onClose = { finish() }) } }
    }
}

val VOICE_LANGUAGES = listOf("en-IN" to "English", "hi-IN" to "हिन्दी", "mr-IN" to "मराठी")

@Composable
fun VoiceSheet(graph: `in`.s123dive.oneremote.AppGraph, onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val controller = remember { VoiceController(context, graph) }
    val state by controller.state.collectAsStateWithLifecycle()
    val settings by graph.settings.settings.collectAsStateWithLifecycle(SettingsStore.Settings())
    val scope = rememberCoroutineScope()
    var typed by remember { mutableStateOf("") }
    var granted by remember { mutableStateOf(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> granted = ok; if (ok) controller.start(settings.voiceLanguage) }
    DisposableEffect(Unit) { onDispose { controller.destroy() } }
    LaunchedEffect(Unit) { if (granted && state.available) controller.start(settings.voiceLanguage) else if (!granted) ask.launch(Manifest.permission.RECORD_AUDIO) }

    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Mic, contentDescription = null, tint = if (state.listening) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(when { !state.available -> "No speech recogniser on this phone"; state.listening -> "Listening…"; else -> "Voice" }, style = MaterialTheme.typography.titleMedium)
                    Text(if (state.onDevice) "on-device" else "online recogniser", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (state.listening) LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    VOICE_LANGUAGES.forEach { (tag, label) ->
                        FilterChip(selected = settings.voiceLanguage == tag, onClick = { scope.launch { graph.settings.setVoiceLanguage(tag) } }, label = { Text(label) })
                    }
                }
                Text(state.transcript ?: state.partial.ifBlank { "Say: \"channel 120\", \"volume badhao\", \"mute\", \"open YouTube\", \"search cricket\", \"sleep in 30 minutes\"" }, style = MaterialTheme.typography.bodyLarge)
                state.action?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                state.result?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (it.startsWith("Sent") || it.contains("steps sent") || it.startsWith("TV off")) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error) }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = typed, onValueChange = { typed = it }, label = { Text("…or type a command") }, modifier = Modifier.weight(1f), singleLine = true)
                    TextButton(enabled = typed.isNotBlank(), onClick = { controller.handle(typed); typed = "" }) { Text("Go") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = state.available, onClick = { if (granted) { if (state.listening) controller.stop() else controller.start(settings.voiceLanguage) } else ask.launch(Manifest.permission.RECORD_AUDIO) }) {
                        Text(if (state.listening) "Stop" else "Listen again")
                    }
                    TextButton(onClick = onClose) { Text("Close") }
                }
            }
        }
    }
}
