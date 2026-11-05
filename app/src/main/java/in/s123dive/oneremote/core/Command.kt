package `in`.s123dive.oneremote.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Every action a user can ask for, independent of device or transport.
 *
 * [key] is the stable text form used by logs, the Intent API (`--es cmd VOL_UP`),
 * backups and voice. Parameterised commands use `NAME:arg` (e.g. `DIGIT:5`,
 * `CHANNEL:120`, `TEXT:hello`). [Command.fromKey] is the inverse.
 */
@Serializable
sealed interface Command {
    val key: String

    @Serializable @SerialName("POWER") data object Power : Command { override val key get() = "POWER" }
    @Serializable @SerialName("POWER_ON") data object PowerOn : Command { override val key get() = "POWER_ON" }
    @Serializable @SerialName("POWER_OFF") data object PowerOff : Command { override val key get() = "POWER_OFF" }

    @Serializable @SerialName("VOL_UP") data object VolUp : Command { override val key get() = "VOL_UP" }
    @Serializable @SerialName("VOL_DOWN") data object VolDown : Command { override val key get() = "VOL_DOWN" }
    @Serializable @SerialName("MUTE") data object Mute : Command { override val key get() = "MUTE" }

    @Serializable @SerialName("CH_UP") data object ChUp : Command { override val key get() = "CH_UP" }
    @Serializable @SerialName("CH_DOWN") data object ChDown : Command { override val key get() = "CH_DOWN" }

    /** A single digit key 0–9. */
    @Serializable @SerialName("DIGIT") data class Digit(val n: Int) : Command {
        init { require(n in 0..9) { "digit must be 0..9, was $n" } }
        override val key get() = "DIGIT:$n"
    }

    /** Whole channel number: expands to digits + OK with a per-device inter-digit delay. */
    @Serializable @SerialName("CHANNEL") data class Channel(val n: Int) : Command {
        init { require(n >= 0) { "channel must be >= 0, was $n" } }
        override val key get() = "CHANNEL:$n"
    }

    @Serializable @SerialName("OK") data object Ok : Command { override val key get() = "OK" }
    @Serializable @SerialName("BACK") data object Back : Command { override val key get() = "BACK" }
    @Serializable @SerialName("HOME") data object Home : Command { override val key get() = "HOME" }
    @Serializable @SerialName("MENU") data object Menu : Command { override val key get() = "MENU" }
    @Serializable @SerialName("GUIDE") data object Guide : Command { override val key get() = "GUIDE" }
    @Serializable @SerialName("INFO") data object Info : Command { override val key get() = "INFO" }
    @Serializable @SerialName("EXIT") data object Exit : Command { override val key get() = "EXIT" }
    /** Channel list. */
    @Serializable @SerialName("LIST") data object ChannelList : Command { override val key get() = "LIST" }
    @Serializable @SerialName("FAV") data object Fav : Command { override val key get() = "FAV" }
    /** Previous / last channel (recall). */
    @Serializable @SerialName("PREV") data object Prev : Command { override val key get() = "PREV" }
    @Serializable @SerialName("SUBTITLE") data object Subtitle : Command { override val key get() = "SUBTITLE" }
    /** Audio track / AD / SAP. */
    @Serializable @SerialName("AUDIO") data object Audio : Command { override val key get() = "AUDIO" }
    @Serializable @SerialName("SLEEP") data object Sleep : Command { override val key get() = "SLEEP" }
    @Serializable @SerialName("HELP") data object Help : Command { override val key get() = "HELP" }

    @Serializable @SerialName("UP") data object Up : Command { override val key get() = "UP" }
    @Serializable @SerialName("DOWN") data object Down : Command { override val key get() = "DOWN" }
    @Serializable @SerialName("LEFT") data object Left : Command { override val key get() = "LEFT" }
    @Serializable @SerialName("RIGHT") data object Right : Command { override val key get() = "RIGHT" }

    /** Cycle / open the input picker. */
    @Serializable @SerialName("INPUT") data object Input : Command { override val key get() = "INPUT" }
    /** Select a specific input by device-specific id (e.g. `HDMI_1`, `extInput:hdmi?port=2`). */
    @Serializable @SerialName("INPUT_SELECT") data class InputSelect(val id: String) : Command {
        override val key get() = "INPUT_SELECT:$id"
    }

