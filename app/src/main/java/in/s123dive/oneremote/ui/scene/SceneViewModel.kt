package `in`.s123dive.oneremote.ui.scene

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.s123dive.oneremote.AppGraph
import `in`.s123dive.oneremote.core.AppState
import `in`.s123dive.oneremote.core.Capability
import `in`.s123dive.oneremote.core.Command
import `in`.s123dive.oneremote.core.DeviceInstance
import `in`.s123dive.oneremote.core.Role
import `in`.s123dive.oneremote.core.Room
import `in`.s123dive.oneremote.core.Rooms
import `in`.s123dive.oneremote.core.RouteRequest
import `in`.s123dive.oneremote.core.Source
import `in`.s123dive.oneremote.core.capability
import `in`.s123dive.oneremote.ui.remote.LastSend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The Scene for one room: both zones, routing overrides, favourites and the last-send badge. */
class SceneViewModel(private val graph: AppGraph) : ViewModel() {
    data class Ui(
        val state: AppState,
        val room: Room?,
        val tv: DeviceInstance?,
        val stb: DeviceInstance?,
        val tvCaps: Set<Capability>,
        val stbCaps: Set<Capability>,
        val roomCount: Int,
    )

    private val selectedRoom = MutableStateFlow<String?>(null)

    val ui: StateFlow<Ui> = combine(graph.repository.state, selectedRoom, graph.settings.settings) { state, sel, settings ->
        val room = state.room(sel ?: "") ?: state.room(settings.lastRoomId ?: "") ?: state.rooms.firstOrNull()
        val tv = room?.tvId?.let { state.device(it) }
        val stb = room?.stbId?.let { state.device(it) }
        Ui(
            state, room, tv, stb,
            tvCaps = tv?.let { graph.registry.get(it)?.capabilities } ?: emptySet(),
            stbCaps = stb?.let { graph.registry.get(it)?.capabilities } ?: emptySet(),
            roomCount = state.rooms.size,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Ui(graph.repository.current(), null, null, null, emptySet(), emptySet(), 0))

    private val _last = MutableStateFlow<LastSend?>(null)
    val last: StateFlow<LastSend?> = _last

    fun selectRoom(roomId: String) {
        selectedRoom.value = roomId
        viewModelScope.launch { graph.settings.setLastRoom(roomId) }
    }

    /** Resolves the zone default through the room's overrides. */
    fun roleFor(zone: Role, cmd: Command): Role = ui.value.room?.let { Rooms.targetRole(it, zone, cmd) } ?: zone

    fun supports(zone: Role, cmd: Command): Boolean = when (roleFor(zone, cmd)) {
        Role.TV -> cmd.capability in ui.value.tvCaps
        Role.STB -> cmd.capability in ui.value.stbCaps
        Role.EXTRA -> false
    }

    /** Why a key in a zone is greyed: no device, no transport, or no capability. */
    fun unsupportedReason(zone: Role, cmd: Command): String {
        val u = ui.value
        val role = roleFor(zone, cmd)
        val inst = if (role == Role.TV) u.tv else u.stb
        if (inst == null) return "This room has no ${role.name}"
        val caps = graph.capabilities.state.value
        val device = graph.registry.get(inst)
        if (device == null || device.transports.isEmpty()) return when (inst.type) {
            `in`.s123dive.oneremote.core.DeviceType.AIRTEL_HD -> caps.airtelMessage ?: "${inst.name}: no code set"
            else -> "${inst.name}: no transport available (no IP and ${caps.lgIrMessage ?: caps.sonyIrMessage ?: "no IR code set"})"
        }
        return "${inst.name} cannot ${cmd.key}"
    }

    fun send(zone: Role, cmd: Command, source: Source = Source.SCREEN) {
        val room = ui.value.room ?: return
        val role = roleFor(zone, cmd)
        viewModelScope.launch {
            val r = graph.router.send(RouteRequest(room.id, role, cmd, source))
            _last.value = LastSend(cmd, r)
        }
    }

    fun setOverride(cmd: Command, role: Role?) {
        val room = ui.value.room ?: return
        viewModelScope.launch { graph.repository.update { s -> s.copy(rooms = s.rooms.map { if (it.id == room.id) Rooms.setOverride(it, cmd, role) else it }) } }
    }

    fun setTvCollapsed(collapsed: Boolean) { viewModelScope.launch { graph.settings.setTvZoneCollapsed(collapsed) } }
}
