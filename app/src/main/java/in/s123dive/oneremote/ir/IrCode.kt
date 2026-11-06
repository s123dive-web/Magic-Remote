package `in`.s123dive.oneremote.ir

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One IR key: everything needed to produce a `ConsumerIrManager` pattern. Serialisable so expert-mode codes persist. */
@Serializable
sealed interface IrCode {
    val carrierHz: Int
    /** Pattern for one key press. */
    fun pattern(): IntArray
    /** Pattern for one key press with an explicit toggle bit (protocols without one ignore it). */
    fun pattern(toggle: Boolean): IntArray = pattern()
    /** Short summary for the log, e.g. `NEC 0x04/0x08`. */
    fun describe(): String

    @Serializable @SerialName("nec")
    data class Nec(val address: Int, val command: Int, val extended: Boolean = false) : IrCode {
        override val carrierHz get() = NecEncoder.CARRIER_HZ
        override fun pattern() = NecEncoder.press(address, command, extended)
        override fun describe() = NecEncoder.describe(address, command, extended)
    }

    @Serializable @SerialName("sirc")
    data class Sirc(val command: Int, val address: Int, val bits: Int = 12, val extended: Int = 0) : IrCode {
        override val carrierHz get() = SircEncoder.CARRIER_HZ
        override fun pattern() = SircEncoder.burst(command, address, bits, extended)
        override fun describe() = SircEncoder.describe(command, address, bits, extended)
    }

    @Serializable @SerialName("rc5")
    data class Rc5(val address: Int, val command: Int) : IrCode {
        override val carrierHz get() = Rc5Encoder.CARRIER_HZ
        override fun pattern() = Rc5Encoder.press(address, command)
        override fun pattern(toggle: Boolean) = NecEncoder.pad(Rc5Encoder.frame(address, command, toggle), Rc5Encoder.FRAME_PERIOD_US) + Rc5Encoder.frame(address, command, toggle)
        override fun describe() = Rc5Encoder.describe(address, command)
    }

    @Serializable @SerialName("rc6")
    data class Rc6(val address: Int, val command: Int) : IrCode {
        override val carrierHz get() = Rc6Encoder.CARRIER_HZ
        override fun pattern() = Rc6Encoder.press(address, command)
        override fun pattern(toggle: Boolean) = NecEncoder.pad(Rc6Encoder.frame(address, command, toggle), Rc6Encoder.FRAME_PERIOD_US) + Rc6Encoder.frame(address, command, toggle)
        override fun describe() = Rc6Encoder.describe(address, command)
    }

    @Serializable @SerialName("samsung32")
    data class Samsung32(val address: Int, val command: Int) : IrCode {
        override val carrierHz get() = SamsungEncoder.CARRIER_HZ
        override fun pattern() = SamsungEncoder.press(address, command)
        override fun describe() = SamsungEncoder.describe(address, command)
    }

    /** Nokia32 (Technicolor / Airtel boxes): device, sub-device, X and an 8-bit function. */
    @Serializable @SerialName("nokia32")
    data class Nokia32(val device: Int, val sub: Int, val x: Int, val function: Int) : IrCode {
        override val carrierHz get() = Nokia32Encoder.CARRIER_HZ
        override fun pattern() = Nokia32Encoder.press(device, sub, x, function, toggle = false)
        override fun pattern(toggle: Boolean) = Nokia32Encoder.press(device, sub, x, function, toggle)
        override fun describe() = Nokia32Encoder.describe(device, sub, x, function)
    }

    /** A captured raw pattern (microseconds, mark first) at a given carrier; used for non-NEC boxes. */
    @Serializable @SerialName("raw")
    data class Raw(override val carrierHz: Int, val marks: List<Int>, val label: String = "raw") : IrCode {
        override fun pattern() = marks.toIntArray()
        override fun describe() = "RAW ${label} @${carrierHz / 1000}kHz (${marks.size})"
    }
}
