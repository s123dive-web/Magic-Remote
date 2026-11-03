package `in`.s123dive.oneremote.core

import kotlinx.serialization.Serializable

/**
 * Everything the user has configured. Persisted as one JSON document in DataStore.
 * [version] lets future phases migrate; unknown keys are ignored on read.
 */
@Serializable
data class AppState(
    val version: Int = CURRENT_VERSION,
    val rooms: List<Room> = emptyList(),
    val devices: List<DeviceInstance> = emptyList(),
    val macros: List<Macro> = emptyList(),
) {
    fun room(id: String): Room? = rooms.firstOrNull { it.id == id }
    fun device(id: String): DeviceInstance? = devices.firstOrNull { it.id == id }
    fun devicesIn(roomId: String): List<DeviceInstance> = devices.filter { it.roomId == roomId }

    companion object {
        const val CURRENT_VERSION = 1
    }
}
