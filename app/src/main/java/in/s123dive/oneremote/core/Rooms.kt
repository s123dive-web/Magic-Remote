package `in`.s123dive.oneremote.core

/** Pure helpers for room / routing edits so the UI stays thin and the rules are testable. */
object Rooms {
    /** Which role a Scene button targets: the room's override for that command, else the zone default. */
    fun targetRole(room: Room, zoneDefault: Role, cmd: Command): Role = room.routingOverrides[cmd.key] ?: zoneDefault

    fun setOverride(room: Room, cmd: Command, role: Role?): Room =
        room.copy(routingOverrides = if (role == null) room.routingOverrides - cmd.key else room.routingOverrides + (cmd.key to role))

    fun addRoom(state: AppState, name: String, id: String): AppState = state.copy(rooms = state.rooms + Room(id, name.ifBlank { "Room ${state.rooms.size + 1}" }))

    fun renameRoom(state: AppState, roomId: String, name: String): AppState =
        state.copy(rooms = state.rooms.map { if (it.id == roomId) it.copy(name = name.ifBlank { it.name }) else it })

    /** Removing a room deletes its devices and macros too (they cannot exist without a room). */
    fun removeRoom(state: AppState, roomId: String): AppState = state.copy(
        rooms = state.rooms.filterNot { it.id == roomId },
        devices = state.devices.filterNot { it.roomId == roomId },
        macros = state.macros.filterNot { it.roomId == roomId },
    )

    /** Moves a device to a room and gives it a role; the previous holder of that role becomes an extra. */
    fun assign(state: AppState, instanceId: String, roomId: String, role: Role): AppState {
        val device = state.device(instanceId) ?: return state
        val cleared = state.rooms.map { r ->
            r.copy(tvId = r.tvId?.takeUnless { it == instanceId }, stbId = r.stbId?.takeUnless { it == instanceId }, extras = r.extras.filterNot { it == instanceId })
        }
        val rooms = cleared.map { r ->
            if (r.id != roomId) r else when (role) {
                Role.TV -> r.copy(tvId = instanceId, extras = r.extras + listOfNotNull(r.tvId))
                Role.STB -> r.copy(stbId = instanceId, extras = r.extras + listOfNotNull(r.stbId))
                Role.EXTRA -> r.copy(extras = r.extras + instanceId)
            }
        }
        return state.copy(rooms = rooms, devices = state.devices.map { if (it.id == instanceId) device.copy(roomId = roomId) else it })
    }

    fun roleOf(room: Room, instanceId: String): Role? = when (instanceId) {
        room.tvId -> Role.TV
        room.stbId -> Role.STB
        in room.extras -> Role.EXTRA
        else -> null
    }

    fun setFavourites(state: AppState, roomId: String, favourites: List<Favourite>): AppState =
        state.copy(rooms = state.rooms.map { if (it.id == roomId) it.copy(favourites = favourites) else it })

    /** The devices a room's pager shows: TV, STB, extras, then any device of the room not referenced by it. */
    fun pagerDevices(state: AppState, room: Room): List<DeviceInstance> {
        val referenced = listOfNotNull(room.tvId, room.stbId).plus(room.extras).mapNotNull { state.device(it) }
        val ids = referenced.map { it.id }.toSet()
        return referenced + state.devices.filter { it.roomId == room.id && it.id !in ids }
    }
}
