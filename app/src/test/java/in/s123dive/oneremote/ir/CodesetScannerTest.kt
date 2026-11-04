package `in`.s123dive.oneremote.ir

import `in`.s123dive.oneremote.core.Command
import `in`.s123dive.oneremote.core.DeviceType
import `in`.s123dive.oneremote.core.SendResult
import `in`.s123dive.oneremote.ir.codesets.Codeset
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodesetScannerTest {
    private fun cs(i: Int, power: Boolean = true) = Codeset("c$i", "C$i", DeviceType.AIRTEL_HD, if (power) mapOf(Command.Power.key to IrCode.Nec(i, 1)) else emptyMap())

    @Test
    fun sendsPowerOfEachCandidateEverySecondThenFinishes() = runTest {
        val sent = mutableListOf<String>()
        val scanner = CodesetScanner(listOf(cs(1), cs(2, power = false), cs(3)), { c, _ -> sent += c.id; SendResult.Ok(c.id) }, intervalMs = 1000)
        scanner.start(this)
        advanceTimeBy(10)
        assertEquals(listOf("c1"), sent)
        advanceTimeBy(1000)
        assertEquals(listOf("c1"), sent) // c2 has no Power: nothing sent, result says unsupported
        assertTrue(scanner.state.value.lastResult!!.startsWith("unsupported"))
        advanceTimeBy(1000)
        assertEquals(listOf("c1", "c3"), sent)
        advanceTimeBy(1000)
        assertTrue(scanner.state.value.finished)
        assertFalse(scanner.state.value.running)
    }

    @Test
    fun lockReturnsCurrentCandidateAndStops() = runTest {
        val sent = mutableListOf<String>()
        val scanner = CodesetScanner(listOf(cs(1), cs(2), cs(3)), { c, _ -> sent += c.id; SendResult.Ok(c.id) }, intervalMs = 1000)
        scanner.start(this)
        advanceTimeBy(1010)
        assertEquals("c2", scanner.current?.id)
        val locked = scanner.lock()
        assertEquals("c2", locked?.id)
        advanceTimeBy(5000)
        assertEquals(listOf("c1", "c2"), sent)
        assertEquals("c2", scanner.state.value.locked?.id)
    }

    @Test
    fun skipAndPause() = runTest {
        val scanner = CodesetScanner(listOf(cs(1), cs(2), cs(3)), { c, _ -> SendResult.Ok(c.id) }, intervalMs = 1000)
        scanner.seek(2)
        assertEquals("c3", scanner.current?.id)
        scanner.seek(-5)
        assertEquals("c1", scanner.current?.id)
        scanner.start(this)
        advanceTimeBy(10)
        scanner.pause()
        advanceTimeBy(5000)
        assertEquals(0, scanner.state.value.index)
        assertNull(scanner.state.value.locked)
    }
}
