package `in`.s123dive.oneremote.automation

import `in`.s123dive.oneremote.core.AppState
import `in`.s123dive.oneremote.core.Command
import `in`.s123dive.oneremote.core.Macro
import `in`.s123dive.oneremote.core.MacroStep
import `in`.s123dive.oneremote.core.Role
import `in`.s123dive.oneremote.core.Room
import `in`.s123dive.oneremote.devices.DeviceConfig

/** Pure macro helpers: the two built-ins per room and lookup by name. */
object Macros {
    const val WATCH_TV = "Watch TV"
    const val ALL_OFF = "All off"

    /**
     * Watch TV = TV on (WoL / IR) → wait 8 s → switch the TV to the box's input (when known) → box power.
     * All off = box power → TV off. Rooms without a box get TV-only versions.
     */
    fun builtIns(room: Room, state: AppState): List<Macro> {
        val hasStb = room.stbId != null
        val stbInput = room.tvId?.let { state.device(it) }?.let { DeviceConfig(it.config).stbInput }
        val watch = buildList {
            if (room.tvId != null) { add(MacroStep.Send(Role.TV, Command.PowerOn)); add(MacroStep.Delay(8000)) }
            if (room.tvId != null && hasStb && stbInput != null) add(MacroStep.Send(Role.TV, Command.InputSelect(stbInput)))
            if (hasStb) add(MacroStep.Send(Role.STB, Command.Power))
        }
        val off = buildList {
            if (hasStb) add(MacroStep.Send(Role.STB, Command.Power))
            if (room.tvId != null) add(MacroStep.Send(Role.TV, Command.PowerOff))
        }
        return listOf(
            Macro("builtin-watch-${room.id}", room.id, WATCH_TV, watch, icon = "tv", builtIn = true),
            Macro("builtin-off-${room.id}", room.id, ALL_OFF, off, icon = "power", builtIn = true),
        )
    }

    /** Adds missing built-ins for every room that has a device; never overwrites a user-edited one. */
    fun ensureBuiltIns(state: AppState): AppState {
        val missing = state.rooms.filter { it.tvId != null || it.stbId != null }.flatMap { room ->
            builtIns(room, state).filter { b -> state.macros.none { it.id == b.id } }
        }
        return if (missing.isEmpty()) state else state.copy(macros = state.macros + missing)
    }

    fun find(state: AppState, roomId: String?, name: String): Macro? =
        state.macros.firstOrNull { (roomId == null || it.roomId == roomId) && it.name.equals(name.trim(), ignoreCase = true) }

    fun upsert(state: AppState, macro: Macro): AppState =
        state.copy(macros = if (state.macros.any { it.id == macro.id }) state.macros.map { if (it.id == macro.id) macro else it } else state.macros + macro)

    fun remove(state: AppState, macroId: String): AppState = state.copy(macros = state.macros.filterNot { it.id == macroId })
}
