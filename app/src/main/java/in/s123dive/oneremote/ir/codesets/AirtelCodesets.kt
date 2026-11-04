package `in`.s123dive.oneremote.ir.codesets

import `in`.s123dive.oneremote.core.Command
import `in`.s123dive.oneremote.core.DeviceType
import `in`.s123dive.oneremote.ir.IrCode

/**
 * Airtel Digital TV set-top boxes.
 *
 * `airtel-stb2`: Flipper-IRDB `_Converted_/IR_Plus/A/AIRTEL/STB2.ir` — extended NEC, 16-bit address
 * 0xA1DE (Flipper writes it as `DE A1 00 00`). In that file the arrows share codes with CH/VOL,
 * which is how the physical remote behaves on those boxes.
 *
 * `airtel-s8300`: Flipper-IRDB `S8300.ir` — a different, non-NEC box captured as raw timings @38 kHz.
 *
 * [scanList] adds the NEC set-top-box Power codes harvested from probonopd/irdb (Satellite / Cable
 * categories) so the scanner can try other boxes; each entry carries its irdb path for provenance.
 * Expert mode covers anything not listed.
 */
object AirtelCodesets {
    const val ID_STB2 = "airtel-stb2"
    const val ID_S8300 = "airtel-s8300"

    val stb2 = Codeset.nec(
        ID_STB2, "Airtel HD box (NECext 0xA1DE)", DeviceType.AIRTEL_HD, address = 0xA1DE, extended = true,
        source = "Flipper-IRDB AIRTEL/STB2.ir",
        entries = arrayOf(
            Command.Power to 0xDE, Command.Mute to 0xD6,
            Command.VolUp to 0xBE, Command.VolDown to 0xF6, Command.ChUp to 0x7E, Command.ChDown to 0xEE,
            Command.Digit(1) to 0x1E, Command.Digit(2) to 0x9E, Command.Digit(3) to 0x5E, Command.Digit(4) to 0x2E,
            Command.Digit(5) to 0xAE, Command.Digit(6) to 0x6E, Command.Digit(7) to 0x0E, Command.Digit(8) to 0x8E,
            Command.Digit(9) to 0x4E, Command.Digit(0) to 0xB6,
            Command.Up to 0x7E, Command.Down to 0xEE, Command.Left to 0xF6, Command.Right to 0xBE,
            Command.Menu to 0x36, Command.Ok to 0x66, Command.Back to 0x3E, Command.Exit to 0x96,
        ),
    )

    private fun raw(label: String, vararg us: Int) = IrCode.Raw(38_000, us.toList(), label)

    /** Airtel S8300 (raw captures). Keys that Flipper could not name are left out. */
    val s8300 = Codeset(
        ID_S8300, "Airtel S8300 (raw @38 kHz)", DeviceType.AIRTEL_HD, source = "Flipper-IRDB AIRTEL/S8300.ir (raw)",
        digitDelayMs = 300,
        codes = mapOf(
            Command.Digit(1).key to raw("1", 1439, 465, 292, 292, 292, 465, 2409, 465, 292, 292, 292, 662, 1126, 662, 662),
            Command.Digit(2).key to raw("2", 1434, 468, 288, 288, 288, 468, 1968, 468, 288, 468, 288, 348, 288, 676, 1109, 676, 676),
        ) + S8300_EXTRA,
    )

    /** NEC Power codes of other set-top boxes (probonopd/irdb). device,subdevice,function per irdb convention. */
    data class ScanEntry(val id: String, val name: String, val code: IrCode.Nec, val source: String)

    private fun entry(brand: String, device: Int, sub: Int, fn: Int): ScanEntry {
        val ext = sub >= 0
        val address = if (ext) (sub shl 8) or device else device
        val id = "irdb-${brand.lowercase().replace(' ', '-')}-$device-$sub"
        return ScanEntry(id, "$brand STB (NEC $device,$sub)", IrCode.Nec(address, fn, ext), "probonopd/irdb $brand ($device,$sub)")
    }

    val scanList: List<ScanEntry> = listOf(
        entry("Humax", 0, 17, 0), entry("Humax", 0, 23, 0), entry("Humax", 0, 73, 0),
        entry("Dish", 1, -1, 16), entry("Generic", 4, -1, 8), entry("Generic", 8, -1, 10),
        entry("Pioneer", 12, 251, 21), entry("Generic", 32, 255, 15), entry("Zinwell", 64, 64, 10),
        entry("Generic", 64, 223, 18), entry("Kaon", 130, 110, 1), entry("Generic", 132, 99, 20),
        entry("Generic", 170, -1, 28), entry("Generic", 247, -1, 8), entry("Motorola", 2, 255, 24),
    )

    /** Every candidate the scanner tries, in order: Airtel first, then the harvested list as Power-only codesets. */
    val candidates: List<Codeset> = listOf(stb2, s8300) + scanList.map {
        Codeset(it.id, it.name, DeviceType.AIRTEL_HD, mapOf(Command.Power.key to it.code), it.source)
    }
}

/** Remaining S8300 raw keys (split out to keep the table readable). */
private val S8300_EXTRA: Map<String, IrCode> = S8300Raw.codes
