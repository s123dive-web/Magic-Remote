package `in`.s123dive.oneremote.ir

/**
 * Brute-force helpers for boxes that are in no database. The phone cannot receive IR, so the
 * only way to find a code is to transmit candidates and let the user say when the box reacts.
 */
object NecSweep {
    data class Candidate(val label: String, val code: IrCode, val codesetId: String? = null)

    /** Power command bytes seen on NEC set-top boxes, most common first (irdb + Flipper-IRDB tallies). */
    val POWER_COMMANDS: List<Int> = listOf(0x0C, 0x02, 0x08, 0x12, 0x10, 0x15, 0x40, 0x45, 0x48, 0x4D, 0x1C, 0x00, 0x01, 0x03, 0x0A, 0x0F, 0x14, 0x18, 0x19, 0x1A, 0x20, 0x21, 0x80, 0xDE)

    /** Standard NEC addresses, most common first, then the rest. */
    val ADDRESSES: List<Int> = (listOf(0x00, 0x01, 0x02, 0x04, 0x08, 0x10, 0x20, 0x40, 0x80, 0x86, 0x7F, 0x44, 0x48, 0x55, 0xAA, 0xBF, 0xDE, 0x18, 0x1C, 0x30, 0x38, 0x3C, 0x50, 0x60, 0x70, 0xA0, 0xC0, 0xF0, 0xFF) + (0..0xFF)).distinct()

    /** Known TV codes that must not be mistaken for the box (LG TV Power 0x04/0x08 and discrete on/off). */
    val TV_CODES: Set<Pair<Int, Int>> = setOf(0x04 to 0x08, 0x04 to 0xC4, 0x04 to 0xC5)

    /** Every (address, power command) pair: common commands across all addresses, common addresses first. */
    fun powerSweep(): List<Candidate> = buildList {
        for (cmd in POWER_COMMANDS) for (addr in ADDRESSES) if ((addr to cmd) !in TV_CODES) add(Candidate("NEC 0x%02X/0x%02X".format(addr, cmd), IrCode.Nec(addr, cmd)))
    }

    /** All 256 command bytes on one known address, skipping the ones already mapped. */
    fun commandSweep(address: Int, extended: Boolean, skip: Set<Int> = emptySet()): List<Candidate> =
        (0..0xFF).filter { it !in skip }.map { Candidate(NecEncoder.describe(address, it, extended), IrCode.Nec(address, it, extended)) }
}
