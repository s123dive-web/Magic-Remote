package `in`.s123dive.oneremote.ir.codesets

import `in`.s123dive.oneremote.core.Command
import `in`.s123dive.oneremote.core.DeviceType

/**
 * Atomberg BLDC ceiling fan (Renesa / Efficio family, IR remote).
 *
 * Source: Flipper-IRDB `Fans/Atomberg/Atomberg_fan.ir` (NECext, address bytes `00 F3`, i.e. the
 * 16-bit extended address 0xF300 sent low byte first). Every command's inverse byte in that file
 * checks out, but it is a single public capture: UNVERIFIED on the user's own unit. If a key does
 * nothing, the override screen / key finder replaces it per key.
 *
 * The remote has discrete speed keys 1–5 (no speed +/−), one cycling TIMER key, BOOST, SLEEP, LED.
 */
object AtombergCodeset {
    const val ID = "atomberg-fan"
    const val ADDRESS = 0xF300

    val codeset = Codeset.nec(
        ID, "Atomberg fan (NECext 0xF300)", DeviceType.ATOMBERG_FAN, address = ADDRESS, extended = true,
        source = "Flipper-IRDB Fans/Atomberg/Atomberg_fan.ir (unverified on this unit)",
        entries = arrayOf(
            Command.Power to 0x91,
            Command.FanSpeed(1) to 0x8B, Command.FanSpeed(2) to 0x90, Command.FanSpeed(3) to 0x8A,
            Command.FanSpeed(4) to 0x93, Command.FanSpeed(5) to 0x88,
            Command.Boost to 0x8F, Command.Timer to 0x96, Command.Light to 0x97, Command.Sleep to 0x8E,
        ),
    )
}
