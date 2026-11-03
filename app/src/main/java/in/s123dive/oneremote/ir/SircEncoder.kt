package `in`.s123dive.oneremote.ir

/**
 * Sony SIRC infrared protocol.
 *
 * 40 kHz carrier. Header 2400 µs mark + 600 space. Bit 1 = 1200 mark + 600 space,
 * bit 0 = 600 mark + 600 space, LSB first. 12-bit = 7-bit command + 5-bit address;
 * 15-bit = 7 + 8; 20-bit = 7 + 5 + 8-bit extended. Sony receivers expect the frame
 * three times at a 45 ms period.
 *
 * Pure function; see SircEncoderTest.
 */
object SircEncoder {
    const val CARRIER_HZ = IrCapability.SIRC_CARRIER_HZ
    const val FRAME_PERIOD_US = 45_000
    const val REPEATS = 3
    const val HEADER_MARK = 2400
    const val SPACE = 600
    const val ONE_MARK = 1200
    const val ZERO_MARK = 600

    /** One frame (header + bits); ends with the 600 µs space of the last bit. */
    fun frame(command: Int, address: Int, bits: Int = 12, extended: Int = 0): IntArray {
        require(bits == 12 || bits == 15 || bits == 20) { "bits must be 12, 15 or 20, was $bits" }
        require(command in 0..0x7F) { "command must be 0..127, was $command" }
        val addrBits = if (bits == 15) 8 else 5
        require(address in 0 until (1 shl addrBits)) { "address must fit $addrBits bits, was $address" }
        require(extended in 0..0xFF) { "extended must be 0..255, was $extended" }

        val payload = ArrayList<Boolean>(bits)
        payload += NecEncoder.lsbFirst(command, 7)
        payload += NecEncoder.lsbFirst(address, addrBits)
        if (bits == 20) payload += NecEncoder.lsbFirst(extended, 8)

        val out = IntArray(2 + payload.size * 2)
        out[0] = HEADER_MARK
        out[1] = SPACE
        var i = 2
        for (b in payload) {
            out[i++] = if (b) ONE_MARK else ZERO_MARK
            out[i++] = SPACE
        }
        return out
    }

    /** [REPEATS] frames, each padded to [FRAME_PERIOD_US]: what a single key press sends. */
    fun burst(command: Int, address: Int, bits: Int = 12, extended: Int = 0, repeats: Int = REPEATS): IntArray {
        val one = NecEncoder.pad(frame(command, address, bits, extended), FRAME_PERIOD_US)
        val out = IntArray(one.size * repeats)
        repeat(repeats) { r -> one.copyInto(out, r * one.size) }
        return out
    }

    fun describe(command: Int, address: Int, bits: Int = 12, extended: Int = 0): String =
        if (bits == 20) "SIRC20 $command/$address/$extended" else "SIRC$bits $command/$address"
}
