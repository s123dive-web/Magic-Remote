package `in`.s123dive.oneremote.ir

/**
 * RC5 (Philips, 36 kHz, Manchester): 14 bits at 889 µs per half-bit — start 1, field (1 for
 * commands 0–63), toggle, 5-bit address, 6-bit command, MSB first. Logical 1 = space then mark,
 * 0 = mark then space. Frame 24.9 ms, repeat period 114 ms. Used by Thomson/Technicolor boxes.
 */
object Rc5Encoder {
    const val CARRIER_HZ = 36_000
    const val HALF_US = 889
    const val FRAME_PERIOD_US = 114_000

    fun frame(address: Int, command: Int, toggle: Boolean = false): IntArray {
        require(address in 0..31) { "RC5 address must be 0..31, was $address" }
        require(command in 0..127) { "RC5 command must be 0..127, was $command" }
        val bits = ArrayList<Boolean>(14)
        bits += true                       // start
        bits += command < 64               // field: inverted 7th command bit
        bits += toggle
        for (i in 4 downTo 0) bits += (address shr i) and 1 == 1
        for (i in 5 downTo 0) bits += (command shr i) and 1 == 1
        return Manchester.encode(bits, HALF_US, oneIsSpaceThenMark = true)
    }

    /** One key press: the frame padded to the 114 ms period, sent twice (toggle unchanged, like a short press). */
    fun press(address: Int, command: Int): IntArray {
        val f = NecEncoder.pad(frame(address, command), FRAME_PERIOD_US)
        return f + frame(address, command)
    }

    fun describe(address: Int, command: Int) = "RC5 $address/$command"
}

/**
 * RC6 mode 0 (Philips, 36 kHz): leader 2666 µs mark + 889 space, start bit 1, three mode bits
 * (000), a double-length trailer/toggle bit, then 8-bit address and 8-bit command MSB first.
 * Bit 1 = mark then space (444 µs each), 0 = space then mark. Signal-free gap 2666 µs.
 */
object Rc6Encoder {
    const val CARRIER_HZ = 36_000
    const val T_US = 444
    const val FRAME_PERIOD_US = 106_000

    fun frame(address: Int, command: Int, toggle: Boolean = false): IntArray {
        require(address in 0..255) { "RC6 address must be 0..255, was $address" }
        require(command in 0..255) { "RC6 command must be 0..255, was $command" }
        // Build half-bit levels: true = mark.
        val levels = ArrayList<Boolean>()
        repeat(6) { levels += true }; repeat(2) { levels += false }   // leader 2666/889
        fun bit(b: Boolean) { levels += b; levels += !b }              // 1 = mark,space ; 0 = space,mark
        bit(true)                                                      // start
        bit(false); bit(false); bit(false)                             // mode 0
        // trailer (toggle) bit is double length
        levels += toggle; levels += toggle; levels += !toggle; levels += !toggle
        for (i in 7 downTo 0) bit((address shr i) and 1 == 1)
        for (i in 7 downTo 0) bit((command shr i) and 1 == 1)
        return Manchester.levelsToPattern(levels, T_US)
    }

    fun press(address: Int, command: Int): IntArray = NecEncoder.pad(frame(address, command), FRAME_PERIOD_US) + frame(address, command)

    fun describe(address: Int, command: Int) = "RC6 $address/$command"
}

/** Manchester helpers shared by RC5/RC6: merge equal half-bit levels into mark/space durations, mark first. */
internal object Manchester {
    fun encode(bits: List<Boolean>, halfUs: Int, oneIsSpaceThenMark: Boolean): IntArray {
        val levels = ArrayList<Boolean>(bits.size * 2)
        for (b in bits) {
            val first = if (oneIsSpaceThenMark) !b else b
            levels += first; levels += !first
        }
        return levelsToPattern(levels, halfUs)
    }

    /** Drops a leading space (the pattern must start with a mark) and merges runs. */
    fun levelsToPattern(levels: List<Boolean>, unitUs: Int): IntArray {
        val start = levels.indexOfFirst { it }
        require(start >= 0) { "no mark in pattern" }
        val out = ArrayList<Int>()
        var level = true; var run = 0
        for (l in levels.drop(start)) {
            if (l == level) run++ else { out += run * unitUs; level = l; run = 1 }
        }
        out += run * unitUs
        if (!level) out.removeAt(out.lastIndex) // trailing space is just the gap
        return out.toIntArray()
    }
}
