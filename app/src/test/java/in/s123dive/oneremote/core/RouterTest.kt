package `in`.s123dive.oneremote.core

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouterTest {
    private class FakeTransport(
        override val kind: TransportKind,
        override val capabilities: Set<Capability>,
        var reachable: Boolean = true,
        val fail: String? = null,
        val throwOnSend: Boolean = false,
    ) : Transport {
        val sent = mutableListOf<Command>()
        override fun describe(cmd: Command) = "${kind.badge}:${cmd.key}"
        override suspend fun send(cmd: Command): SendResult {
            if (throwOnSend) throw IllegalStateException("socket closed")
            sent += cmd
            return if (fail != null) SendResult.Failed(describe(cmd), fail) else SendResult.Ok(describe(cmd))
        }
        override suspend fun isReachable() = reachable
    }

    private class FakeDevice(override val instance: DeviceInstance, override val transports: List<Transport>) : Device

    private val tv = DeviceInstance("tv1", "r1", "Hall TV", DeviceType.LG_WEBOS, listOf(TransportKind.WIFI, TransportKind.IR))
    private val stb = DeviceInstance("stb1", "r1", "Airtel", DeviceType.AIRTEL_HD, listOf(TransportKind.IR))
    private val state = AppState(rooms = listOf(Room("r1", "Hall", tvId = "tv1", stbId = "stb1")), devices = listOf(tv, stb))

    private fun router(log: SendLog, registry: DeviceRegistry) = Router({ state }, registry, log, clock = { 1000L })

    @Test
    fun wifiFirstWhenItWorks() = runTest {
        val wifi = FakeTransport(TransportKind.WIFI, setOf(Capability.VOLUME, Capability.POWER))
        val ir = FakeTransport(TransportKind.IR, setOf(Capability.VOLUME, Capability.POWER))
        val log = SendLog()
        val r = router(log) { if (it.id == "tv1") FakeDevice(it, listOf(wifi, ir)) else null }
        val result = r.send(RouteRequest("r1", Role.TV, Command.VolUp))
        assertEquals(RouteResult.Sent(TransportKind.WIFI, "Wi-Fi:VOL_UP", 0), result)
        assertEquals(listOf(Command.VolUp), wifi.sent)
        assertTrue(ir.sent.isEmpty())
        assertEquals(1, log.entries.value.size)
        assertEquals("OK", log.entries.value[0].result)
        assertEquals("TV/screen", log.entries.value[0].role)
    }

    @Test
    fun fallsBackToIrWhenWifiFailsAndLogsBoth() = runTest {
        val wifi = FakeTransport(TransportKind.WIFI, setOf(Capability.VOLUME), fail = "timeout")
        val ir = FakeTransport(TransportKind.IR, setOf(Capability.VOLUME))
        val log = SendLog()
        val r = router(log) { FakeDevice(it, listOf(wifi, ir)) }
        val result = r.send(RouteRequest("r1", Role.TV, Command.VolDown, Source.VOLUME_KEYS))
        assertEquals(TransportKind.IR, (result as RouteResult.Sent).transport)
        val entries = log.entries.value
        assertEquals(2, entries.size)
        assertEquals("OK", entries[0].result)
        assertEquals("IR", entries[0].transport)
        assertEquals("FAILED: timeout", entries[1].result)
        assertEquals("Wi-Fi", entries[1].transport)
    }

    @Test
    fun exceptionsInsideTransportBecomeFailedNotCrash() = runTest {
        val wifi = FakeTransport(TransportKind.WIFI, setOf(Capability.VOLUME), throwOnSend = true)
        val log = SendLog()
        val r = router(log) { FakeDevice(it, listOf(wifi)) }
        val result = r.send(RouteRequest("r1", Role.TV, Command.Mute))
        assertEquals(RouteResult.Failed("socket closed", TransportKind.WIFI), result)
        assertEquals("FAILED: socket closed", log.entries.value[0].result)
    }

    @Test
    fun skipsTransportsThatLackTheCapability() = runTest {
        val wifi = FakeTransport(TransportKind.WIFI, setOf(Capability.VOLUME))
        val ir = FakeTransport(TransportKind.IR, setOf(Capability.VOLUME, Capability.DIGITS))
        val log = SendLog()
        val r = router(log) { FakeDevice(it, listOf(wifi, ir)) }
        val result = r.send(RouteRequest("r1", Role.TV, Command.Digit(3)))
        assertEquals(TransportKind.IR, (result as RouteResult.Sent).transport)
        assertTrue(wifi.sent.isEmpty())
    }

    @Test
    fun unsupportedEverywhereIsExplicitAndLogged() = runTest {
        val ir = FakeTransport(TransportKind.IR, setOf(Capability.VOLUME))
        val log = SendLog()
        val r = router(log) { FakeDevice(it, listOf(ir)) }
        val result = r.send(RouteRequest("r1", Role.STB, Command.Text("hi")))
        assertTrue(result is RouteResult.Unsupported)
        assertTrue((result as RouteResult.Unsupported).reason.contains("cannot TEXT:hi"))
        assertTrue(log.entries.value[0].result.startsWith("UNSUPPORTED"))
    }

    @Test
    fun missingRoomRoleDeviceAndDriverAreDistinctMessages() = runTest {
        val log = SendLog()
        val noDrivers = router(log) { null }
        assertEquals("no room 'nope'", (noDrivers.send(RouteRequest("nope", Role.TV, Command.Power)) as RouteResult.Unsupported).reason)
        assertEquals("room 'Hall' has no extra", (noDrivers.send(RouteRequest("r1", Role.EXTRA, Command.Power)) as RouteResult.Unsupported).reason)
        assertEquals("no driver for LG webOS TV yet", (noDrivers.send(RouteRequest("r1", Role.TV, Command.Power)) as RouteResult.Unsupported).reason)
        assertEquals(3, log.entries.value.size)
    }

    @Test
    fun diagnosticSendsAreLoggedUnderDebug() = runTest {
        val log = SendLog()
        val r = router(log) { null }
        val ok = r.diagnostic("LG Power @ 38 kHz", TransportKind.IR, "NEC 0x04/0x08") { SendResult.Ok("NEC 0x04/0x08") }
        assertTrue(ok is RouteResult.Sent)
        val bad = r.diagnostic("Sony Power @ 40 kHz", TransportKind.IR, "SIRC12 21/1") { SendResult.Failed("SIRC12 21/1", "40.0 kHz not supported") }
        assertEquals(RouteResult.Failed("40.0 kHz not supported", TransportKind.IR), bad)
        assertEquals("DEBUG", log.entries.value[0].role)
        assertEquals("FAILED: 40.0 kHz not supported", log.entries.value[0].result)
        assertEquals("OK", log.entries.value[1].result)
    }
}
