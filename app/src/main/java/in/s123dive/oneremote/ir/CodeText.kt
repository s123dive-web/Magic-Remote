package `in`.s123dive.oneremote.ir

/**
 * Parsers for code formats people find online: Pronto hex (irplus, RemoteCentral) and Flipper
 * `.ir` blocks (parsed NEC/NECext/RC5/RC6/SIRC/Samsung32, or raw). Pure; see CodeTextTest.
 */
object CodeText {
    /** Pronto learned format: 0000, frequency code, once-pair count, repeat-pair count, then pairs of on/off in carrier periods. */
    fun parsePronto(text: String, label: String = "pronto"): IrCode.Raw? {
        val words = text.trim().split(Regex("[\\s,]+")).filter { it.isNotBlank() }.map { it.removePrefix("0x").toIntOrNull(16) ?: return null }
        if (words.size < 6 || words[0] != 0) return null
        val freqCode = words[1].takeIf { it > 0 } ?: return null
        val periodUs = freqCode * 0.241246
        val once = words[2]; val repeat = words[3]
        val body = words.drop(4)
        val pairs = if (once > 0) body.take(once * 2) else body.take(repeat * 2)
        if (pairs.size < 2) return null
        val us = pairs.map { Math.round(it * periodUs).toInt().coerceAtLeast(1) }.toMutableList()
        if (us.size % 2 == 0) us.removeAt(us.lastIndex) // the final off-time is the gap to the next frame
        return IrCode.Raw(Math.round(1_000_000.0 / periodUs).toInt(), us, label)
    }

    /** One Flipper `.ir` entry (the lines from `name:` to the next `#`). Returns the key name and the code. */
    fun parseFlipper(block: String): Pair<String, IrCode>? {
        val f = block.lines().mapNotNull { l -> l.indexOf(':').takeIf { it > 0 }?.let { l.substring(0, it).trim().lowercase() to l.substring(it + 1).trim() } }.toMap()
        val name = f["name"] ?: "key"
        return when (f["type"]) {
            "raw" -> {
                val hz = f["frequency"]?.toIntOrNull() ?: 38000
                val data = f["data"]?.split(Regex("\\s+"))?.mapNotNull { it.toIntOrNull() } ?: return null
                val us = if (data.size % 2 == 0) data.dropLast(1) else data
                if (us.isEmpty()) null else name to IrCode.Raw(hz, us, name)
            }
            "parsed" -> {
                val a = bytes(f["address"] ?: return null); val c = bytes(f["command"] ?: return null)
                if (a.isEmpty() || c.isEmpty()) return null
                val code: IrCode = when (f["protocol"]?.lowercase()) {
                    "nec" -> IrCode.Nec(a[0], c[0], extended = false)
                    "necext" -> IrCode.Nec(a[0] or (a.getOrElse(1) { 0 } shl 8), c[0], extended = true)
                    "nec42", "nec42ext" -> return null
                    "rc5", "rc5x" -> IrCode.Rc5(a[0] and 0x1F, c[0] and 0x7F)
                    "rc6" -> IrCode.Rc6(a[0], c[0])
                    "sirc" -> IrCode.Sirc(c[0] and 0x7F, a[0] and 0x1F, 12)
                    "sirc15" -> IrCode.Sirc(c[0] and 0x7F, a[0], 15)
                    "sirc20" -> IrCode.Sirc(c[0] and 0x7F, a[0] and 0x1F, 20, a.getOrElse(1) { 0 })
                    "samsung32" -> IrCode.Samsung32(a[0], c[0])
                    else -> return null
                }
                name to code
            }
            else -> null
        }
    }

    /** Every entry of a whole Flipper file. */
    fun parseFlipperFile(text: String): List<Pair<String, IrCode>> =
        text.split(Regex("(?m)^#.*$")).mapNotNull { if (it.contains("name:")) parseFlipper(it) else null }

    private fun bytes(s: String) = s.trim().split(Regex("\\s+")).mapNotNull { it.toIntOrNull(16) }
}
