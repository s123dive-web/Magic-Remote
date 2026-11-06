package `in`.s123dive.oneremote.ir

/** Samsung32: like NEC but 4.5 ms / 4.5 ms header and the 8-bit address sent twice (no inverse). 38 kHz. */
object SamsungEncoder {
    const val CARRIER_HZ = 38_000
    const val FRAME_PERIOD_US = 108_000

    fun frame(address: Int, command: Int): IntArray {
        require(address in 0..255 && command in 0..255)
        val bits = NecEncoder.lsbFirst(address, 8) + NecEncoder.lsbFirst(address, 8) + NecEncoder.lsbFirst(command, 8) + NecEncoder.lsbFirst(command.inv() and 0xFF, 8)
        val out = IntArray(2 + bits.size * 2 + 1)
        out[0] = 4500; out[1] = 4500
        var i = 2
        for (b in bits) { out[i++] = 560; out[i++] = if (b) 1690 else 560 }
        out[i] = 560
        return out
    }

    fun press(address: Int, command: Int): IntArray = NecEncoder.pad(frame(address, command), FRAME_PERIOD_US) + frame(address, command)
    fun describe(address: Int, command: Int) = "Samsung32 0x%02X/0x%02X".format(address, command)
}
