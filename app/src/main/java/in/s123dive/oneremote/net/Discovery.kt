package `in`.s123dive.oneremote.net

import android.content.Context
import android.net.wifi.WifiManager
import `in`.s123dive.oneremote.core.DeviceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/** Something on the LAN that looks like a TV we can drive. */
data class Found(
    val type: DeviceType,
    val ip: String,
    val name: String,
    val model: String? = null,
    val via: String,
    val raw: String = "",
)

/**
 * SSDP M-SEARCH (with the multicast lock Android needs) for LG and Sony, then a /24 probe of
 * tcp:3000 (LG) and `POST /sony/system getSystemInformation` (Sony) for TVs that ignore SSDP.
 * Raw responses are kept for the Debug console. Manual IP is always offered by the UI.
 */
class Discovery(private val context: Context) {
    data class State(val running: Boolean = false, val found: List<Found> = emptyList(), val rawSsdp: List<String> = emptyList(), val note: String? = null)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    suspend fun run(localIpv4: String?, ssdpMs: Long = 3000, scanSubnet: Boolean = true) {
        _state.value = State(running = true)
        val found = LinkedHashMap<String, Found>()
        val raw = ArrayList<String>()
        try {
            ssdp(ssdpMs) { r ->
                raw += "${r.from}\n${r.raw.trim()}"
                val host = Ssdp.hostOf(r.location) ?: r.from
                val type = when { r.isLg -> DeviceType.LG_WEBOS; r.isSony -> DeviceType.SONY_BRAVIA; else -> null } ?: return@ssdp
                if (found.containsKey("$type@$host")) return@ssdp
                val xml = r.location?.let { fetch(it) } ?: ""
                val name = Ssdp.friendlyName(xml) ?: type.displayName
                found["$type@$host"] = Found(type, host, name, Ssdp.modelName(xml), via = "SSDP", raw = r.raw)
                _state.update { it.copy(found = found.values.toList(), rawSsdp = raw.toList()) }
            }
        } catch (e: Exception) {
            _state.update { it.copy(note = "SSDP failed: ${e.message}") }
        }
        _state.update { it.copy(found = found.values.toList(), rawSsdp = raw.toList()) }
        if (scanSubnet && localIpv4 != null) {
            val prefix = localIpv4.substringBeforeLast('.')
            probeSubnet(prefix, localIpv4) { f -> if (!found.containsKey("${f.type}@${f.ip}")) { found["${f.type}@${f.ip}"] = f; _state.update { it.copy(found = found.values.toList()) } } }
        } else if (scanSubnet) {
            _state.update { it.copy(note = (it.note ?: "") + " No local IPv4: subnet scan skipped.") }
        }
        _state.update { it.copy(running = false, found = found.values.toList()) }
    }

    private suspend fun ssdp(ms: Long, onResponse: suspend (Ssdp.Response) -> Unit) = withContext(Dispatchers.IO) {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = wifi?.createMulticastLock("oneremote-ssdp")?.apply { setReferenceCounted(false); acquire() }
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = 500
                socket.broadcast = true
                val group = InetAddress.getByName(Ssdp.ADDRESS)
                for (st in listOf(Ssdp.ST_LG, Ssdp.ST_SONY, Ssdp.ST_ALL)) {
                    val msg = Ssdp.mSearch(st).toByteArray()
                    repeat(2) { socket.send(DatagramPacket(msg, msg.size, group, Ssdp.PORT)) }
                }
                val buf = ByteArray(4096)
                val deadline = System.currentTimeMillis() + ms
                while (System.currentTimeMillis() < deadline) {
                    val packet = DatagramPacket(buf, buf.size)
                    try { socket.receive(packet) } catch (_: SocketTimeoutException) { continue }
                    val text = String(packet.data, 0, packet.length)
                    Ssdp.parse(packet.address.hostAddress ?: "?", text)?.let { onResponse(it) }
                }
            }
        } finally {
            lock?.release()
        }
    }

    private suspend fun fetch(url: String): String? = withContext(Dispatchers.IO) {
        runCatching { Http.plain.newCall(Request.Builder().url(url).get().build()).execute().use { it.body?.string() } }.getOrNull()
    }

    private suspend fun probeSubnet(prefix: String, self: String, onFound: suspend (Found) -> Unit) = coroutineScope {
        (1..254).map { "$prefix.$it" }.filter { it != self }.chunked(32).forEach { chunk ->
            chunk.map { ip -> async(Dispatchers.IO) { probe(ip) } }.awaitAll().filterNotNull().forEach { onFound(it) }
        }
    }

    private suspend fun probe(ip: String): Found? = withTimeoutOrNull(1500) {
        if (tcpOpen(ip, 3000) || tcpOpen(ip, 3001)) return@withTimeoutOrNull Found(DeviceType.LG_WEBOS, ip, "LG TV (port 3000)", via = "scan")
        val sony = `in`.s123dive.oneremote.sony.SonyClient.probe(ip)
        if (sony != null) Found(DeviceType.SONY_BRAVIA, ip, sony.model ?: "Sony Bravia", sony.model, via = "scan") else null
    }

    private fun tcpOpen(ip: String, port: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(ip, port), 300); true }
    } catch (_: Exception) { false }
}
