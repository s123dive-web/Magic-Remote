package `in`.s123dive.oneremote.net

import java.io.File

/**
 * Best-effort MAC lookup from the ARP table. Android 10+ blocks /proc/net/arp for apps,
 * so this usually returns null there; callers fall back to the TV's own API or manual entry.
 */
object Arp {
    fun lookup(ip: String, table: String? = readTable()): String? {
        if (table == null) return null
        for (line in table.lines().drop(1)) {
            val cols = line.trim().split(Regex("\\s+"))
            if (cols.size >= 4 && cols[0] == ip) {
                val mac = cols[3]
                if (mac != "00:00:00:00:00:00" && WakeOnLan.parseMac(mac) != null) return mac.uppercase()
            }
        }
        return null
    }

    fun readTable(): String? = try { File("/proc/net/arp").takeIf { it.canRead() }?.readText() } catch (_: Exception) { null }
}
