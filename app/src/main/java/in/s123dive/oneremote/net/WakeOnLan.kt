package `in`.s123dive.oneremote.net

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/** Wake-on-LAN magic packet: 6×0xFF followed by the MAC 16 times. Sent as UDP broadcast on port 9 (and 7). */
object WakeOnLan {
    fun parseMac(text: String): ByteArray? {
        val hex = text.replace(Regex("[^0-9A-Fa-f]"), "")
        if (hex.length != 12) return null
        return ByteArray(6) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    fun formatMac(mac: ByteArray): String = mac.joinToString(":") { "%02X".format(it) }

    fun magicPacket(mac: ByteArray): ByteArray {
        require(mac.size == 6)
        val out = ByteArray(6 + 16 * 6)
        for (i in 0 until 6) out[i] = 0xFF.toByte()
        for (r in 0 until 16) mac.copyInto(out, 6 + r * 6)
        return out
    }

    /** Sends the packet to the broadcast address and, when known, directly to the host's subnet broadcast. Throws on IO error. */
    fun send(mac: String, broadcast: String = "255.255.255.255", extraTargets: List<String> = emptyList()) {
        val bytes = parseMac(mac) ?: throw IllegalArgumentException("bad MAC '$mac'")
        val packet = magicPacket(bytes)
        DatagramSocket().use { socket ->
            socket.broadcast = true
            for (target in listOf(broadcast) + extraTargets) {
                for (port in intArrayOf(9, 7)) {
                    socket.send(DatagramPacket(packet, packet.size, InetAddress.getByName(target), port))
                }
            }
        }
    }

    /** `192.168.1.20` -> `192.168.1.255` (assumes /24, the common home case). */
    fun subnetBroadcast(ipv4: String): String? {
        val parts = ipv4.split('.')
        if (parts.size != 4) return null
        return parts.take(3).joinToString(".") + ".255"
    }
}
