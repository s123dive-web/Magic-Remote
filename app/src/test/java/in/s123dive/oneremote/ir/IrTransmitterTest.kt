package `in`.s123dive.oneremote.ir

import `in`.s123dive.oneremote.core.SendResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class IrTransmitterTest {
    private class FakePort(override val capability: IrCapability, val fail: Boolean = false) : IrPort {
        val sent = mutableListOf<Pair<Int, IntArray>>()
        val concurrent = AtomicInteger()
        var maxConcurrent = 0
        override fun transmit(carrierHz: Int, pattern: IntArray) {
            val now = concurrent.incrementAndGet()
            synchronized(this) { if (now > maxConcurrent) maxConcurrent = now }
            try {
                if (fail) throw RuntimeException("HAL busy")
                Thread.sleep(5)
                synchronized(this) { sent += carrierHz to pattern }
            } finally {
                concurrent.decrementAndGet()
            }
        }
    }

    private val both = IrCapability(hasIr = true, ranges = listOf(CarrierRange(30_000, 40_000)))

    @Test
    fun noBlasterIsAnExplicitFailure() = runTest {
        val tx = IrTransmitter(FakePort(IrCapability.NONE), Dispatchers.Unconfined)
        val r = tx.send(38_000, intArrayOf(1, 2), "x")
        assertEquals(SendResult.Failed("x", "this phone has no IR blaster"), r)
    }

    @Test
    fun unsupportedCarrierIsAnExplicitFailureNamingTheRanges() = runTest {
        val port = FakePort(IrCapability(hasIr = true, ranges = listOf(CarrierRange(36_000, 38_000))))
        val tx = IrTransmitter(port, Dispatchers.Unconfined)
        val r = tx.send(40_000, intArrayOf(1, 2), "sony") as SendResult.Failed
        assertEquals("40.0 kHz not supported (ranges: 36.0–38.0 kHz)", r.reason)
        assertTrue(port.sent.isEmpty())
    }

    @Test
    fun unknownRangesAreOptimistic() = runTest {
        val port = FakePort(IrCapability(hasIr = true, ranges = emptyList(), rangesKnown = false))
        val tx = IrTransmitter(port, Dispatchers.Unconfined)
        assertEquals(SendResult.Ok("p"), tx.send(40_000, intArrayOf(1, 2), "p"))
        assertEquals(1, port.sent.size)
    }

    @Test
    fun halErrorsAreReported() = runTest {
        val tx = IrTransmitter(FakePort(both, fail = true), Dispatchers.Unconfined)
        assertEquals(SendResult.Failed("p", "IR HAL error: HAL busy"), tx.send(38_000, intArrayOf(1), "p"))
    }

    @Test
    fun sendsAreSerialised() = runTest {
        val port = FakePort(both)
        val tx = IrTransmitter(port, Dispatchers.IO)
        (1..8).map { async(Dispatchers.Default) { tx.send(38_000, intArrayOf(it), "p$it") } }.awaitAll()
        assertEquals(8, port.sent.size)
        assertEquals(1, port.maxConcurrent)
    }
}
