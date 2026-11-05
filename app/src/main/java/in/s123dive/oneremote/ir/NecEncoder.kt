package `in`.s123dive.oneremote.ir

/**
 * NEC infrared protocol (Airtel HD box, LG TV IR).
 *
 * 38 kHz carrier. Lead 9000 µs mark / 4500 µs space. Bit 0 = 562 mark + 562 space,
 * bit 1 = 562 mark + 1687 space, LSB first. Standard frame = addr, ~addr, cmd, ~cmd;
 * extended frame = 16-bit addr, cmd, ~cmd. Trailing 562 µs mark. Repeat frame
 * 9000/2250/562 every 108 ms while held.
 *
 * Output is the alternating on/off pattern in microseconds that `ConsumerIrManager`
 * expects. Pure function; see NecEncoderTest.
 */
object NecEncoder {
    const val CARRIER_HZ = IrCapability.NEC_CARRIER_HZ
    const val FRAME_PERIOD_US = 108_000
    const val LEAD_MARK = 9000
    const val LEAD_SPACE = 4500
    const val BIT_MARK = 562
    const val ZERO_SPACE = 562
    const val ONE_SPACE = 1687
    const val REPEAT_SPACE = 2250

    /** One full frame ending with the trailing mark. */
    fun frame(address: Int, command: Int, extended: Boolean = false): IntArray {
        require(command in 0..0xFF) { "command must be 0..255, was $command" }
        require(address in 0..(if (extended) 0xFFFF else 0xFF)) { "address out of range: $address" }
        val bits = ArrayList<Boolean>(32)
        if (extended) {
            bits += lsbFirst(address, 16)
        } else {
            bits += lsbFirst(address, 8)
            bits += lsbFirst(address.inv() and 0xFF, 8)
        }
        bits += lsbFirst(command, 8)
        bits += lsbFirst(command.inv() and 0xFF, 8)

        val out = IntArray(2 + bits.size * 2 + 1)
        out[0] = LEAD_MARK
        out[1] = LEAD_SPACE
        var i = 2
        for (b in bits) {
            out[i++] = BIT_MARK
            out[i++] = if (b) ONE_SPACE else ZERO_SPACE
        }
        out[i] = BIT_MARK
        return out
    }

    /** The short "still held" frame. */
    fun repeatFrame(): IntArray = intArrayOf(LEAD_MARK, REPEAT_SPACE, BIT_MARK)

    /**
     * What one key press on a real remote looks like: the full frame, a gap to the 108 ms
     * period, then one repeat frame. Receivers that debounce on the repeat code accept this
     * where a lone frame is ignored.
     */
    fun press(address: Int, command: Int, extended: Boolean = false): IntArray =
        framePadded(address, command, extended) + repeatFrame()

    /** [frame] followed by a gap so the whole thing occupies exactly [FRAME_PERIOD_US]. */
    fun framePadded(address: Int, command: Int, extended: Boolean = false): IntArray =
        pad(frame(address, command, extended), FRAME_PERIOD_US)

    fun describe(address: Int, command: Int, extended: Boolean = false): String =
        if (extended) "NECx 0x%04X/0x%02X".format(address, command) else "NEC 0x%02X/0x%02X".format(address, command)

    internal fun lsbFirst(value: Int, bits: Int): List<Boolean> = List(bits) { (value shr it) and 1 == 1 }

    /** Appends a trailing space so the pattern totals [periodUs]; no-op if already longer. */
    internal fun pad(pattern: IntArray, periodUs: Int): IntArray {
        val total = pattern.sum()
        if (total >= periodUs) return pattern
        return if (pattern.size % 2 == 1) pattern + (periodUs - total) // ends with mark: add a space
        else pattern.copyOf().also { it[it.lastIndex] += periodUs - total } // ends with space: extend it
    }
}
