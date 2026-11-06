package `in`.s123dive.oneremote.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomsTest {
    private val tv = DeviceInstance("tv", "r1", "TV", DeviceType.LG_WEBOS, listOf(TransportKind.WIFI))
    private val stb = DeviceInstance("stb", "r1", "Box", DeviceType.AIRTEL_HD, listOf(TransportKind.IR))
    private val tv2 = DeviceInstance("tv2", "r1", "TV2", DeviceType.SONY_BRAVIA, listOf(TransportKind.WIFI))
    private val state = AppState(rooms = listOf(Room("r1", "Hall", tvId = "tv", stbId = "stb", extras = listOf("tv2")), Room("r2", "Bedroom")), devices = listOf(tv, stb, tv2))

    @Test
    fun overridesWinOverZoneDefault() {
        val room = state.room("r1")!!
        assertEquals(Role.STB, Rooms.targetRole(room, Role.STB, Command.Mute))
        val o = Rooms.setOverride(room, Command.Mute, Role.TV)
        assertEquals(Role.TV, Rooms.targetRole(o, Role.STB, Command.Mute))
        assertEquals(Role.STB, Rooms.targetRole(o, Role.STB, Command.VolUp))
        assertTrue(Rooms.setOverride(o, Command.Mute, null).routingOverrides.isEmpty())
    }

    @Test
    fun assignMovesDeviceAndDemotesPreviousHolder() {
        val s = Rooms.assign(state, "tv2", "r1", Role.TV)
        val r1 = s.room("r1")!!
        assertEquals("tv2", r1.tvId)
        assertEquals(listOf("tv"), r1.extras) // old TV became an extra
        val moved = Rooms.assign(s, "stb", "r2", Role.STB)
        assertNull(moved.room("r1")!!.stbId)
        assertEquals("stb", moved.room("r2")!!.stbId)
        assertEquals("r2", moved.device("stb")!!.roomId)
        assertEquals(Role.STB, Rooms.roleOf(moved.room("r2")!!, "stb"))
        assertNull(Rooms.roleOf(moved.room("r1")!!, "stb"))
    }

    @Test
    fun roomLifecycle() {
        val s = Rooms.addRoom(state, "", "r3")
        assertEquals("Room 3", s.room("r3")!!.name)
        val renamed = Rooms.renameRoom(s, "r3", "Kids")
        assertEquals("Kids", renamed.room("r3")!!.name)
        val removed = Rooms.removeRoom(renamed, "r1")
        assertNull(removed.room("r1"))
        assertTrue(removed.devices.none { it.roomId == "r1" })
        assertEquals(2, removed.rooms.size)
    }

    @Test
    fun favouritesAndPagerOrder() {
        val s = Rooms.setFavourites(state, "r1", listOf(Favourite("News", 7)))
        assertEquals(7, s.room("r1")!!.favourites.single().channel)
        assertEquals(listOf("tv", "stb", "tv2"), Rooms.pagerDevices(state, state.room("r1")!!).map { it.id })
        val orphan = DeviceInstance("fan", "r1", "Fan", DeviceType.ATOMBERG_FAN, listOf(TransportKind.IR))
        assertEquals(listOf("tv", "stb", "tv2", "fan"), Rooms.pagerDevices(state.copy(devices = state.devices + orphan), state.room("r1")!!).map { it.id })
    }
}
