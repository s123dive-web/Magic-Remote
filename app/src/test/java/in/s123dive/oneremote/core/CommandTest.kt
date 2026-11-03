package `in`.s123dive.oneremote.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommandTest {
    private val all: List<Command> = Command.SIMPLE + listOf(
        Command.Digit(0), Command.Digit(9), Command.Channel(120), Command.InputSelect("HDMI_2"),
        Command.LaunchApp("youtube.leanback.v4"), Command.PointerMove(-3, 12), Command.Scroll(0, -5),
        Command.Text("hello world: x"), Command.Raw(byteArrayOf(0x04, 0x08, 0xFF.toByte())),
    )

    @Test
    fun keysRoundTripThroughFromKey() {
        for (cmd in all) {
            assertEquals("round trip ${cmd.key}", cmd, Command.fromKey(cmd.key))
        }
    }

    @Test
    fun keysAreUnique() {
        assertEquals(all.size, all.map { it.key }.toSet().size)
    }

    @Test
    fun fromKeyIsCaseInsensitiveForSimpleCommandsAndTolerant() {
        assertEquals(Command.VolUp, Command.fromKey(" vol_up "))
        assertEquals(Command.Digit(5), Command.fromKey("DIGIT:5"))
        assertNull(Command.fromKey("DIGIT:x"))
        assertNull(Command.fromKey("DIGIT:12"))
        assertNull(Command.fromKey("NOPE"))
        assertNull(Command.fromKey(""))
        assertNull(Command.fromKey(":"))
    }

    @Test
    fun everyCommandMapsToACapability() {
        for (cmd in all) cmd.capability // must not throw; `when` is exhaustive at compile time
        assertEquals(Capability.VOLUME, Command.Mute.capability)
        assertEquals(Capability.CHANNEL, Command.Channel(1).capability)
        assertEquals(Capability.DIGITS, Command.Digit(1).capability)
        assertEquals(Capability.RAW, Command.Raw(byteArrayOf()).capability)
    }

    @Test
    fun rawEqualityIsByContent() {
        assertEquals(Command.Raw(byteArrayOf(1, 2)), Command.Raw(byteArrayOf(1, 2)))
        assertEquals("RAW:0102", Command.Raw(byteArrayOf(1, 2)).key)
    }
}
