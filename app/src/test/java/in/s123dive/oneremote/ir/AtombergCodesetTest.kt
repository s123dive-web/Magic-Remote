package `in`.s123dive.oneremote.ir

import `in`.s123dive.oneremote.core.Capability
import `in`.s123dive.oneremote.core.Command
import `in`.s123dive.oneremote.core.DeviceType
import `in`.s123dive.oneremote.core.capability
import `in`.s123dive.oneremote.ir.codesets.AtombergCodeset
import `in`.s123dive.oneremote.ir.codesets.Codesets
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AtombergCodesetTest {
    @Test
    fun powerFrameMatchesTheFlipperCapture() {
        // Flipper: NECext address 00 F3 -> 0xF300 low byte first; command 91 with inverse 6E.
        val code = AtombergCodeset.codeset.code(Command.Power) as IrCode.Nec
        assertEquals(IrCode.Nec(0xF300, 0x91, extended = true), code)
        val frame = NecEncoder.frame(0xF300, 0x91, extended = true)
        val bits = (0 until 32).map { frame[3 + it * 2] == NecEncoder.ONE_SPACE }
        fun byte(off: Int) = (0 until 8).sumOf { if (bits[off + it]) 1 shl it else 0 }
        assertEquals(0x00, byte(0)); assertEquals(0xF3, byte(8)); assertEquals(0x91, byte(16)); assertEquals(0x6E, byte(24))
        assertArrayEquals(NecEncoder.press(0xF300, 0x91, extended = true), code.pattern())
        assertEquals("NECx 0xF300/0x91", code.describe())
    }

    @Test
    fun everyRemoteKeyIsPresentAndFanCapable() {
        val cs = AtombergCodeset.codeset
        val expected = mapOf(
            Command.Power to 0x91, Command.FanSpeed(1) to 0x8B, Command.FanSpeed(2) to 0x90, Command.FanSpeed(3) to 0x8A,
            Command.FanSpeed(4) to 0x93, Command.FanSpeed(5) to 0x88, Command.Boost to 0x8F, Command.Timer to 0x96,
            Command.Light to 0x97, Command.Sleep to 0x8E,
        )
        for ((cmd, byte) in expected) assertEquals(cmd.key, IrCode.Nec(0xF300, byte, true), cs.code(cmd))
        assertEquals(expected.size, cs.codes.size)
        assertEquals(Capability.FAN, Command.FanSpeed(3).capability)
        assertEquals(Capability.FAN, Command.Boost.capability)
        assertEquals(Command.FanSpeed(4), Command.fromKey("FAN_SPEED:4"))
        assertEquals(null, Command.fromKey("FAN_SPEED:6"))
        assertEquals(DeviceType.ATOMBERG_FAN, cs.type)
        assertTrue(Codesets.scanCandidates(DeviceType.ATOMBERG_FAN).single() === cs)
        assertEquals(cs, Codesets.byId(AtombergCodeset.ID))
    }

    @Test
    fun irTransportCarriesFanKeys() {
        val port = object : IrPort {
            override val capability = IrCapability(true, listOf(CarrierRange(30_000, 56_000)))
            val sent = mutableListOf<IntArray>()
            override fun transmit(carrierHz: Int, pattern: IntArray) { sent += pattern }
        }
        val t = IrTransport(AtombergCodeset.codeset, IrTransmitter(port, kotlinx.coroutines.Dispatchers.Unconfined))
        assertTrue(Capability.FAN in t.capabilities)
        assertTrue(t.supports(Command.FanSpeed(5)))
        assertTrue(!t.supports(Command.VolUp))
        kotlinx.coroutines.test.runTest {
            assertEquals(`in`.s123dive.oneremote.core.SendResult.Ok("NECx 0xF300/0x88"), t.send(Command.FanSpeed(5)))
        }
    }
}
