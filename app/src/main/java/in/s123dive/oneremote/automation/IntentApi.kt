package `in`.s123dive.oneremote.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import `in`.s123dive.oneremote.OneRemoteApplication
import `in`.s123dive.oneremote.core.AppState
import `in`.s123dive.oneremote.core.Command
import `in`.s123dive.oneremote.core.LogEntry
import `in`.s123dive.oneremote.core.Role
import `in`.s123dive.oneremote.core.RouteRequest
import `in`.s123dive.oneremote.core.RouteResult
import `in`.s123dive.oneremote.core.Source
import kotlinx.coroutines.launch

/**
 * External commands (Tasker, Automate, adb):
 *
 *   am broadcast -a in.s123dive.oneremote.SEND -p in.s123dive.oneremote --es room "Hall" --es role TV --es cmd VOL_UP
 *   am broadcast -a in.s123dive.oneremote.SEND -p in.s123dive.oneremote --es macro "Watch TV"
 *
 * `room` is a room name or id (default: the last-used room); `role` is TV | STB | EXTRA (default TV);
 * `cmd` is a [Command.key]. Unknown values are logged as UNSUPPORTED, never ignored silently.
 */
class IntentApi : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val graph = (context.applicationContext as OneRemoteApplication).graph
        val state = graph.repository.current()
        val roomParam = intent.getStringExtra("room")
        val room = IntentRouting.resolveRoom(state, roomParam, graph.lastRoomId())
        val macroName = intent.getStringExtra("macro")
        val cmdKey = intent.getStringExtra("cmd")
        val role = intent.getStringExtra("role")?.uppercase()?.let { runCatching { Role.valueOf(it) }.getOrNull() } ?: Role.TV
        val pending = goAsync()
        graph.appScope.launch {
            try {
                when {
                    room == null -> graph.log.add(entry("room '${roomParam ?: "(last)"}' not found", cmdKey ?: macroName ?: "-"))
                    macroName != null -> {
                        val macro = Macros.find(state, room.id, macroName)
                        if (macro == null) graph.log.add(entry("macro '$macroName' not found in ${room.name}", macroName))
                        else graph.macros.run(macro, Source.INTENT)
                    }
                    cmdKey != null -> {
                        val cmd = Command.fromKey(cmdKey)
                        if (cmd == null) graph.log.add(entry("unknown cmd '$cmdKey'", cmdKey))
                        else graph.router.send(RouteRequest(room.id, role, cmd, Source.INTENT))
                    }
                    else -> graph.log.add(entry("neither cmd nor macro given", "-"))
                }
            } finally { pending.finish() }
        }
    }

    private fun entry(reason: String, cmd: String) = LogEntry(System.currentTimeMillis(), "-", "INTENT", cmd, "-", "-", "UNSUPPORTED: $reason", 0)

    companion object {
        const val ACTION = "in.s123dive.oneremote.SEND"
    }
}

/** Pure part of the Intent API, unit-tested without Android. */
object IntentRouting {
    /** `room` may be an id or a (case-insensitive) name; blank means the last-used room, else the first. */
    fun resolveRoom(state: AppState, param: String?, lastRoomId: String?): `in`.s123dive.oneremote.core.Room? {
        if (param.isNullOrBlank()) return state.room(lastRoomId ?: "") ?: state.rooms.firstOrNull()
        return state.room(param) ?: state.rooms.firstOrNull { it.name.equals(param.trim(), ignoreCase = true) }
    }

    fun describeResult(r: RouteResult) = r.summary
}
