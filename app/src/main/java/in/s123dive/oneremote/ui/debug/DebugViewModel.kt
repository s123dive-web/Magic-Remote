package `in`.s123dive.oneremote.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.s123dive.oneremote.AppGraph
import `in`.s123dive.oneremote.core.RouteResult
import `in`.s123dive.oneremote.core.TransportKind
import `in`.s123dive.oneremote.ir.NecEncoder
import `in`.s123dive.oneremote.ir.SircEncoder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A canned IR burst used to prove a carrier frequency works on this phone. */
data class CarrierTest(val id: String, val label: String, val carrierHz: Int, val payload: String, val pattern: IntArray)

val CARRIER_TESTS: List<CarrierTest> = listOf(
    CarrierTest(
        id = "lg38", label = "LG Power @ 38 kHz", carrierHz = NecEncoder.CARRIER_HZ,
        payload = NecEncoder.describe(0x04, 0x08) + " @38kHz +repeat", pattern = NecEncoder.press(0x04, 0x08),
    ),
    CarrierTest(
        id = "sony40", label = "Sony Power @ 40 kHz", carrierHz = SircEncoder.CARRIER_HZ,
        payload = SircEncoder.describe(21, 1) + " @40kHz x3", pattern = SircEncoder.burst(21, 1),
    ),
)

class DebugViewModel(private val graph: AppGraph) : ViewModel() {
    val log = graph.log.entries
    val capabilities = graph.capabilities.state
    val appState = graph.repository.state

    private val _results = MutableStateFlow<Map<String, String>>(emptyMap())
    val carrierResults: StateFlow<Map<String, String>> = _results

    private val _running = MutableStateFlow<String?>(null)
    val running: StateFlow<String?> = _running

    fun runCarrierTest(test: CarrierTest) {
        if (_running.value != null) return
        _running.value = test.id
        viewModelScope.launch {
            val result = graph.router.diagnostic(test.label, TransportKind.IR, test.payload) {
                graph.ir.send(test.carrierHz, test.pattern, test.payload)
            }
            val text = when (result) {
                is RouteResult.Sent -> "sent in ${result.ms} ms (${result.payload})"
                is RouteResult.Failed -> "failed: ${result.reason}"
                is RouteResult.Unsupported -> "unsupported: ${result.reason}"
            }
            _results.update { it + (test.id to text) }
            _running.value = null
        }
    }

    fun clearLog() = graph.log.clear()

    fun exportText(formatTime: (Long) -> String): String {
        val caps = graph.capabilities.state.value
        val header = buildString {
            appendLine("OneRemote send log")
            appendLine("IR: ${if (caps.ir.hasIr) "present" else "none"}; ranges: ${caps.ir.describeRanges()}")
            appendLine("Wi-Fi: ${if (caps.wifiConnected) "connected" else "not connected"}; IP: ${caps.ipv4 ?: "-"}")
            appendLine("time\troom\trole\tcmd\ttransport\tpayload\tresult\tms")
        }
        return header + graph.log.export(formatTime)
    }
}
