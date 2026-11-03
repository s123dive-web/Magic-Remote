package `in`.s123dive.oneremote.system

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import `in`.s123dive.oneremote.ir.IrCapability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.Inet4Address

/** A snapshot of what this phone can do right now. Drives every "greyed out" message. */
data class PhoneCapabilities(
    val ir: IrCapability,
    val wifiConnected: Boolean,
    val ipv4: String?,
) {
    val airtelMessage: String? get() = if (ir.hasIr) null else "This box is IR-only; this phone has no IR blaster"
    val sonyIrMessage: String? get() = when {
        !ir.hasIr -> "Sony IR fallback unavailable: no IR blaster"
        !ir.supports40k -> "Sony IR fallback unavailable: this blaster does not support 40 kHz"
        else -> null
    }
    val lgIrMessage: String? get() = when {
        !ir.hasIr -> "LG IR fallback unavailable: no IR blaster"
        !ir.supports38k -> "LG IR fallback unavailable: this blaster does not support 38 kHz"
        else -> null
    }
}

/** IR capability is fixed for the life of the process; Wi-Fi state is re-read on [refresh]. */
class CapabilityDetector(private val context: Context, private val ir: IrCapability) {
    private val _state = MutableStateFlow(snapshot())
    val state: StateFlow<PhoneCapabilities> = _state

    fun refresh(): PhoneCapabilities = snapshot().also { _state.value = it }

    private fun snapshot(): PhoneCapabilities {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = cm?.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        val wifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
        val ip = network?.let { cm.getLinkProperties(it) }
            ?.linkAddresses
            ?.map { it.address }
            ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress
        return PhoneCapabilities(ir = ir, wifiConnected = wifi, ipv4 = ip)
    }
}
