package `in`.s123dive.oneremote.ui.setup

import androidx.lifecycle.ViewModel
import `in`.s123dive.oneremote.AppGraph
import `in`.s123dive.oneremote.core.AppState
import `in`.s123dive.oneremote.core.DeviceInstance
import `in`.s123dive.oneremote.core.DeviceType
import `in`.s123dive.oneremote.core.Room
import `in`.s123dive.oneremote.core.TransportKind
import `in`.s123dive.oneremote.devices.DeviceConfig
import `in`.s123dive.oneremote.ir.IrCode
import java.util.UUID

/** Mutations shared by every setup screen: rooms and device instances. */
class SetupViewModel(private val graph: AppGraph) : ViewModel() {
    val state = graph.repository.state
    val capabilities = graph.capabilities.state

    /** The room new devices go into: the given one, else the first, else a new "Home". */
    suspend fun ensureRoom(roomId: String? = null): Room {
        val current = graph.repository.current()
        roomId?.let { id -> current.room(id)?.let { return it } }
        current.rooms.firstOrNull()?.let { return it }
        val room = Room(id = newId(), name = "Home")
        graph.repository.update { it.copy(rooms = it.rooms + room) }
        return room
    }

    /**
     * Adds an instance and slots it into the room: an STB fills `stbId`, a TV fills `tvId`,
     * anything else becomes an extra. Returns the new instance id.
     */
    suspend fun addDevice(
        type: DeviceType,
        name: String,
        roomId: String?,
        transports: List<TransportKind>,
        config: DeviceConfig,
        mac: String? = null,
    ): String {
        val room = ensureRoom(roomId)
        val instance = DeviceInstance(newId(), room.id, name, type, transports, config.json, mac)
        graph.repository.update { state -> state.copy(devices = state.devices + instance, rooms = state.rooms.map { r -> if (r.id == room.id) assign(r, instance) else r }) }
        return instance.id
    }

    suspend fun updateConfig(instanceId: String, transform: (DeviceConfig) -> DeviceConfig) {
        graph.repository.update { state ->
            state.copy(devices = state.devices.map { d -> if (d.id == instanceId) d.copy(config = transform(DeviceConfig(d.config)).json) else d })
        }
        graph.registry.evict(instanceId)
    }

    suspend fun rename(instanceId: String, name: String) {
        graph.repository.update { s -> s.copy(devices = s.devices.map { if (it.id == instanceId) it.copy(name = name) else it }) }
    }

    suspend fun removeDevice(instanceId: String) {
        graph.registry.evict(instanceId)
        graph.repository.update { s ->
            s.copy(
                devices = s.devices.filterNot { it.id == instanceId },
                rooms = s.rooms.map { r ->
                    r.copy(
                        tvId = r.tvId?.takeUnless { it == instanceId },
                        stbId = r.stbId?.takeUnless { it == instanceId },
                        extras = r.extras.filterNot { it == instanceId },
                    )
                },
            )
        }
    }

    /** Expert mode: replace the custom code table for an instance. */
    suspend fun setCustomCodes(instanceId: String, codes: Map<String, IrCode>) = updateConfig(instanceId) { it.withCustomCodes(codes) }

    companion object {
        fun newId(): String = UUID.randomUUID().toString().substring(0, 8)

        fun assign(room: Room, instance: DeviceInstance): Room = when (instance.type) {
            DeviceType.AIRTEL_HD -> if (room.stbId == null) room.copy(stbId = instance.id) else room.copy(extras = room.extras + instance.id)
            DeviceType.LG_WEBOS, DeviceType.SONY_BRAVIA -> if (room.tvId == null) room.copy(tvId = instance.id) else room.copy(extras = room.extras + instance.id)
            DeviceType.ATOMBERG_FAN -> room.copy(extras = room.extras + instance.id)
        }

        fun roleOf(state: AppState, instanceId: String): String {
            val room = state.rooms.firstOrNull { it.tvId == instanceId || it.stbId == instanceId || instanceId in it.extras } ?: return "unassigned"
            return when (instanceId) { room.tvId -> "TV"; room.stbId -> "STB"; else -> "extra" }
        }
    }
}
