package `in`.s123dive.oneremote.ir

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One IR key: everything needed to produce a `ConsumerIrManager` pattern. Serialisable so expert-mode codes persist. */
@Serializable
sealed interface IrCode {
    val carrierHz: Int
    /** Pattern for one key press. */
    fun pattern(): IntArray
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

    /** A captured raw pattern (microseconds, mark first) at a given carrier; used for non-NEC boxes. */
    @Serializable @SerialName("raw")
    data class Raw(override val carrierHz: Int, val marks: List<Int>, val label: String = "raw") : IrCode {
        override fun pattern() = marks.toIntArray()
        override fun describe() = "RAW ${label} @${carrierHz / 1000}kHz (${marks.size})"
    }
}
