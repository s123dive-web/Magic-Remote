package `in`.s123dive.oneremote.ui.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.s123dive.oneremote.AppGraph
import `in`.s123dive.oneremote.core.Capability
import `in`.s123dive.oneremote.core.Command
import `in`.s123dive.oneremote.core.DeviceInstance
import `in`.s123dive.oneremote.core.Role
import `in`.s123dive.oneremote.core.RouteRequest
import `in`.s123dive.oneremote.core.RouteResult
import `in`.s123dive.oneremote.core.Source
import `in`.s123dive.oneremote.core.TransportKind
import `in`.s123dive.oneremote.core.capability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the last send did: feeds the transport badge and the toast. */
data class LastSend(val cmd: Command, val result: RouteResult) {
    val transport: TransportKind? get() = (result as? RouteResult.Sent)?.transport
    val text: String
        get() = when (result) {
            is RouteResult.Sent -> "${cmd.key} via ${result.transport.badge} (${result.ms} ms)"
            is RouteResult.Failed -> "${cmd.key} failed: ${result.reason}"
            is RouteResult.Unsupported -> "${cmd.key}: ${result.reason}"
        }
    val ok: Boolean get() = result is RouteResult.Sent
}

/** One device's full remote. Every button ends up in [send] → Router. */
class RemoteViewModel(private val graph: AppGraph, private val instanceId: String) : ViewModel() {
    val instance: StateFlow<DeviceInstance?> = graph.repository.state.map { it.device(instanceId) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, graph.repository.current().device(instanceId))

    private val _last = MutableStateFlow<LastSend?>(null)
    val last: StateFlow<LastSend?> = _last

    /** Capabilities of the live driver (empty when there is no transport, e.g. IR device on a phone without IR). */
    val capabilities: StateFlow<Set<Capability>> = instance.map { inst ->
        inst?.let { graph.registry.get(it)?.capabilities } ?: emptySet()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** Why a device has no usable transport, for the greyed-out message. */
    fun unavailableReason(): String? {
        val inst = instance.value ?: return "Device not found"
        val device = graph.registry.get(inst) ?: return "No driver for ${inst.type.displayName}"
        if (device.transports.isNotEmpty()) return null
        val caps = graph.capabilities.state.value
        return when (inst.type) {
            `in`.s123dive.oneremote.core.DeviceType.AIRTEL_HD -> caps.airtelMessage ?: "No code set configured"
            `in`.s123dive.oneremote.core.DeviceType.LG_WEBOS -> caps.lgIrMessage ?: "No transport configured"
            `in`.s123dive.oneremote.core.DeviceType.SONY_BRAVIA -> caps.sonyIrMessage ?: "No transport configured"
        }
    }

    fun supports(cmd: Command): Boolean = cmd.capability in capabilities.value

    fun send(cmd: Command, source: Source = Source.SCREEN) {
        val inst = instance.value ?: return
        viewModelScope.launch {
            val result = graph.router.send(RouteRequest(inst.roomId, Role.EXTRA, cmd, source, instanceId = inst.id))
            _last.value = LastSend(cmd, result)
        }
    }
}
