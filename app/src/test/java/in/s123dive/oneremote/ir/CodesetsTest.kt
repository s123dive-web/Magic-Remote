package `in`.s123dive.oneremote.ir

import `in`.s123dive.oneremote.core.Command
import `in`.s123dive.oneremote.core.DeviceType
import `in`.s123dive.oneremote.ir.codesets.AirtelCodesets
import `in`.s123dive.oneremote.ir.codesets.Codesets
import `in`.s123dive.oneremote.ir.codesets.LgNecCodeset
import `in`.s123dive.oneremote.ir.codesets.SonySircCodeset
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodesetsTest {
    @Test
    fun everyBuiltInCodeEncodes() {
        for (cs in Codesets.all) for ((key, code) in cs.codes) {
            assertNotNull("$key in ${cs.id} is a known command", Command.fromKey(key))
            val p = code.pattern()
            assertTrue("$key in ${cs.id} has a pattern", p.isNotEmpty())
            assertTrue("$key in ${cs.id} all positive", p.all { it > 0 })
        }
    }

    @Test
    fun idsAreUnique() {
        assertEquals(Codesets.all.size, Codesets.all.map { it.id }.toSet().size)
    }

    @Test
    fun lgPowerMatchesReference() {
        val code = LgNecCodeset.codeset.code(Command.Power) as IrCode.Nec
        assertEquals(IrCode.Nec(0x04, 0x08), code)
        assertArrayEquals(NecEncoder.press(0x04, 0x08), code.pattern())
        assertEquals("NEC 0x04/0x08", code.describe())
        assertEquals(38_000, LgNecCodeset.codeset.carrierHz)
    }

    @Test
    fun sonyPowerMatchesReference() {
        val code = SonySircCodeset.addr1.code(Command.Power) as IrCode.Sirc
        assertEquals(IrCode.Sirc(21, 1, 12), code)
        assertArrayEquals(SircEncoder.burst(21, 1), code.pattern())
        assertEquals(40_000, SonySircCodeset.addr26.carrierHz)
        assertEquals(IrCode.Sirc(9, 1, 12), SonySircCodeset.addr1.code(Command.Digit(0)))
        assertEquals(IrCode.Sirc(0, 1, 12), SonySircCodeset.addr1.code(Command.Digit(1)))
    }

    @Test
    fun airtelStb2IsExtendedNecA1DE() {
        val power = AirtelCodesets.stb2.code(Command.Power) as IrCode.Nec
        assertEquals(IrCode.Nec(0xA1DE, 0xDE, extended = true), power)
        assertEquals("NECx 0xA1DE/0xDE", power.describe())
        // Extended frame: 16-bit address LSB first -> low byte 0xDE then 0xA1, then cmd 0xDE and ~cmd 0x21.
        val frame = NecEncoder.frame(0xA1DE, 0xDE, extended = true)
        assertEquals(67, frame.size)
        assertEquals(71, power.pattern().size) // padded frame + repeat frame
        val bits = (0 until 32).map { frame[3 + it * 2] == 1687 }
        fun byte(off: Int) = (0 until 8).sumOf { if (bits[off + it]) 1 shl it else 0 }
        assertEquals(0xDE, byte(0)); assertEquals(0xA1, byte(8)); assertEquals(0xDE, byte(16)); assertEquals(0x21, byte(24))
        assertEquals(10, (0..9).count { AirtelCodesets.stb2.code(Command.Digit(it)) != null })
    }

    @Test
    fun airtelS8300HasRawKeys() {
        assertTrue(AirtelCodesets.s8300.code(Command.Power) == null) // Flipper file has no POWER; scanner skips it
        assertTrue(AirtelCodesets.s8300.code(Command.Ok) is IrCode.Raw)
        assertEquals(10, (0..9).count { AirtelCodesets.s8300.code(Command.Digit(it)) != null })
    }

    @Test
    fun scanCandidatesForAirtelStartWithAirtelAndCarryProvenance() {
        val c = Codesets.scanCandidates(DeviceType.AIRTEL_HD)
        assertEquals("airtel-ds-nokia32", c[0].id)
        assertEquals(AirtelCodesets.ID_STB2, c[1].id)
        assertTrue(c.size >= 16)
        assertTrue(c.all { it.source.isNotBlank() })
        assertTrue(c.drop(3).all { it.code(Command.Power) != null })
    }

    @Test
    fun scanEntryAddressEncoding() {
        val humax = AirtelCodesets.scanList.first { it.id == "irdb-humax-0-17" }
        assertEquals(IrCode.Nec(0x1100, 0, extended = true), humax.code)
        val plain = AirtelCodesets.scanList.first { it.id == "irdb-generic-8--1" }
        assertEquals(IrCode.Nec(8, 10, extended = false), plain.code)
        assertTrue(AirtelCodesets.scanList.none { it.code.address == 4 && it.code.command == 8 }) // never the LG TV's own code
    }
}
