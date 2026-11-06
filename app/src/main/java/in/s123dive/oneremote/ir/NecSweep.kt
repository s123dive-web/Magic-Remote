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

    /** Philips protocols used by Thomson / Technicolor boxes: standard power command is 12. */
    fun rc5PowerPass(): List<Candidate> = (0..31).map { Candidate(Rc5Encoder.describe(it, 12), IrCode.Rc5(it, 12)) }
    fun rc6PowerPass(): List<Candidate> = (0..255).map { Candidate(Rc6Encoder.describe(it, 12), IrCode.Rc6(it, 12)) }
    fun rc5Full(): List<Candidate> = buildList { for (cmd in 0..63) if (cmd != 12) for (addr in 0..31) add(Candidate(Rc5Encoder.describe(addr, cmd), IrCode.Rc5(addr, cmd))) }
    fun rc6Common(): List<Candidate> = buildList { for (cmd in 0..255) if (cmd != 12) for (addr in 0..15) add(Candidate(Rc6Encoder.describe(addr, cmd), IrCode.Rc6(addr, cmd))) }

    /**
     * The deep sweep, cheapest and likeliest first: RC5 power (32), RC6 power (256), NEC power
     * commands across every address (~6k), then every RC5 code (~2k) and RC6 on low addresses (~4k).
     */
    fun deepSweep(): List<Candidate> = nokia32PowerPass() + rc5PowerPass() + rc6PowerPass() + powerSweep() + rc5Full() + rc6Common()

    /** The per-key sweep for whatever protocol/address Power was found on. */
    fun commandSweepFor(power: IrCode, skip: Set<Int> = emptySet()): List<Candidate> = when (power) {
        is IrCode.Nec -> commandSweep(power.address, power.extended, skip)
        is IrCode.Rc5 -> (0..63).filter { it !in skip }.map { Candidate(Rc5Encoder.describe(power.address, it), IrCode.Rc5(power.address, it)) }
        is IrCode.Rc6 -> (0..255).filter { it !in skip }.map { Candidate(Rc6Encoder.describe(power.address, it), IrCode.Rc6(power.address, it)) }
        is IrCode.Sirc -> (0..127).filter { it !in skip }.map { Candidate(SircEncoder.describe(it, power.address, power.bits), IrCode.Sirc(it, power.address, power.bits)) }
        is IrCode.Nokia32 -> (0..255).filter { it !in skip }.map { Candidate(Nokia32Encoder.describe(power.device, power.sub, power.x, it), IrCode.Nokia32(power.device, power.sub, power.x, it)) }
        is IrCode.Samsung32 -> (0..255).filter { it !in skip }.map { Candidate(SamsungEncoder.describe(power.address, it), IrCode.Samsung32(power.address, it)) }
        is IrCode.Raw -> emptyList()
    }

    fun commandOf(code: IrCode): Int? = when (code) { is IrCode.Nec -> code.command; is IrCode.Rc5 -> code.command; is IrCode.Rc6 -> code.command; is IrCode.Sirc -> code.command; is IrCode.Nokia32 -> code.function; is IrCode.Samsung32 -> code.command; is IrCode.Raw -> null }

    /** Nokia32 power sweep for other Technicolor boxes: F=12 across devices 0..255 (sub 0, X = device, as Airtel uses). */
    fun nokia32PowerPass(): List<Candidate> = (0..255).map { Candidate(Nokia32Encoder.describe(it, 0, it and 0x7F, 12), IrCode.Nokia32(it, 0, it and 0x7F, 12)) }
}
