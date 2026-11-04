package `in`.s123dive.oneremote.ir.codesets

import `in`.s123dive.oneremote.core.Command
import `in`.s123dive.oneremote.core.DeviceType
import `in`.s123dive.oneremote.ir.IrCode
import kotlinx.serialization.Serializable

/**
 * A complete IR key map for one device model, keyed by [Command.key].
 * Built-in codesets live in [Codesets]; expert-mode codesets are stored in the device config.
 */
@Serializable
data class Codeset(
    val id: String,
    val name: String,
    val type: DeviceType,
    val codes: Map<String, IrCode>,
    /** Where the codes came from, shown in Device info. */
    val source: String = "",
    /** Delay between digits when expanding Channel(n). */
    val digitDelayMs: Int = 250,
    /** Whether the box needs OK after the digits of a channel number. */
    val okAfterChannel: Boolean = true,
) {
    fun code(cmd: Command): IrCode? = codes[cmd.key]
    val commands: List<Command> get() = codes.keys.mapNotNull { Command.fromKey(it) }
    val carrierHz: Int get() = codes.values.firstOrNull()?.carrierHz ?: 38_000

    companion object {
        fun nec(id: String, name: String, type: DeviceType, address: Int, extended: Boolean, source: String, digitDelayMs: Int = 250, okAfterChannel: Boolean = true, vararg entries: Pair<Command, Int>) =
            Codeset(id, name, type, entries.associate { (c, code) -> c.key to IrCode.Nec(address, code, extended) }, source, digitDelayMs, okAfterChannel)
    }
}
