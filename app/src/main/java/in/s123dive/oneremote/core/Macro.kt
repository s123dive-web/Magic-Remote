package `in`.s123dive.oneremote.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface MacroStep {
    @Serializable @SerialName("send")
    data class Send(val role: Role, val cmd: Command) : MacroStep

    @Serializable @SerialName("delay")
    data class Delay(val ms: Long) : MacroStep
}

/** A named sequence of routed commands and delays, per room. */
@Serializable
data class Macro(
    val id: String,
    val roomId: String,
    val name: String,
    val steps: List<MacroStep>,
    val icon: String = "bolt",
    val builtIn: Boolean = false,
)
