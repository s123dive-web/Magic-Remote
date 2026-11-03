package `in`.s123dive.oneremote.ir

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SircEncoderTest {
    private fun expected(bitsLsbFirst: String): IntArray {
        val out = mutableListOf(2400, 600)
        for (c in bitsLsbFirst) { out += if (c == '1') 1200 else 600; out += 600 }
        return out.toIntArray()
    }

    @Test
    fun sonyPower12BitReferencePattern() {
        // cmd 21 = 0010101 -> LSB first 1010100; addr 1 (5 bits) -> 10000
        val frame = SircEncoder.frame(21, 1)
        assertEquals(26, frame.size)
        assertArrayEquals(expected("1010100" + "10000"), frame)
    }

    @Test
    fun sonyVolUp12Bit() {
        // cmd 18 = 0010010 -> LSB first 0100100; addr 1 -> 10000
        assertArrayEquals(expected("0100100" + "10000"), SircEncoder.frame(18, 1))
    }

    @Test
    fun fifteenBitUsesEightAddressBits() {
        // cmd 99 = 1100011 -> LSB first 1100011; addr 26 = 00011010 -> LSB first 01011000
        val frame = SircEncoder.frame(99, 26, bits = 15)
        assertEquals(2 + 15 * 2, frame.size)
        assertArrayEquals(expected("1100011" + "01011000"), frame)
    }

    @Test
    fun twentyBitAppendsExtended() {
        // cmd 1 -> 1000000; addr 1 -> 10000; ext 0x80 -> LSB first 00000001
        val frame = SircEncoder.frame(1, 1, bits = 20, extended = 0x80)
        assertEquals(2 + 20 * 2, frame.size)
        assertArrayEquals(expected("1000000" + "10000" + "00000001"), frame)
    }

    @Test
    fun burstIsThreeFramesAt45ms() {
        val burst = SircEncoder.burst(21, 1)
        val one = SircEncoder.frame(21, 1)
        assertEquals(one.size * 3, burst.size)
        assertEquals(45_000 * 3, burst.sum())
        // Each frame starts with the header and its last space is stretched to the period.
        for (r in 0 until 3) {
            assertEquals(2400, burst[r * one.size])
            assertEquals(45_000, burst.copyOfRange(r * one.size, (r + 1) * one.size).sum())
        }
    }

    @Test
    fun describe() {
        assertEquals("SIRC12 21/1", SircEncoder.describe(21, 1))
        assertEquals("SIRC15 99/26", SircEncoder.describe(99, 26, bits = 15))
        assertEquals("SIRC20 1/1/128", SircEncoder.describe(1, 1, bits = 20, extended = 128))
    }

    @Test
    fun rejectsInvalid() {
        assertThrows(IllegalArgumentException::class.java) { SircEncoder.frame(128, 1) }
        assertThrows(IllegalArgumentException::class.java) { SircEncoder.frame(1, 32) }
        assertThrows(IllegalArgumentException::class.java) { SircEncoder.frame(1, 1, bits = 13) }
        assertThrows(IllegalArgumentException::class.java) { SircEncoder.frame(1, 1, bits = 20, extended = 256) }
    }
}
