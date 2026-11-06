package `in`.s123dive.oneremote.ir

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Nokia32 (Mediahighway) protocol, used by Technicolor-built set-top boxes such as Airtel's.
 * IRP: {36k,1p,msb}<6,-10|6,-16|6,-22|6,-28>(15,-10,D:8,S:8,T:1,X:7,F:8,6,^100m)
 * One period at 36 kHz is 27.78 µs; each symbol is a 6-period mark followed by a space of
 * 10/16/22/28 periods encoding two bits (00/01/10/11), MSB first. The toggle bit T flips on
 * every new key press, so two presses of the same key are not read as one long hold.
 */
object Nokia32Encoder {
    const val CARRIER_HZ = 36_000
    const val FRAME_PERIOD_US = 100_000
    private const val UNIT = 1_000_000.0 / CARRIER_HZ
    private fun u(periods: Int) = Math.round(periods * UNIT).toInt()

    fun frame(device: Int, sub: Int, x: Int, function: Int, toggle: Boolean): IntArray {
        require(device in 0..255 && sub in 0..255 && x in 0..127 && function in 0..255) { "Nokia32 field out of range" }
        val word = (device shl 24) or (sub shl 16) or ((if (toggle) 1 else 0) shl 15) or (x shl 8) or function
        val out = ArrayList<Int>(2 + 32 + 1)
        out += u(15); out += u(10)
        for (i in 15 downTo 0) {
            val sym = (word shr (i * 2)) and 0b11
            out += u(6)
            out += u(intArrayOf(10, 16, 22, 28)[sym])
        }
        out += u(6)
        return out.toIntArray()
    }

    /** One key press: the frame padded to 100 ms, then the same frame again (same toggle). */
    fun press(device: Int, sub: Int, x: Int, function: Int, toggle: Boolean): IntArray =
        NecEncoder.pad(frame(device, sub, x, function, toggle), FRAME_PERIOD_US) + frame(device, sub, x, function, toggle)

    fun describe(device: Int, sub: Int, x: Int, function: Int) = "Nokia32 $device.$sub.$x/$function"
}

/** Process-wide toggle bit for protocols that need one (Nokia32, RC5, RC6): flips on every send. */
object IrToggle {
    private val state = AtomicBoolean(false)
    fun next(): Boolean { while (true) { val v = state.get(); if (state.compareAndSet(v, !v)) return !v } }
}
