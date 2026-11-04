package `in`.s123dive.oneremote.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.s123dive.oneremote.core.DeviceType
import `in`.s123dive.oneremote.core.RouteResult
import `in`.s123dive.oneremote.core.SendResult
import `in`.s123dive.oneremote.core.TransportKind
import `in`.s123dive.oneremote.devices.DeviceConfig
import `in`.s123dive.oneremote.ir.codesets.SonySircCodeset
import `in`.s123dive.oneremote.sony.SonyApi
import `in`.s123dive.oneremote.sony.SonyClient
import `in`.s123dive.oneremote.ui.KeyValue
import `in`.s123dive.oneremote.ui.SectionCard
import `in`.s123dive.oneremote.ui.appGraph
import kotlinx.coroutines.launch

/**
 * PSK entry + getSystemInformation precheck with precise errors, then stores the TV's IRCC code
 * list and probes setTextForm so the Text capability is only claimed when it really works.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SonyPairScreen(ip: String, suggestedName: String?, roomId: String?, existingId: String?, onBack: () -> Unit, onDone: (String) -> Unit) {
    val graph = appGraph()
    val vm: SetupViewModel = viewModel { SetupViewModel(graph) }
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val existing = existingId?.let { state.device(it) }
    var name by remember { mutableStateOf(existing?.name ?: suggestedName ?: "Sony Bravia") }
    var psk by remember { mutableStateOf(existing?.let { DeviceConfig(it.config).psk } ?: "") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<SonyApi.SystemInfo?>(null) }
    var codes by remember { mutableStateOf<Int?>(null) }
    var textInput by remember { mutableStateOf<Boolean?>(null) }
    var savedId by remember { mutableStateOf(existingId) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Connect Sony Bravia") }, navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            })
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
            KeyValue("IP", ip)
            OutlinedTextField(value = psk, onValueChange = { psk = it }, label = { Text("Pre-shared key (PSK)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
            SectionCard("On the TV") {
                Text("Settings → Network → Home Network → IP Control: Authentication = \"Normal and Pre-Shared Key\", set the PSK; Simple IP Control = On; Remote start = On (for power-on over Wi-Fi).", style = MaterialTheme.typography.bodySmall)
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            info?.let { i ->
                SectionCard("TV answered") {
                    KeyValue("Model", i.model ?: "?"); KeyValue("Firmware", i.generation ?: "?"); KeyValue("MAC", i.mac ?: "unknown", highlight = i.mac == null)
                    KeyValue("IRCC codes", codes?.toString() ?: "not fetched", highlight = codes == null)
                    KeyValue("Text input (setTextForm)", when (textInput) { true -> "supported"; false -> "not supported"; null -> "unknown" }, highlight = textInput != true)
                }
            }
            Button(
                modifier = Modifier.fillMaxWidth(), enabled = !busy && psk.isNotBlank(),
                onClick = {
                    busy = true; error = null
                    scope.launch {
                        val client = SonyClient(ip, psk)
                        val r = graph.router.diagnostic("Sony precheck $ip", TransportKind.WIFI, "system/getSystemInformation") {
                            when (val o = client.systemInformation()) {
                                is SonyClient.Outcome.Ok -> { info = o.value; SendResult.Ok("${o.value.model} ${o.value.generation}") }
                                else -> SendResult.Failed("system/getSystemInformation", o.reason)
                            }
                        }
                        if (r !is RouteResult.Sent) {
                            error = when (r) { is RouteResult.Failed -> r.reason; is RouteResult.Unsupported -> r.reason; else -> "failed" }
                            busy = false; return@launch
                        }
                        val codeMap = (client.remoteControllerInfo() as? SonyClient.Outcome.Ok)?.value ?: emptyMap()
                        codes = codeMap.size
                        val probe = graph.router.diagnostic("Sony setTextForm probe", TransportKind.WIFI, "appControl/setTextForm \"\"") {
                            when (val o = client.setTextForm("")) {
                                is SonyClient.Outcome.Ok -> SendResult.Ok("supported")
                                is SonyClient.Outcome.Unsupported -> SendResult.Unsupported(o.reason)
                                else -> SendResult.Failed("setTextForm", o.reason)
                            }
                        }
                        textInput = probe is RouteResult.Sent
                        val i = info!!
                        var cfg = DeviceConfig.empty().with(DeviceConfig.IP, ip).with(DeviceConfig.PSK, psk)
                            .with(DeviceConfig.CODESET_ID, SonySircCodeset.ID_ADDR1)
                            .with(DeviceConfig.TEXT_INPUT, textInput == true)
                            .withIrccCodes(codeMap)
                        i.model?.let { cfg = cfg.with(DeviceConfig.MODEL, it) }
                        i.generation?.let { cfg = cfg.with(DeviceConfig.FIRMWARE, it) }
                        i.mac?.let { cfg = cfg.with(DeviceConfig.MAC, it) }
                        val id = savedId
                        if (id == null) {
                            savedId = vm.addDevice(DeviceType.SONY_BRAVIA, name.ifBlank { i.model ?: "Sony Bravia" }, roomId, listOf(TransportKind.WIFI, TransportKind.IR), cfg, mac = i.mac)
                        } else {
                            vm.rename(id, name)
                            vm.updateConfig(id) { old -> DeviceConfig(kotlinx.serialization.json.JsonObject(old.json + cfg.json)) }
                        }
                        busy = false
                    }
                },
            ) { Text(if (savedId == null) "Check & save" else "Check again & save") }
            if (savedId != null) Button(modifier = Modifier.fillMaxWidth(), onClick = { onDone(savedId!!) }) { Text("Done") }
        }
    }
}
