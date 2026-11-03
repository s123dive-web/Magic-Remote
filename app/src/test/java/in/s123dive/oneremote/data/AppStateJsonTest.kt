package `in`.s123dive.oneremote.data

import `in`.s123dive.oneremote.core.AppState
import `in`.s123dive.oneremote.core.Command
import `in`.s123dive.oneremote.core.DeviceInstance
import `in`.s123dive.oneremote.core.DeviceType
import `in`.s123dive.oneremote.core.Favourite
import `in`.s123dive.oneremote.core.Macro
import `in`.s123dive.oneremote.core.MacroStep
import `in`.s123dive.oneremote.core.Role
import `in`.s123dive.oneremote.core.Room
import `in`.s123dive.oneremote.core.TransportKind
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStateJsonTest {
    private val sample = AppState(
        rooms = listOf(
            Room(
                id = "hall", name = "Hall", tvId = "lg1", stbId = "airtel1", extras = listOf("sony1"),
                routingOverrides = mapOf(Command.Mute.key to Role.STB, Command.Channel(5).key to Role.TV),
                favourites = listOf(Favourite("Star Sports", 120), Favourite("News", 7)),
            ),
            Room(id = "bedroom", name = "Bedroom", tvId = "sony1"),
        ),
        devices = listOf(
            DeviceInstance(
                "lg1", "hall", "Hall TV", DeviceType.LG_WEBOS, listOf(TransportKind.WIFI, TransportKind.IR),
                config = buildJsonObject {
                    put("ip", JsonPrimitive("192.168.1.20"))
                    put("clientKey", JsonPrimitive("abc123"))
                    put("secure", JsonPrimitive(true))
                },
                mac = "AA:BB:CC:DD:EE:FF",
            ),
            DeviceInstance(
                "airtel1", "hall", "Airtel", DeviceType.AIRTEL_HD, listOf(TransportKind.IR),
                config = buildJsonObject { put("codeset", JsonPrimitive("airtel-hd-01")) },
            ),
            DeviceInstance(
                "sony1", "bedroom", "Bravia", DeviceType.SONY_BRAVIA, listOf(TransportKind.WIFI, TransportKind.IR),
                config = buildJsonObject { put("ip", JsonPrimitive("192.168.1.21")); put("psk", JsonPrimitive("0000")) },
            ),
        ),
        macros = listOf(
            Macro(
                "m1", "hall", "Watch TV", builtIn = true,
                steps = listOf(
                    MacroStep.Send(Role.TV, Command.PowerOn),
                    MacroStep.Delay(8000),
                    MacroStep.Send(Role.TV, Command.InputSelect("HDMI_1")),
                    MacroStep.Send(Role.STB, Command.Power),
                    MacroStep.Send(Role.TV, Command.Text("search me")),
                    MacroStep.Send(Role.TV, Command.PointerMove(3, -4)),
                    MacroStep.Send(Role.TV, Command.Raw(byteArrayOf(0x04, 0x08))),
                    MacroStep.Send(Role.STB, Command.Digit(7)),
                    MacroStep.Send(Role.STB, Command.Channel(120)),
                    MacroStep.Send(Role.TV, Command.LaunchApp("netflix")),
                    MacroStep.Send(Role.TV, Command.Scroll(0, 9)),
                ),
            ),
        ),
    )

    @Test
    fun roundTripsEverything() {
        val text = AppStateJson.encode(sample)
        val back = AppStateJson.decode(text)
        assertEquals(sample, back)
    }

    @Test
    fun everyCommandTypeRoundTrips() {
        val cmds = Command.SIMPLE + listOf(
            Command.Digit(3), Command.Channel(999), Command.InputSelect("x"), Command.LaunchApp("y"),
            Command.PointerMove(1, 2), Command.Scroll(3, 4), Command.Text("t"), Command.Raw(byteArrayOf(9, 8)),
        )
        for (c in cmds) {
            val s = AppStateJson.json.encodeToString(Command.serializer(), c)
            assertEquals(c, AppStateJson.json.decodeFromString(Command.serializer(), s))
            assertTrue("discriminator present for ${c.key}", s.contains("\"type\""))
        }
    }

    @Test
    fun emptyStateIsStableAndVersioned() {
        val text = AppStateJson.encode(AppState())
        assertTrue(text.contains("\"version\": 1"))
        assertEquals(AppState(), AppStateJson.decode(text))
    }

    @Test
    fun unknownFieldsFromANewerBuildAreIgnored() {
        val text = """
            {"version": 7, "rooms": [{"id": "r", "name": "R", "futureField": 1}], "devices": [], "macros": [], "extra": {"a": 1}}
        """.trimIndent()
        val state = AppStateJson.decode(text)
        assertEquals(7, state.version)
        assertEquals("R", state.rooms.single().name)
    }

    @Test
    fun missingOptionalFieldsUseDefaults() {
        val state = AppStateJson.decode("""{"rooms":[{"id":"r","name":"R"}]}""")
        assertEquals(AppState.CURRENT_VERSION, state.version)
        assertEquals(Room("r", "R"), state.rooms.single())
        assertTrue(state.devices.isEmpty())
    }

    @Test
    fun secretsAreStoredVerbatimSoBackupCanWarn() {
        val text = AppStateJson.encode(sample)
        assertTrue(text.contains("\"clientKey\": \"abc123\""))
        assertTrue(text.contains("\"psk\": \"0000\""))
    }
}
