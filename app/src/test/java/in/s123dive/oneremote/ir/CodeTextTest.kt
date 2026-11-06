package `in`.s123dive.oneremote.ir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeTextTest {
    private val dsPower = "0000 0073 0000 0012 000F 000A 0006 000A 0006 0016 0006 0010 0006 0016 0006 000A 0006 000A 0006 000A 0006 000A 0006 000A 0006 0016 0006 0010 0006 0016 0006 000A 0006 000A 0006 001C 0006 000A 0006 0CA5"

    @Test
    fun prontoBecomesRawAtTheRightCarrier() {
        val raw = CodeText.parsePronto(dsPower)!!
        assertEquals(36044.0, raw.carrierHz.toDouble(), 60.0)
        assertEquals(35, raw.marks.size) // 18 pairs minus the trailing gap
        assertEquals(416.0, raw.marks[0].toDouble(), 2.0) // 15 periods
        assertEquals(167.0, raw.marks[2].toDouble(), 2.0) // 6 periods
        // the Nokia32 encoder produces the same symbol timings as the Pronto capture
        val enc = Nokia32Encoder.frame(38, 0, 38, 12, false)
        for (i in enc.indices) assertEquals("sample $i", enc[i].toDouble(), raw.marks[i].toDouble(), 3.0)
        assertNull(CodeText.parsePronto("hello"))
        assertNull(CodeText.parsePronto("0100 0073 0000 0002 0001 0002 0003 0004"))
    }

    @Test
    fun flipperParsedAndRawBlocks() {
        val nec = CodeText.parseFlipper("name: POWER\ntype: parsed\nprotocol: NECext\naddress: DE A1 00 00\ncommand: DE 21 00 00\n")!!
        assertEquals("POWER" to IrCode.Nec(0xA1DE, 0xDE, true), nec)
        val plain = CodeText.parseFlipper("name: Mute\ntype: parsed\nprotocol: NEC\naddress: 04 00 00 00\ncommand: 09 00 00 00\n")!!
        assertEquals(IrCode.Nec(0x04, 0x09, false), plain.second)
        val rc5 = CodeText.parseFlipper("name: P\ntype: parsed\nprotocol: RC5\naddress: 03 00 00 00\ncommand: 0C 00 00 00\n")!!
        assertEquals(IrCode.Rc5(3, 12), rc5.second)
        val sam = CodeText.parseFlipper("name: P\ntype: parsed\nprotocol: Samsung32\naddress: 07 00 00 00\ncommand: 02 00 00 00\n")!!
        assertEquals(IrCode.Samsung32(7, 2), sam.second)
        val raw = CodeText.parseFlipper("name: X\ntype: raw\nfrequency: 38000\nduty_cycle: 0.33\ndata: 1439 465 292 292 292\n")!!
        assertEquals(IrCode.Raw(38000, listOf(1439, 465, 292, 292, 292), "X"), raw.second)
        assertNull(CodeText.parseFlipper("type: parsed\nprotocol: Weird\naddress: 00\ncommand: 00"))
        val file = CodeText.parseFlipperFile("Filetype: IR signals file\n#\nname: A\ntype: parsed\nprotocol: NEC\naddress: 01 00 00 00\ncommand: 02 00 00 00\n#\nname: B\ntype: parsed\nprotocol: NEC\naddress: 01 00 00 00\ncommand: 03 00 00 00\n")
        assertEquals(listOf("A", "B"), file.map { it.first })
    }

    @Test
    fun samsung32Frame() {
        val f = SamsungEncoder.frame(0x07, 0x02)
        assertEquals(4500, f[0]); assertEquals(4500, f[1]); assertEquals(67, f.size)
        assertTrue(SamsungEncoder.press(7, 2).sum() > SamsungEncoder.FRAME_PERIOD_US)
        assertEquals(255, NecSweep.commandSweepFor(IrCode.Samsung32(7, 2), setOf(2)).size)
    }
}
