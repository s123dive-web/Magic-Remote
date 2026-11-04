package `in`.s123dive.oneremote.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SsdpTest {
    @Test
    fun mSearchFormat() {
        val m = Ssdp.mSearch(Ssdp.ST_LG, 3)
        assertTrue(m.startsWith("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\n"))
        assertTrue(m.contains("MAN: \"ssdp:discover\"\r\nMX: 3\r\nST: urn:lge-com:service:webos-second-screen:1\r\n\r\n"))
    }

    @Test
    fun parsesLgResponse() {
        val text = "HTTP/1.1 200 OK\r\nCACHE-CONTROL: max-age=1800\r\nLOCATION: http://192.168.1.20:1442/webos_second_screen.xml\r\nSERVER: WebOS/4.4.0 UPnP/1.0\r\nST: urn:lge-com:service:webos-second-screen:1\r\nUSN: uuid:abc::urn:lge-com:service:webos-second-screen:1\r\n\r\n"
        val r = Ssdp.parse("192.168.1.20", text)!!
        assertTrue(r.isLg); assertFalse(r.isSony)
        assertEquals("192.168.1.20", Ssdp.hostOf(r.location))
        assertEquals("urn:lge-com:service:webos-second-screen:1", r.st)
    }

    @Test
    fun parsesSonyAndIgnoresGarbage() {
        val text = "HTTP/1.1 200 OK\nST: urn:schemas-sony-com:service:ScalarWebAPI:1\nLOCATION: http://192.168.1.21:52323/dmr.xml\nUSN: uuid:xyz::urn:schemas-sony-com:service:ScalarWebAPI:1\n\n"
        val r = Ssdp.parse("192.168.1.21", text)!!
        assertTrue(r.isSony); assertFalse(r.isLg)
        assertNull(Ssdp.parse("x", "garbage"))
        assertNull(Ssdp.parse("x", ""))
    }

    @Test
    fun xmlHelpers() {
        val xml = "<root><device><friendlyName>Living Room TV</friendlyName><modelName>KDL-43W6600</modelName><av:X_ScalarWebAPI_BaseURL>http://192.168.1.21/sony</av:X_ScalarWebAPI_BaseURL></device></root>"
        assertEquals("Living Room TV", Ssdp.friendlyName(xml))
        assertEquals("KDL-43W6600", Ssdp.modelName(xml))
        assertEquals("http://192.168.1.21/sony", Ssdp.sonyBaseUrl(xml))
        assertNull(Ssdp.friendlyName("<x/>"))
    }
}
