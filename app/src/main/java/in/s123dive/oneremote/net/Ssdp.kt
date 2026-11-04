package `in`.s123dive.oneremote.net

/** Pure SSDP helpers: M-SEARCH builder and response parser. Tested without sockets. */
object Ssdp {
    const val ADDRESS = "239.255.255.250"
    const val PORT = 1900
    const val ST_LG = "urn:lge-com:service:webos-second-screen:1"
    const val ST_SONY = "urn:schemas-sony-com:service:ScalarWebAPI:1"
    const val ST_ALL = "ssdp:all"

    fun mSearch(st: String, mx: Int = 2): String =
        "M-SEARCH * HTTP/1.1\r\nHOST: $ADDRESS:$PORT\r\nMAN: \"ssdp:discover\"\r\nMX: $mx\r\nST: $st\r\n\r\n"

    /** A parsed SSDP response (or NOTIFY). Header names are upper-cased. */
    data class Response(val from: String, val headers: Map<String, String>, val raw: String) {
        val st: String? get() = headers["ST"] ?: headers["NT"]
        val usn: String? get() = headers["USN"]
        val location: String? get() = headers["LOCATION"]
        val server: String? get() = headers["SERVER"]
        val isLg: Boolean get() = st == ST_LG || (server?.contains("webOS", ignoreCase = true) == true) || (usn?.contains("lge", true) == true)
        val isSony: Boolean get() = st == ST_SONY || (usn?.contains("sony", ignoreCase = true) == true) || (server?.contains("Sony", true) == true)
    }

    fun parse(from: String, text: String): Response? {
        val lines = text.split("\r\n", "\n")
        val first = lines.firstOrNull()?.trim() ?: return null
        if (!first.startsWith("HTTP/1.1") && !first.startsWith("NOTIFY")) return null
        val headers = HashMap<String, String>()
        for (line in lines.drop(1)) {
            val i = line.indexOf(':')
            if (i <= 0) continue
            headers[line.substring(0, i).trim().uppercase()] = line.substring(i + 1).trim()
        }
        return Response(from, headers, text)
    }

    /** Extracts the host part of a LOCATION url, e.g. `http://192.168.1.20:1234/x.xml` -> `192.168.1.20`. */
    fun hostOf(url: String?): String? = url?.let { Regex("^[a-z]+://([^/:]+)").find(it)?.groupValues?.get(1) }

    /** Best-effort friendly name / model from a UPnP device description XML. */
    fun friendlyName(xml: String): String? = tag(xml, "friendlyName")
    fun modelName(xml: String): String? = tag(xml, "modelName")
    fun sonyBaseUrl(xml: String): String? = tag(xml, "av:X_ScalarWebAPI_BaseURL") ?: tag(xml, "X_ScalarWebAPI_BaseURL")

    private fun tag(xml: String, name: String): String? =
        Regex("<$name>([^<]*)</$name>").find(xml)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
}
