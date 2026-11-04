package `in`.s123dive.oneremote.automation

import `in`.s123dive.oneremote.core.AppState
import `in`.s123dive.oneremote.core.Capability
import `in`.s123dive.oneremote.core.Command
import `in`.s123dive.oneremote.core.Device
import `in`.s123dive.oneremote.core.DeviceInstance
import `in`.s123dive.oneremote.core.DeviceType
import `in`.s123dive.oneremote.core.Macro
import `in`.s123dive.oneremote.core.MacroStep
import `in`.s123dive.oneremote.core.Role
import `in`.s123dive.oneremote.core.Room
import `in`.s123dive.oneremote.core.RouteResult
import `in`.s123dive.oneremote.core.Router
import `in`.s123dive.oneremote.core.SendLog
import `in`.s123dive.oneremote.core.SendResult
import `in`.s123dive.oneremote.core.Transport
import `in`.s123dive.oneremote.core.TransportKind
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MacroRunnerTest {
    private class T : Transport {
        val sent = mutableListOf<String>()
        override val kind = TransportKind.WIFI
        override val capabilities = setOf(Capability.POWER, Capability.INPUT)
        override fun describe(cmd: Command) = cmd.key
        override suspend fun send(cmd: Command): SendResult { sent += cmd.key; return SendResult.Ok(cmd.key) }
        override suspend fun isReachable() = true
    }

    @Test
    fun runsStepsInOrderWithDelaysAndLogsEverything() = runTest {
        val tv = DeviceInstance("tv", "r1", "TV", DeviceType.LG_WEBOS, listOf(TransportKind.WIFI))
        val stb = DeviceInstance("stb", "r1", "Box", DeviceType.AIRTEL_HD, listOf(TransportKind.IR))
        val state = AppState(rooms = listOf(Room("r1", "Hall", tvId = "tv", stbId = "stb")), devices = listOf(tv, stb))
        val t = T()
        val log = SendLog()
        val router = Router({ state }, { inst -> if (inst.id == "tv") object : Device { override val instance = inst; override val transports: List<Transport> = listOf(t) } else null }, log)
        val delays = mutableListOf<Long>()
        val runner = MacroRunner(router) { delays += it }
        val macro = Macro("m", "r1", "Watch TV", listOf(MacroStep.Send(Role.TV, Command.PowerOn), MacroStep.Delay(8000), MacroStep.Send(Role.TV, Command.InputSelect("HDMI_1")), MacroStep.Send(Role.STB, Command.Power)))
        val results = runner.run(macro)
        assertEquals(listOf("POWER_ON", "INPUT_SELECT:HDMI_1"), t.sent)
        assertEquals(listOf(8000L), delays)
        assertEquals(3, results.size)
        assertTrue(results[2] is RouteResult.Unsupported) // no driver for the box in this test
        assertEquals(3, log.entries.value.size)
        assertEquals("TV/macro", log.entries.value.last().role)
        assertTrue(runner.running.value!!.done)
        assertEquals(1, runner.running.value!!.failures)
    }
}
