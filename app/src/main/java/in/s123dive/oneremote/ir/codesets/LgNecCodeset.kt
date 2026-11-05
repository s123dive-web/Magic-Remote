package `in`.s123dive.oneremote.ir.codesets

import `in`.s123dive.oneremote.core.Command
import `in`.s123dive.oneremote.core.DeviceType

/**
 * LG TV, NEC address 0x04. Power/volume/channel/nav/digits are from the build spec; the rest are
 * the widely published LG (AKB7xxxx) codes: Settings 0x43, Exit 0x5B, Guide 0xA9, Info 0xAA,
 * List 0x53, Fav 0x1E, Prev 0x1A, Subtitle 0x39, AD 0x91, Sleep 0x0E, media 0xB0/0xBA/0xB1/0x8F/0x8E/0xBD,
 * colours 0x72/0x71/0x63/0x61, HDMI1–4 0xCE/0xCC/0xE9/0xDA, Netflix 0x56, Amazon 0x5C.
 * Any key that turns out wrong on a given model can be re-learned with the key finder.
 */
object LgNecCodeset {
    const val ID = "lg-nec-04"
    val codeset = Codeset.nec(
        ID, "LG TV (NEC 0x04)", DeviceType.LG_WEBOS, address = 0x04, extended = false,
        source = "LG NEC address 0x04 (spec + public LG code tables)",
        entries = arrayOf(
            Command.Power to 0x08, Command.PowerOn to 0xC4, Command.PowerOff to 0xC5,
            Command.VolUp to 0x02, Command.VolDown to 0x03, Command.Mute to 0x09,
            Command.ChUp to 0x00, Command.ChDown to 0x01, Command.Input to 0x0B,
            Command.Ok to 0x44, Command.Up to 0x40, Command.Down to 0x41, Command.Left to 0x07, Command.Right to 0x06,
            Command.Back to 0x28, Command.Home to 0x7C, Command.Menu to 0x43, Command.Exit to 0x5B,
            Command.Guide to 0xA9, Command.Info to 0xAA, Command.ChannelList to 0x53, Command.Fav to 0x1E, Command.Prev to 0x1A,
            Command.Subtitle to 0x39, Command.Audio to 0x91, Command.Sleep to 0x0E, Command.Help to 0x7A,
            Command.Play to 0xB0, Command.Pause to 0xBA, Command.Stop to 0xB1, Command.Rew to 0x8F, Command.Fwd to 0x8E, Command.Record to 0xBD,
            Command.InputSelect("HDMI_1") to 0xCE, Command.InputSelect("HDMI_2") to 0xCC, Command.InputSelect("HDMI_3") to 0xE9, Command.InputSelect("HDMI_4") to 0xDA,
            Command.LaunchApp("netflix") to 0x56, Command.LaunchApp("amazon") to 0x5C,
            Command.Red to 0x72, Command.Green to 0x71, Command.Yellow to 0x63, Command.Blue to 0x61,
            Command.Digit(0) to 0x10, Command.Digit(1) to 0x11, Command.Digit(2) to 0x12, Command.Digit(3) to 0x13,
            Command.Digit(4) to 0x14, Command.Digit(5) to 0x15, Command.Digit(6) to 0x16, Command.Digit(7) to 0x17,
            Command.Digit(8) to 0x18, Command.Digit(9) to 0x19,
        ),
    )
}