    @Serializable @SerialName("PLAY") data object Play : Command { override val key get() = "PLAY" }
    @Serializable @SerialName("PAUSE") data object Pause : Command { override val key get() = "PAUSE" }
    @Serializable @SerialName("STOP") data object Stop : Command { override val key get() = "STOP" }
    @Serializable @SerialName("REW") data object Rew : Command { override val key get() = "REW" }
    @Serializable @SerialName("FWD") data object Fwd : Command { override val key get() = "FWD" }
    @Serializable @SerialName("RECORD") data object Record : Command { override val key get() = "RECORD" }

    @Serializable @SerialName("RED") data object Red : Command { override val key get() = "RED" }
    @Serializable @SerialName("GREEN") data object Green : Command { override val key get() = "GREEN" }
    @Serializable @SerialName("YELLOW") data object Yellow : Command { override val key get() = "YELLOW" }
    @Serializable @SerialName("BLUE") data object Blue : Command { override val key get() = "BLUE" }

    /** Launch an app by device-specific id (e.g. `youtube.leanback.v4`). */
    @Serializable @SerialName("LAUNCH_APP") data class LaunchApp(val id: String) : Command {
        override val key get() = "LAUNCH_APP:$id"
    }

    @Serializable @SerialName("POINTER_MOVE") data class PointerMove(val dx: Int, val dy: Int) : Command {
        override val key get() = "POINTER_MOVE:$dx,$dy"
    }
    @Serializable @SerialName("POINTER_CLICK") data object PointerClick : Command { override val key get() = "POINTER_CLICK" }
    @Serializable @SerialName("SCROLL") data class Scroll(val dx: Int, val dy: Int) : Command {
        override val key get() = "SCROLL:$dx,$dy"
    }

    /** Inject text into the focused field on the TV. */
    @Serializable @SerialName("TEXT") data class Text(val s: String) : Command {
        override val key get() = "TEXT:$s"
    }

    /** Expert mode: transport-specific raw payload (e.g. NEC address+command bytes). */
    @Serializable @SerialName("RAW") class Raw(val bytes: ByteArray) : Command {
        override val key get() = "RAW:" + bytes.joinToString("") { "%02X".format(it) }
        override fun equals(other: Any?) = other is Raw && other.bytes.contentEquals(bytes)
        override fun hashCode() = bytes.contentHashCode()
        override fun toString() = "Raw(${key.removePrefix("RAW:")})"
    }

    companion object {
        /** All parameterless commands, for UIs and parsers. */
        val SIMPLE: List<Command> = listOf(
            Power, PowerOn, PowerOff, VolUp, VolDown, Mute, ChUp, ChDown,
            Ok, Back, Home, Menu, Guide, Info, Exit, ChannelList, Fav, Prev, Subtitle, Audio, Sleep, Help, Up, Down, Left, Right, Input,
            Play, Pause, Stop, Rew, Fwd, Record, Red, Green, Yellow, Blue, PointerClick,
        )
        private val simpleByKey = SIMPLE.associateBy { it.key }

        /** Parses a [key]; returns null for anything malformed rather than throwing. */
        fun fromKey(raw: String): Command? {
            val text = raw.trim()
            simpleByKey[text.uppercase()]?.let { return it }
            val idx = text.indexOf(':')
            if (idx <= 0) return null
            val name = text.substring(0, idx).uppercase()
            val arg = text.substring(idx + 1)
            return runCatching {
                when (name) {
                    "DIGIT" -> Digit(arg.toInt())
                    "CHANNEL" -> Channel(arg.toInt())
                    "INPUT_SELECT" -> InputSelect(arg)
                    "LAUNCH_APP" -> LaunchApp(arg)
                    "TEXT" -> Text(arg)
                    "POINTER_MOVE" -> arg.split(',').let { PointerMove(it[0].trim().toInt(), it[1].trim().toInt()) }
                    "SCROLL" -> arg.split(',').let { Scroll(it[0].trim().toInt(), it[1].trim().toInt()) }
                    "RAW" -> Raw(arg.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
                    else -> null
                }
            }.getOrNull()
        }
    }
}
