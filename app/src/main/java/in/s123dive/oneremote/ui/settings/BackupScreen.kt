package `in`.s123dive.oneremote.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.s123dive.oneremote.data.Backup
import `in`.s123dive.oneremote.ui.SectionCard
import `in`.s123dive.oneremote.ui.appGraph
import kotlinx.coroutines.launch

/**
 * Export (file via the system picker, or share sheet) and import (file picker or pasted JSON).
 * The export contains LG client-keys and Sony PSKs unless "without secrets" is on; the screen says so.
 * QR transfer is not in this build: it needs a camera/QR dependency the project has not approved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(onBack: () -> Unit) {
    val graph = appGraph()
    val context = LocalContext.current
    val state by graph.repository.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var withoutSecrets by remember { mutableStateOf(false) }
    var pasted by remember { mutableStateOf("") }
    var merge by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    fun exportText() = if (withoutSecrets) Backup.exportWithoutSecrets(state) else Backup.export(state)

    val createDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(exportText().encodeToByteArray()) } }
            .onSuccess { message = "Exported." }.onFailure { message = "Export failed: ${it.message}" }
    }
    fun importText(text: String) {
        when (val p = Backup.parse(text)) {
            is Backup.Parsed.Err -> message = p.reason
            is Backup.Parsed.Ok -> scope.launch {
                graph.repository.update { cur -> if (merge) Backup.merge(cur, p.file.state) else p.file.state }
                message = "Imported ${p.file.state.rooms.size} room(s), ${p.file.state.devices.size} device(s), ${p.file.state.macros.size} macro(s)."
            }
        }
    }
    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: "" }
            .onSuccess { importText(it) }.onFailure { message = "Read failed: ${it.message}" }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Backup") }, navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            })
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionCard("Export") {
                val secrets = Backup.secretCount(state)
                if (secrets > 0 && !withoutSecrets) Text("Warning: the export contains secrets for $secrets device(s) (LG client-key / Sony PSK). Anyone with the file can control those TVs.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Switch(checked = withoutSecrets, onCheckedChange = { withoutSecrets = it }); Text("Export without secrets") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { createDoc.launch("oneremote-backup.json") }) { Text("Save file") }
                    OutlinedButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply { type = "application/json"; putExtra(Intent.EXTRA_SUBJECT, "OneRemote backup"); putExtra(Intent.EXTRA_TEXT, exportText()) }
                        context.startActivity(Intent.createChooser(send, "Share backup"))
                    }) { Text("Share") }
                }
            }
            SectionCard("Import") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Switch(checked = merge, onCheckedChange = { merge = it }); Text(if (merge) "Merge into current setup" else "Replace current setup") }
                Button(onClick = { openDoc.launch(arrayOf("application/json", "text/*", "*/*")) }) { Text("Pick file") }
                OutlinedTextField(value = pasted, onValueChange = { pasted = it }, label = { Text("…or paste backup JSON") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                OutlinedButton(enabled = pasted.isNotBlank(), onClick = { importText(pasted) }) { Text("Import pasted text") }
            }
            SectionCard("QR transfer") {
                Text("Not available in this build: scanning needs a camera + QR library that is not among the approved dependencies. Use Share → the other phone → Import pasted text, or a file.", style = MaterialTheme.typography.bodySmall)
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.tertiary) }
        }
    }
}
