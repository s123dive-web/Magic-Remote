package `in`.s123dive.oneremote.ir

import `in`.s123dive.oneremote.core.Command
import `in`.s123dive.oneremote.core.DeviceType
import `in`.s123dive.oneremote.devices.DeviceConfig
import `in`.s123dive.oneremote.ir.codesets.AtombergCodeset
import `in`.s123dive.oneremote.ui.setup.overrideKeysFor
import `in`.s123dive.oneremote.ui.setup.parseOverride
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OverrideParseTest {
    @Test
    fun parsesTheFormsUsersPaste() {
        assertEquals(IrCode.Nec(0xF300, 0x91, true), parseOverride("91", 0xF300, true))
        assertEquals(IrCode.Nec(0xF300, 0x91, true), parseOverride("0x91", 0xF300, true))
        assertEquals(IrCode.Nec(0xF300, 0x8B, true), parseOverride("F300/8B", null, false))
        assertEquals(IrCode.Nec(0xF300, 0x8B, true), parseOverride("0xF300 0x8B", null, false))
        assertEquals(IrCode.Nec(0x04, 0x08, false), parseOverride("04/08", null, false))
        assertEquals(IrCode.Nec(0xA1DE, 0xDE, true), parseOverride("A1 DE DE", null, false))
        assertNull(parseOverride("", 0xF300, true))
        assertNull(parseOverride("zz", 0xF300, true))
        assertNull(parseOverride("91", null, false)) // no default address to use
        assertNull(parseOverride("F300/1FF", null, false))
    }

    @Test
    fun overrideWinsAndResetRestoresDefault() {
        val cfg = DeviceConfig.empty().with(DeviceConfig.CODESET_ID, AtombergCodeset.ID)
        val base = cfg.effectiveCodeset(AtombergCodeset.codeset, "Fan", DeviceType.ATOMBERG_FAN)!!
        assertEquals(IrCode.Nec(0xF300, 0x91, true), base.code(Command.Power))
        val over = cfg.withCustomCodes(mapOf(Command.Power.key to IrCode.Nec(0xF300, 0x12, true)))
        assertEquals(IrCode.Nec(0xF300, 0x12, true), over.effectiveCodeset(AtombergCodeset.codeset, "Fan", DeviceType.ATOMBERG_FAN)!!.code(Command.Power))
        assertEquals(IrCode.Nec(0xF300, 0x8B, true), over.effectiveCodeset(AtombergCodeset.codeset, "Fan", DeviceType.ATOMBERG_FAN)!!.code(Command.FanSpeed(1)))
        val reset = over.withCustomCodes(over.customCodes - Command.Power.key)
        assertEquals(IrCode.Nec(0xF300, 0x91, true), reset.effectiveCodeset(AtombergCodeset.codeset, "Fan", DeviceType.ATOMBERG_FAN)!!.code(Command.Power))
    }

    @Test
    fun fanOverrideKeysMatchTheRemote() {
        assertEquals(listOf("POWER", "FAN_SPEED:1", "FAN_SPEED:2", "FAN_SPEED:3", "FAN_SPEED:4", "FAN_SPEED:5", "BOOST", "TIMER", "SLEEP", "LIGHT"), overrideKeysFor(DeviceType.ATOMBERG_FAN).map { it.key })
    }
}
