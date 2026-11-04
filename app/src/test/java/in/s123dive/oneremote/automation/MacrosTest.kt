package `in`.s123dive.oneremote.automation

import `in`.s123dive.oneremote.core.AppState
import `in`.s123dive.oneremote.core.Command
import `in`.s123dive.oneremote.core.DeviceInstance
import `in`.s123dive.oneremote.core.DeviceType
import `in`.s123dive.oneremote.core.Macro
import `in`.s123dive.oneremote.core.MacroStep
import `in`.s123dive.oneremote.core.Role
import `in`.s123dive.oneremote.core.Room
import `in`.s123dive.oneremote.core.TransportKind
import `in`.s123dive.oneremote.devices.DeviceConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MacrosTest {
    private val tv = DeviceInstance("tv", "r1", "TV", DeviceType.LG_WEBOS, listOf(TransportKind.WIFI), DeviceConfig.empty().with(DeviceConfig.STB_INPUT, "HDMI_1").json)
    private val stb = DeviceInstance("stb", "r1", "Box", DeviceType.AIRTEL_HD, listOf(TransportKind.IR))
    private val state = AppState(rooms = listOf(Room("r1", "Hall", tvId = "tv", stbId = "stb"), Room("r2", "Empty")), devices = listOf(tv, stb))

    @Test
    fun builtInsUseTheStbInputWhenKnown() {
        val macros = Macros.builtIns(state.room("r1")!!, state)
        val watch = macros.first { it.name == Macros.WATCH_TV }
        assertEquals(listOf(MacroStep.Send(Role.TV, Command.PowerOn), MacroStep.Delay(8000), MacroStep.Send(Role.TV, Command.InputSelect("HDMI_1")), MacroStep.Send(Role.STB, Command.Power)), watch.steps)
        val off = macros.first { it.name == Macros.ALL_OFF }
        assertEquals(listOf(MacroStep.Send(Role.STB, Command.Power), MacroStep.Send(Role.TV, Command.PowerOff)), off.steps)
        assertTrue(watch.builtIn)
    }

    @Test
    fun ensureBuiltInsIsIdempotentAndSkipsEmptyRooms() {
        val s1 = Macros.ensureBuiltIns(state)
        assertEquals(2, s1.macros.size)
        assertTrue(s1.macros.all { it.roomId == "r1" })
        val s2 = Macros.ensureBuiltIns(s1)
        assertSame(s1, s2)
        val edited = Macros.upsert(s1, s1.macros[0].copy(steps = emptyList()))
        assertEquals(0, Macros.ensureBuiltIns(edited).macros[0].steps.size) // user edit kept
    }

    @Test
    fun findIsCaseInsensitiveAndRoomScoped() {
        val s = Macros.ensureBuiltIns(state)
        assertEquals("builtin-watch-r1", Macros.find(s, "r1", "watch tv")!!.id)
        assertNull(Macros.find(s, "r2", "Watch TV"))
        assertEquals("builtin-off-r1", Macros.find(s, null, " all off ")!!.id)
        assertEquals(1, Macros.remove(s, "builtin-off-r1").macros.size)
    }

    @Test
    fun resolveRoomByNameIdOrLast() {
        assertEquals("r1", IntentRouting.resolveRoom(state, "hall", null)!!.id)
        assertEquals("r2", IntentRouting.resolveRoom(state, "r2", null)!!.id)
        assertEquals("r2", IntentRouting.resolveRoom(state, null, "r2")!!.id)
        assertEquals("r1", IntentRouting.resolveRoom(state, "", null)!!.id)
        assertNull(IntentRouting.resolveRoom(state, "Kitchen", null))
    }

    @Test
    fun upsertReplacesById() {
        val m = Macro("m1", "r1", "A", emptyList())
        val s = Macros.upsert(Macros.upsert(state, m), m.copy(name = "B"))
        assertEquals(1, s.macros.size); assertEquals("B", s.macros[0].name)
    }
}
