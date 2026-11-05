package `in`.s123dive.oneremote.ir

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NecEncoderTest {
    /** Builds the expected pattern independently from a bit string written MSB->LSB per byte. */
    private fun expected(vararg bytesLsbFirst: String): IntArray {
        val out = mutableListOf(9000, 4500)
        for (byte in bytesLsbFirst) for (c in byte) { out += 562; out += if (c == '1') 1687 else 562 }
        out += 562
        return out.toIntArray()
    }

    @Test
    fun lgPowerReferencePattern() {
        // addr 0x04 = 0000 0100 -> LSB first 00100000; ~addr 0xFB -> 11011111
        // cmd  0x08 = 0000 1000 -> LSB first 00010000; ~cmd 0xF7 -> 11101111
        val pattern = NecEncoder.frame(0x04, 0x08)
        assertEquals(67, pattern.size)
        assertArrayEquals(expected("00100000", "11011111", "00010000", "11101111"), pattern)
    }

    @Test
    fun lgVolUpReferencePattern() {
        // cmd 0x02 -> LSB first 01000000; ~cmd 0xFD -> 10111111
        assertArrayEquals(expected("00100000", "11011111", "01000000", "10111111"), NecEncoder.frame(0x04, 0x02))
    }

    @Test
    fun extendedAddressHasNoInverseAddress() {
        // addr 0x6B86 -> low byte 0x86 LSB first 01100001, high byte 0x6B LSB first 11010110; cmd 0x01 -> 10000000, ~ -> 01111111
        val pattern = NecEncoder.frame(0x6B86, 0x01, extended = true)
        assertEquals(67, pattern.size)
        assertArrayEquals(expected("01100001", "11010110", "10000000", "01111111"), pattern)
    }

    @Test
    fun repeatFrame() {
        assertArrayEquals(intArrayOf(9000, 2250, 562), NecEncoder.repeatFrame())
    }

    @Test
    fun paddedFrameOccupiesExactly108ms() {
        val p = NecEncoder.framePadded(0x04, 0x08)
        assertEquals(108_000, p.sum())
        assertEquals(68, p.size) // trailing mark + one gap
    }

    @Test
    fun padExtendsAnExistingTrailingSpace() {
        assertArrayEquals(intArrayOf(100, 900), NecEncoder.pad(intArrayOf(100, 200), 1000))
        assertArrayEquals(intArrayOf(100, 200, 300, 400), NecEncoder.pad(intArrayOf(100, 200, 300), 1000))
        assertArrayEquals(intArrayOf(600, 600), NecEncoder.pad(intArrayOf(600, 600), 1000))
    }

    @Test
    fun pressIsPaddedFrameThenRepeat() {
        val p = NecEncoder.press(0x04, 0x08)
        assertEquals(68 + 3, p.size)
        assertEquals(108_000, p.copyOfRange(0, 68).sum())
        assertArrayEquals(intArrayOf(9000, 2250, 562), p.copyOfRange(68, 71))
    }

    @Test
    fun describe() {
        assertEquals("NEC 0x04/0x08", NecEncoder.describe(0x04, 0x08))
        assertEquals("NECx 0x6B86/0x01", NecEncoder.describe(0x6B86, 0x01, extended = true))
    }

    @Test
    fun rejectsOutOfRange() {
        assertThrows(IllegalArgumentException::class.java) { NecEncoder.frame(0x100, 0x08) }
        assertThrows(IllegalArgumentException::class.java) { NecEncoder.frame(0x04, 0x100) }
        assertThrows(IllegalArgumentException::class.java) { NecEncoder.frame(0x10000, 0x08, extended = true) }
    }
}
