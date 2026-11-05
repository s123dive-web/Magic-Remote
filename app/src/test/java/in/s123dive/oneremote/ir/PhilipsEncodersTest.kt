package `in`.s123dive.oneremote.ir

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhilipsEncodersTest {
    @Test
    fun rc5PowerAddress0Command12() {
        // bits 1 1 0 00000 001100 -> half-bits s m | s m | m s | (m s)x5 | m s | m s | s m | s m | m s | m s
        // drop the leading space, merge runs, drop the trailing space:
        val u = 889
        val expected = intArrayOf(u, u, 2 * u, u, u, u, u, u, u, u, u, u, u, u, u, u, u, 2 * u, u, u, 2 * u, u, u)
        assertArrayEquals(expected, Rc5Encoder.frame(0, 12))
        assertEquals(26 * u, Rc5Encoder.frame(0, 12).sum())
        val press = Rc5Encoder.press(0, 12)
        assertEquals(Rc5Encoder.FRAME_PERIOD_US + 26 * u, press.sum())
        assertEquals("RC5 0/12", Rc5Encoder.describe(0, 12))
    }

    @Test
    fun rc5FieldBitDistinguishesExtendedCommands() {
        assertFalse(Rc5Encoder.frame(0, 12).contentEquals(Rc5Encoder.frame(0, 12 + 64)))
    }

    @Test
    fun rc6ModeZeroAddress0Command12() {
        // leader m6 s2 | start m s | mode 000: s m s m s m | trailer(0) s s m m | address 0: (s m)x8 | command 00001100
        val t = 444
        val f = Rc6Encoder.frame(0, 12)
        assertEquals(6 * t, f[0])       // 2666 leader mark
        assertEquals(2 * t, f[1])       // 889 leader space
        assertEquals(t, f[2])           // start bit mark
        assertEquals(2 * t, f[3])       // start-bit space + first mode-bit space
        assertEquals(52 * t, f.sum())   // 52 half-units, ends on a mark so nothing is dropped
        assertTrue(f.all { it % t == 0 })
        assertEquals("RC6 0/12", Rc6Encoder.describe(0, 12))
        assertFalse(f.contentEquals(Rc6Encoder.frame(0, 13)))
    }

    @Test
    fun manchesterMerge() {
        assertArrayEquals(intArrayOf(200, 100, 100), Manchester.levelsToPattern(listOf(false, true, true, false, true), 100))
        assertArrayEquals(intArrayOf(100, 100, 100), Manchester.levelsToPattern(listOf(true, false, true, false), 100))
    }

    @Test
    fun sweepsAndCommandSweepFor() {
        assertEquals(32, NecSweep.rc5PowerPass().size)
        assertEquals(256, NecSweep.rc6PowerPass().size)
        val deep = NecSweep.deepSweep()
        assertTrue(deep.size > 10_000)
        assertEquals(IrCode.Rc5(0, 12), deep[0].code)
        assertEquals(63, NecSweep.commandSweepFor(IrCode.Rc5(3, 12), skip = setOf(12)).size)
        assertEquals(255, NecSweep.commandSweepFor(IrCode.Rc6(1, 12), skip = setOf(12)).size)
        assertEquals(255, NecSweep.commandSweepFor(IrCode.Nec(0xA1DE, 0xDE, true), skip = setOf(0xDE)).size)
        assertEquals(12, NecSweep.commandOf(IrCode.Rc5(0, 12)))
    }

    @Test
    fun codesetsWithoutPowerStillGetAScanCandidate() {
        val cands = CodesetScanner.fromCodesets(listOf(`in`.s123dive.oneremote.ir.codesets.AirtelCodesets.s8300))
        assertEquals(1, cands.size)
        assertTrue(cands[0].label.contains("MUTE"))
    }
}
