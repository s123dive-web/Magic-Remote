package `in`.s123dive.oneremote.core

import kotlinx.serialization.Serializable

/** Which device in a room a command is aimed at. */
@Serializable
enum class Role { TV, STB, EXTRA }

/** A user-named channel chip on the Scene screen; tapping sends Channel(n) to the STB. */
@Serializable
data class Favourite(val name: String, val channel: Int)

/**
 * One physical room: a TV plus an optional set-top box plus extras.
 * [routingOverrides] maps a [Command.key] to the role that should receive it,
 * overriding the Scene's default (TV zone -> TV, STB zone -> STB).
 */
@Serializable
data class Room(
    val id: String,
    val name: String,
    val tvId: String? = null,
    val stbId: String? = null,
    val extras: List<String> = emptyList(),
    val routingOverrides: Map<String, Role> = emptyMap(),
    val favourites: List<Favourite> = emptyList(),
) {
    fun instanceIdFor(role: Role): String? = when (role) {
        Role.TV -> tvId
        Role.STB -> stbId
        Role.EXTRA -> extras.firstOrNull()
    }
}
