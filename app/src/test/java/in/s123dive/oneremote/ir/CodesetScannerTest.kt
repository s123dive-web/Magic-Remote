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
    private fun scanner(codesets: List<Codeset>, sent: MutableList<String>, intervalMs: Long = 1000) =
        CodesetScanner(CodesetScanner.fromCodesets(codesets), { c -> sent += c.codesetId!!; SendResult.Ok(c.label) }, intervalMs)

    @Test
    fun sendsPowerOfEachCandidateEverySecondThenFinishes() = runTest {
        val sent = mutableListOf<String>()
        val scanner = scanner(listOf(cs(1), cs(2, power = false), cs(3)), sent)
        assertEquals(2, scanner.candidates.size) // c2 has no Power code: not a candidate
        scanner.start(this)
        advanceTimeBy(10)
        assertEquals(listOf("c1"), sent)
        advanceTimeBy(1000)
        assertEquals(listOf("c1", "c3"), sent)
        advanceTimeBy(1000)
        assertTrue(scanner.state.value.finished)
        assertFalse(scanner.state.value.running)
    }

    @Test
    fun recentReturnsNewestFirstForVerification() = runTest {
        val sent = mutableListOf<String>()
        val scanner = scanner((1..6).map { cs(it) }, sent)
        scanner.start(this)
        advanceTimeBy(3010) // cursor on c4
        assertEquals(listOf("c4", "c3", "c2", "c1"), scanner.recent(4).map { it.codesetId })
        scanner.seek(0)
        assertEquals(listOf("c1"), scanner.recent(4).map { it.codesetId })
    }

    @Test
    fun sweepsCoverAddressesAndCommands() {
        val power = NecSweep.powerSweep()
        assertEquals(NecSweep.POWER_COMMANDS.size * 256 - 1, power.size) // minus the LG TV power code
        assertEquals(IrCode.Nec(0x00, 0x0C), power[0].code)
        assertTrue(power.none { (it.code as IrCode.Nec).address == 0x04 && (it.code as IrCode.Nec).command == 0x08 })
        assertTrue(power.all { it.codesetId == null })
        val cmds = NecSweep.commandSweep(0xA1DE, extended = true, skip = setOf(0xDE))
        assertEquals(255, cmds.size)
        assertTrue(cmds.none { (it.code as IrCode.Nec).command == 0xDE })
        assertTrue(cmds.all { (it.code as IrCode.Nec).address == 0xA1DE && (it.code as IrCode.Nec).extended })
    }

    @Test
    fun lockReturnsCurrentCandidateAndStops() = runTest {
        val sent = mutableListOf<String>()
        val scanner = scanner(listOf(cs(1), cs(2), cs(3)), sent)
        scanner.start(this)
        advanceTimeBy(1010)
        assertEquals("c2", scanner.current?.codesetId)
        val locked = scanner.lock()
        assertEquals("c2", locked?.codesetId)
        advanceTimeBy(5000)
        assertEquals(listOf("c1", "c2"), sent)
        assertEquals("c2", scanner.state.value.locked?.codesetId)
    }

    @Test
    fun skipAndPause() = runTest {
        val scanner = scanner(listOf(cs(1), cs(2), cs(3)), mutableListOf())
        scanner.seek(2)
        assertEquals("c3", scanner.current?.codesetId)
        scanner.seek(-5)
        assertEquals("c1", scanner.current?.codesetId)
        scanner.start(this)
        advanceTimeBy(10)
        scanner.pause()
        advanceTimeBy(5000)
        assertEquals(0, scanner.state.value.index)
        assertNull(scanner.state.value.locked)
    }
}
