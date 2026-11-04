package `in`.s123dive.oneremote.data

import `in`.s123dive.oneremote.core.AppState
import `in`.s123dive.oneremote.core.DeviceInstance
import `in`.s123dive.oneremote.core.DeviceType
import `in`.s123dive.oneremote.core.Macro
import `in`.s123dive.oneremote.core.Room
import `in`.s123dive.oneremote.core.TransportKind
import `in`.s123dive.oneremote.devices.DeviceConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {
    private val lg = DeviceInstance("lg", "r1", "TV", DeviceType.LG_WEBOS, listOf(TransportKind.WIFI), DeviceConfig.empty().with(DeviceConfig.IP, "1.2.3.4").with(DeviceConfig.CLIENT_KEY, "secret-key").json)
    private val state = AppState(rooms = listOf(Room("r1", "Hall", tvId = "lg")), devices = listOf(lg), macros = listOf(Macro("m1", "r1", "A", emptyList())))

    @Test
    fun exportRoundTripsAndCountsSecrets() {
        val text = Backup.export(state, now = 42)
        assertTrue(text.contains("\"format\": \"oneremote-backup\""))
        assertTrue(text.contains("secret-key"))
        assertEquals(1, Backup.secretCount(state))
        val parsed = Backup.parse(text) as Backup.Parsed.Ok
        assertEquals(state, parsed.file.state)
        assertEquals(42L, parsed.file.exportedAt)
    }

    @Test
    fun exportWithoutSecretsStripsOnlySecrets() {
        val text = Backup.exportWithoutSecrets(state)
        assertFalse(text.contains("secret-key"))
        assertTrue(text.contains("1.2.3.4"))
        assertEquals(0, Backup.secretCount((Backup.parse(text) as Backup.Parsed.Ok).file.state))
    }

    @Test
    fun rejectsForeignJson() {
        assertTrue(Backup.parse("{\"hello\":1}") is Backup.Parsed.Err)
        assertTrue(Backup.parse("not json") is Backup.Parsed.Err)
        assertTrue((Backup.parse("{\"format\":\"other\",\"exportedAt\":1,\"state\":{}}") as Backup.Parsed.Err).reason.contains("other"))
    }

    @Test
    fun mergeKeepsExistingIdsAndAddsNew() {
        val incoming = state.copy(rooms = listOf(Room("r1", "Renamed"), Room("r2", "Bedroom")), devices = emptyList(), macros = listOf(Macro("m2", "r2", "B", emptyList())))
        val merged = Backup.merge(state, incoming)
        assertEquals("Hall", merged.room("r1")!!.name)
        assertEquals(2, merged.rooms.size)
        assertEquals(1, merged.devices.size)
        assertEquals(setOf("m1", "m2"), merged.macros.map { it.id }.toSet())
    }
}
