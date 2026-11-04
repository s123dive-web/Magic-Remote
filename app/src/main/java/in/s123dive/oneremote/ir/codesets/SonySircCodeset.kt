package `in`.s123dive.oneremote.ir.codesets

import `in`.s123dive.oneremote.core.Command
import `in`.s123dive.oneremote.core.DeviceType
import `in`.s123dive.oneremote.ir.IrCode

/**
 * Sony Bravia SIRC. Address 1 (12-bit) is the classic TV set from the spec. Where Bravia
 * uses the extended 15-bit address 26 (Back / media keys) those variants are included as a
 * second codeset so the scanner can offer both.
 */
object SonySircCodeset {
    const val ID_ADDR1 = "sony-sirc-1"
    const val ID_ADDR26 = "sony-sirc-26"

    private fun s12(cmd: Int) = IrCode.Sirc(cmd, 1, 12)
    private fun s15(cmd: Int, addr: Int = 26) = IrCode.Sirc(cmd, addr, 15)

    private val digits: Map<String, IrCode> = (1..9).associate { Command.Digit(it).key to s12(it - 1) } + (Command.Digit(0).key to s12(9))

    /** Spec set: TV address 1. */
    val addr1 = Codeset(
        ID_ADDR1, "Sony Bravia (SIRC addr 1)", DeviceType.SONY_BRAVIA,
        source = "SIRC 12-bit address 1 (spec); PowerOn/Off 46/47 and Display 58 from public Sony tables",
        codes = mapOf(
            Command.Power.key to s12(21), Command.PowerOn.key to s12(46), Command.PowerOff.key to s12(47),
            Command.VolUp.key to s12(18), Command.VolDown.key to s12(19), Command.Mute.key to s12(20),
            Command.ChUp.key to s12(16), Command.ChDown.key to s12(17), Command.Input.key to s12(37),
            Command.Ok.key to s12(101), Command.Up.key to s12(116), Command.Down.key to s12(117),
            Command.Left.key to s12(52), Command.Right.key to s12(51),
            Command.Home.key to s12(96), Command.Back.key to s12(99), Command.Info.key to s12(58),
            Command.Menu.key to s12(96),
        ) + digits,
    )

    /** Same keys but with the Bravia 15-bit address 26 variants where they exist (Back, Home, media keys). */
    val addr26 = Codeset(
        ID_ADDR26, "Sony Bravia (SIRC addr 1 + 26 media keys)", DeviceType.SONY_BRAVIA,
        source = "addr1 set + 15-bit address 26 media keys from public Bravia tables (unverified on KDL-43W6600)",
        codes = addr1.codes + mapOf(
            Command.Back.key to s15(99), Command.Home.key to s15(96), Command.Guide.key to s15(91),
            Command.Play.key to s15(26), Command.Pause.key to s15(25), Command.Stop.key to s15(24),
            Command.Rew.key to s15(27), Command.Fwd.key to s15(28), Command.Exit.key to s15(99),
            Command.Red.key to s15(37, 151), Command.Green.key to s15(38, 151),
            Command.Yellow.key to s15(39, 151), Command.Blue.key to s15(36, 151),
        ),
    )
}
