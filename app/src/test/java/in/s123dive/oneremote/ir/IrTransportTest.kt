package `in`.s123dive.oneremote.ir

import `in`.s123dive.oneremote.core.Capability
import `in`.s123dive.oneremote.core.Command
import `in`.s123dive.oneremote.core.SendResult
import `in`.s123dive.oneremote.ir.codesets.AirtelCodesets
import `in`.s123dive.oneremote.ir.codesets.Codeset
import `in`.s123dive.oneremote.ir.codesets.LgNecCodeset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IrTransportTest {
    private class Port(override val capability: IrCapability) : IrPort {
        val sent = mutableListOf<Pair<Int, IntArray>>()
        override fun transmit(carrierHz: Int, pattern: IntArray) { sent += carrierHz to pattern }
    }
    private val fullIr = IrCapability(true, listOf(CarrierRange(30_000, 56_000)))
    private fun transport(cs: Codeset, port: Port = Port(fullIr), delays: MutableList<Long> = mutableListOf()) =
        IrTransport(cs, IrTransmitter(port, Dispatchers.Unconfined), delayFn = { delays += it })

    @Test
    fun capabilitiesFollowTheCodeset() {
        val t = transport(LgNecCodeset.codeset)
        assertTrue(Capability.POWER in t.capabilities)
        assertTrue(Capability.DIGITS in t.capabilities)
        assertTrue(Capability.CHANNEL in t.capabilities)
        assertTrue(Capability.RAW in t.capabilities)
        assertFalse(Capability.TEXT_INPUT in t.capabilities)
        assertFalse(Capability.POINTER in t.capabilities)
    }

    @Test
    fun channelExpandsToDigitsThenOkWithDelays() = runTest {
        val port = Port(fullIr); val delays = mutableListOf<Long>()
        val t = transport(AirtelCodesets.stb2, port, delays)
        val r = t.send(Command.Channel(120))
        assertTrue(r is SendResult.Ok)
        assertEquals(4, port.sent.size) // 1, 2, 0, OK
        assertEquals(listOf(250L, 250L, 250L), delays)
        assertEquals("digits 120+OK @250ms", t.describe(Command.Channel(120)))
        val expected = listOf(Command.Digit(1), Command.Digit(2), Command.Digit(0), Command.Ok).map { AirtelCodesets.stb2.code(it)!!.pattern().toList() }
        assertEquals(expected, port.sent.map { it.second.toList() })
    }

    @Test
    fun powerOnFallsBackToToggleAndSaysSo() = runTest {
        val cs = Codeset("x", "X", `in`.s123dive.oneremote.core.DeviceType.AIRTEL_HD, mapOf(Command.Power.key to IrCode.Nec(1, 2)))
        val t = transport(cs)
        assertEquals("NEC 0x01/0x02 (toggle)", t.describe(Command.PowerOn))
        assertTrue(t.supports(Command.PowerOff))
        assertEquals(SendResult.Ok("NEC 0x01/0x02 (toggle)"), t.send(Command.PowerOff))
    }

    @Test
    fun unsupportedKeyIsExplicit() = runTest {
        val t = transport(AirtelCodesets.stb2)
        val r = t.send(Command.Text("hi"))
        assertEquals(SendResult.Unsupported("Airtel HD box (NECext 0xA1DE) has no code for TEXT:hi"), r)
        assertFalse(t.supports(Command.Text("hi")))
    }

    @Test
    fun rawBytesBecomeNec() = runTest {
        val port = Port(fullIr)
        val t = transport(LgNecCodeset.codeset, port)
        assertEquals(SendResult.Ok("NEC 0x04/0x08"), t.send(Command.Raw(byteArrayOf(0x04, 0x08))))
        assertEquals(SendResult.Ok("NECx 0xA1DE/0xDE"), t.send(Command.Raw(byteArrayOf(0xA1.toByte(), 0xDE.toByte(), 0xDE.toByte()))))
        assertTrue(t.send(Command.Raw(byteArrayOf(1))) is SendResult.Unsupported)
        assertEquals(2, port.sent.size)
    }

    @Test
    fun carrierOutsideBlasterRangeFailsBeforeSending() = runTest {
        val port = Port(IrCapability(true, listOf(CarrierRange(36_000, 38_000))))
        val t = transport(`in`.s123dive.oneremote.ir.codesets.SonySircCodeset.addr1, port)
        val r = t.send(Command.Power) as SendResult.Failed
        assertEquals("40 kHz not supported by this blaster", r.reason)
        assertTrue(port.sent.isEmpty())
        assertFalse(t.isReachable())
    }
}
