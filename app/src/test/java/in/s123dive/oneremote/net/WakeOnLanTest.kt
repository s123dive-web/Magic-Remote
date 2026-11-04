package `in`.s123dive.oneremote.net

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WakeOnLanTest {
    @Test
    fun parsesManyMacFormats() {
        val expected = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0x01, 0x02, 0x03)
        assertArrayEquals(expected, WakeOnLan.parseMac("AA:BB:CC:01:02:03"))
        assertArrayEquals(expected, WakeOnLan.parseMac("aa-bb-cc-01-02-03"))
        assertArrayEquals(expected, WakeOnLan.parseMac("aabbcc010203"))
        assertNull(WakeOnLan.parseMac("AA:BB:CC:01:02"))
        assertNull(WakeOnLan.parseMac("zz:bb:cc:01:02:03"))
        assertEquals("AA:BB:CC:01:02:03", WakeOnLan.formatMac(expected))
    }

    @Test
    fun magicPacketLayout() {
        val mac = WakeOnLan.parseMac("AA:BB:CC:01:02:03")!!
        val p = WakeOnLan.magicPacket(mac)
        assertEquals(102, p.size)
        for (i in 0 until 6) assertEquals(0xFF.toByte(), p[i])
        for (r in 0 until 16) assertArrayEquals(mac, p.copyOfRange(6 + r * 6, 12 + r * 6))
    }

    @Test
    fun subnetBroadcast() {
        assertEquals("192.168.1.255", WakeOnLan.subnetBroadcast("192.168.1.20"))
        assertNull(WakeOnLan.subnetBroadcast("nope"))
    }

    @Test
    fun arpLookup() {
        val table = "IP address       HW type     Flags       HW address            Mask     Device\n192.168.1.20     0x1         0x2         aa:bb:cc:01:02:03     *        wlan0\n192.168.1.1      0x1         0x0         00:00:00:00:00:00     *        wlan0\n"
        assertEquals("AA:BB:CC:01:02:03", Arp.lookup("192.168.1.20", table))
        assertNull(Arp.lookup("192.168.1.1", table))
        assertNull(Arp.lookup("192.168.1.20", null))
    }
}
