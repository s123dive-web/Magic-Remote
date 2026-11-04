package `in`.s123dive.oneremote.system

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import `in`.s123dive.oneremote.OneRemoteApplication
import `in`.s123dive.oneremote.automation.Macros
import `in`.s123dive.oneremote.core.Command
import `in`.s123dive.oneremote.core.Role
import `in`.s123dive.oneremote.core.RouteRequest
import `in`.s123dive.oneremote.core.Source
import kotlinx.coroutines.launch

/**
 * One in-app receiver behind every notification / widget / shortcut button. The extras name the
 * role, command (key) or macro, and optionally the room; the last-used room is the default.
 */
class RemoteActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val graph = (context.applicationContext as OneRemoteApplication).graph
        val state = graph.repository.current()
        val roomId = intent.getStringExtra(EXTRA_ROOM) ?: graph.lastRoomId() ?: state.rooms.firstOrNull()?.id ?: return
        val role = intent.getStringExtra(EXTRA_ROLE)?.let { runCatching { Role.valueOf(it) }.getOrNull() } ?: Role.TV
        val source = intent.getStringExtra(EXTRA_SOURCE)?.let { runCatching { Source.valueOf(it) }.getOrNull() } ?: Source.NOTIFICATION
        val pending = goAsync()
        graph.appScope.launch {
            try {
                intent.getStringExtra(EXTRA_MACRO)?.let { name -> Macros.find(state, roomId, name)?.let { graph.macros.run(it, source) }; return@launch }
                val cmd = intent.getStringExtra(EXTRA_CMD)?.let { Command.fromKey(it) } ?: return@launch
                graph.router.send(RouteRequest(roomId, role, cmd, source))
            } finally { pending.finish() }
        }
    }

    companion object {
        const val ACTION = "in.s123dive.oneremote.ACTION"
        const val EXTRA_ROOM = "room"
        const val EXTRA_ROLE = "role"
        const val EXTRA_CMD = "cmd"
        const val EXTRA_MACRO = "macro"
        const val EXTRA_SOURCE = "source"

        fun intent(context: Context, cmd: Command?, role: Role = Role.TV, source: Source, macro: String? = null, roomId: String? = null): Intent =
            Intent(context, RemoteActionReceiver::class.java).setAction(ACTION)
                .putExtra(EXTRA_ROLE, role.name).putExtra(EXTRA_SOURCE, source.name)
                .apply { cmd?.let { putExtra(EXTRA_CMD, it.key) }; macro?.let { putExtra(EXTRA_MACRO, it) }; roomId?.let { putExtra(EXTRA_ROOM, it) } }

        fun pending(context: Context, requestCode: Int, cmd: Command?, role: Role = Role.TV, source: Source, macro: String? = null, roomId: String? = null): PendingIntent =
            PendingIntent.getBroadcast(context, requestCode, intent(context, cmd, role, source, macro, roomId), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
