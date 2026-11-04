package `in`.s123dive.oneremote.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.s123dive.oneremote.core.Command
import `in`.s123dive.oneremote.core.DeviceType
import `in`.s123dive.oneremote.core.Role
import `in`.s123dive.oneremote.core.RouteRequest
import `in`.s123dive.oneremote.core.RouteResult
import `in`.s123dive.oneremote.core.Source
import `in`.s123dive.oneremote.devices.DeviceConfig
import `in`.s123dive.oneremote.net.WakeOnLan
import `in`.s123dive.oneremote.ui.KeyValue
import `in`.s123dive.oneremote.ui.SectionCard
import `in`.s123dive.oneremote.ui.appGraph
import kotlinx.coroutines.launch

/** Wi-Fi settings for LG / Sony instances: IP, MAC (for Wake-on-LAN), re-pair, reachability and WoL tests. */
@Composable
fun WifiDeviceSection(vm: SetupViewModel, instanceId: String, onRepair: (DeviceType, String) -> Unit) {
    val graph = appGraph()
    val state by vm.state.collectAsStateWithLifecycle()
    val instance = state.device(instanceId) ?: return
    if (instance.type == DeviceType.AIRTEL_HD) return
    val config = DeviceConfig(instance.config)
    val scope = rememberCoroutineScope()
    var ip by remember(config.ip) { mutableStateOf(config.ip ?: "") }
    var mac by remember(config.mac, instance.mac) { mutableStateOf(instance.mac ?: config.mac ?: "") }
    var result by remember { mutableStateOf<String?>(null) }
    var stbInput by remember(config.stbInput) { mutableStateOf(config.stbInput ?: "") }

    SectionCard("Watch TV macro") {
        OutlinedTextField(value = stbInput, onValueChange = { stbInput = it.trim() }, label = { Text("Input the box is connected to (id)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Text(if (instance.type == DeviceType.LG_WEBOS) "LG ids look like HDMI_1 (see the Inputs chips on the remote)." else "Sony ids look like extInput:hdmi?port=1 (see the Inputs chips on the remote).", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { scope.launch { vm.updateConfig(instanceId) { c -> if (stbInput.isBlank()) c.without(DeviceConfig.STB_INPUT) else c.with(DeviceConfig.STB_INPUT, stbInput) }; graph.repository.update { `in`.s123dive.oneremote.automation.Macros.ensureBuiltIns(it.copy(macros = it.macros.filterNot { m -> m.builtIn && m.roomId == instance.roomId })) }; result = "Saved; Watch TV regenerated" } }) { Text("Save input") }
    }
    SectionCard("Wi-Fi") {
        OutlinedTextField(value = ip, onValueChange = { ip = it.trim() }, label = { Text("IP address") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = mac, onValueChange = { mac = it.trim() }, label = { Text("MAC address (Wake-on-LAN)") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            isError = mac.isNotBlank() && WakeOnLan.parseMac(mac) == null)
        KeyValue("Secrets stored", if (config.clientKey != null) "LG client-key" else if (config.psk != null) "Sony PSK" else "none")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                scope.launch {
                    vm.updateConfig(instanceId) { c -> c.with(DeviceConfig.IP, ip).let { if (mac.isBlank()) it.without(DeviceConfig.MAC) else it.with(DeviceConfig.MAC, WakeOnLan.parseMac(mac)?.let(WakeOnLan::formatMac) ?: mac) } }
                    result = "Saved"
                }
            }) { Text("Save Wi-Fi") }
            OutlinedButton(onClick = { onRepair(instance.type, ip) }) { Text(if (instance.type == DeviceType.LG_WEBOS) "Pair again" else "Re-check PSK") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                scope.launch {
                    val device = graph.registry.get(instance)
                    val wifi = device?.transports?.firstOrNull { it.kind == `in`.s123dive.oneremote.core.TransportKind.WIFI }
                    result = if (wifi == null) "No Wi-Fi transport (missing IP?)" else if (wifi.isReachable()) "Reachable over Wi-Fi" else "Not reachable over Wi-Fi (TV off, wrong IP, or IP control disabled)"
                }
            }) { Text("Test connection") }
            OutlinedButton(onClick = {
                scope.launch {
                    val r = graph.router.send(RouteRequest(instance.roomId, Role.EXTRA, Command.PowerOn, Source.DEBUG, instanceId = instance.id))
                    result = when (r) { is RouteResult.Sent -> "Power-on sent via ${r.transport.badge}: ${r.payload}"; is RouteResult.Failed -> "Failed: ${r.reason}"; is RouteResult.Unsupported -> r.reason }
                }
            }) { Text("Test power-on") }
        }
        result?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
