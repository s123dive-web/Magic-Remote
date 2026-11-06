package `in`.s123dive.oneremote.ir.codesets

import `in`.s123dive.oneremote.core.Command
import `in`.s123dive.oneremote.core.DeviceType
import `in`.s123dive.oneremote.ir.IrCode

/**
 * Airtel Digital TV set-top box, Nokia32 protocol (36 kHz), device 38, sub-device 0, X 38.
 *
 * Source: irplus database `AIRTEL/DS.xml` (Pronto hex, 42 keys, each with its toggle=0 and
 * toggle=1 variant). Decoded here field by field: digits 0–9 are functions 0–9, every key's
 * two variants differ only in the toggle bit. This is the protocol Technicolor-built Airtel
 * boxes use; the NEC "STB2" set is a different (older/other-vendor) remote.
 *
 * Keys of the newer HD HEVC remote that the file lacks (mute, back, myRec, a/v, fav, movies,
 * help) can be found with the key finder (Nokia32 function sweep, 256 codes).
 */
object AirtelDsCodeset {
    const val ID = "airtel-ds-nokia32"
    const val DEVICE = 38
    const val SUB = 0
    const val X = 38

    private fun f(function: Int) = IrCode.Nokia32(DEVICE, SUB, X, function)

    val codeset = Codeset(
        ID, "Airtel box (Nokia32 38.0.38)", DeviceType.AIRTEL_HD,
        source = "irplus AIRTEL/DS.xml (Pronto, decoded as Nokia32)",
        digitDelayMs = 250,
        codes = mapOf(
            Command.Power.key to f(0x0C),
            Command.Guide.key to f(0x81), Command.Info.key to f(0xCB), Command.Exit.key to f(0x15), Command.Menu.key to f(0x54),
            Command.Home.key to f(0x13),        // "ITV" portal key on the DS remote
            Command.Input.key to f(0x12),       // "LIVE TV"
            Command.VolUp.key to f(0x10), Command.VolDown.key to f(0x11), Command.ChUp.key to f(0x20), Command.ChDown.key to f(0x21),
            Command.Up.key to f(0x58), Command.Down.key to f(0x59), Command.Left.key to f(0x5A), Command.Right.key to f(0x5B), Command.Ok.key to f(0x5C),
            Command.Rew.key to f(0x29), Command.Play.key to f(0x2C), Command.Fwd.key to f(0x28), Command.Pause.key to f(0x30), Command.Stop.key to f(0x31), Command.Record.key to f(0x37),
            Command.Fav.key to f(0x84), Command.Back.key to f(0x83), Command.Prev.key to f(0x83), // RECALL (↩) doubles as back
            Command.Red.key to f(0xBE), Command.Green.key to f(0xBF), Command.Yellow.key to f(0xC0), Command.Blue.key to f(0xC1),
        ) + (0..9).associate { Command.Digit(it).key to f(it) },
    )
}
