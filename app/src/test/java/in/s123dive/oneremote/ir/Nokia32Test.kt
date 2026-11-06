package `in`.s123dive.oneremote.ir

import `in`.s123dive.oneremote.core.Command
import `in`.s123dive.oneremote.ir.codesets.AirtelCodesets
import `in`.s123dive.oneremote.ir.codesets.AirtelDsCodeset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Nokia32Test {
    /** Pronto words from irplus AIRTEL/DS.xml POWER, toggle 0 (frequency 0x73, 18 pairs). */
    private val prontoPowerT0 = "000F 000A 0006 000A 0006 0016 0006 0010 0006 0016 0006 000A 0006 000A 0006 000A 0006 000A 0006 000A 0006 0016 0006 0010 0006 0016 0006 000A 0006 000A 0006 001C 0006 000A 0006 0CA5"
    private val prontoPowerT1 = "000F 000A 0006 000A 0006 0016 0006 0010 0006 0016 0006 000A 0006 000A 0006 000A 0006 000A 0006 0016 0006 0016 0006 0010 0006 0016 0006 000A 0006 000A 0006 001C 0006 000A 0006 0C99"

    private fun symbols(pattern: IntArray): List<Int> {
        // classify each space after a 6-period mark by nearest of 10/16/22/28 periods
        val unit = 1_000_000.0 / 36_000
        return (3 until 35 step 2).map { i -> listOf(10, 16, 22, 28).minByOrNull { kotlin.math.abs(pattern[i] - it * unit) }!! }
    }

    private fun prontoSymbols(words: String): List<Int> = words.split(" ").map { it.toInt(16) }.let { w -> (3 until 35 step 2).map { w[it] } }

    @Test
    fun powerFrameMatchesThePronto() {
        val f0 = Nokia32Encoder.frame(38, 0, 38, 12, toggle = false)
        assertEquals(35, f0.size)
        assertEquals(417, f0[0]); assertEquals(278, f0[1]); assertEquals(167, f0[2])
        assertEquals(prontoSymbols(prontoPowerT0), symbols(f0))
        val f1 = Nokia32Encoder.frame(38, 0, 38, 12, toggle = true)
        assertEquals(prontoSymbols(prontoPowerT1), symbols(f1))
        assertNotEquals(f0.toList(), f1.toList())
        // one press = padded frame + frame
        val press = Nokia32Encoder.press(38, 0, 38, 12, false)
        assertEquals(Nokia32Encoder.FRAME_PERIOD_US + f0.sum(), press.sum())
    }

    @Test
    fun digitsAreFunctionsZeroToNineAndToggleAlternates() {
        for (d in 0..9) assertEquals(IrCode.Nokia32(38, 0, 38, d), AirtelDsCodeset.codeset.code(Command.Digit(d)))
        val code = AirtelDsCodeset.codeset.code(Command.Power)!!
        assertNotEquals(code.pattern(false).toList(), code.pattern(true).toList())
        assertEquals(36_000, code.carrierHz)
        assertEquals("Nokia32 38.0.38/12", code.describe())
        val a = IrToggle.next(); val b = IrToggle.next()
        assertNotEquals(a, b)
    }

    @Test
    fun airtelCandidatesStartWithNokia32AndSweepsCoverIt() {
        assertEquals(AirtelDsCodeset.ID, AirtelCodesets.candidates[0].id)
        assertTrue(AirtelDsCodeset.codeset.codes.size >= 38)
        val sweep = NecSweep.commandSweepFor(IrCode.Nokia32(38, 0, 38, 12), skip = setOf(12))
        assertEquals(255, sweep.size)
        assertTrue(sweep.all { (it.code as IrCode.Nokia32).device == 38 })
        assertEquals(IrCode.Nokia32(0, 0, 0, 12), NecSweep.deepSweep()[0].code)
        assertEquals(12, NecSweep.commandOf(IrCode.Nokia32(38, 0, 38, 12)))
    }
}
