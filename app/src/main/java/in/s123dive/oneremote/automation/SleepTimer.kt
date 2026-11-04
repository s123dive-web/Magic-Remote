package `in`.s123dive.oneremote.automation

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import `in`.s123dive.oneremote.OneRemoteApplication
import `in`.s123dive.oneremote.core.Command
import `in`.s123dive.oneremote.core.Role
import `in`.s123dive.oneremote.core.RouteRequest
import `in`.s123dive.oneremote.core.Source
import kotlinx.coroutines.launch

/**
 * "TV off in N minutes": an exact alarm (inexact when the OS denies exact alarms) that sends
 * PowerOff to the room's TV through the Router. The pending timer is kept in Settings so the
 * UI can show the countdown after a restart.
 */
class SleepTimer(private val context: Context) {
    data class Pending(val roomId: String, val at: Long)

    private val alarm get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun pendingIntent(roomId: String): PendingIntent {
        val i = Intent(context, SleepTimerReceiver::class.java).setAction(ACTION).putExtra(EXTRA_ROOM, roomId)
        return PendingIntent.getBroadcast(context, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Returns a note when the alarm had to be inexact. */
    fun schedule(roomId: String, minutes: Int): String? {
        val at = System.currentTimeMillis() + minutes * 60_000L
        val pi = pendingIntent(roomId)
        val exactAllowed = Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()
        if (exactAllowed) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        return if (exactAllowed) null else "Exact alarms are not allowed for this app; the TV may switch off a few minutes late."
    }

    fun cancel(roomId: String) = alarm.cancel(pendingIntent(roomId))

    companion object {
        const val ACTION = "in.s123dive.oneremote.SLEEP_TIMER"
        const val EXTRA_ROOM = "room"
    }
}

class SleepTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val roomId = intent.getStringExtra(SleepTimer.EXTRA_ROOM) ?: return
        val graph = (context.applicationContext as OneRemoteApplication).graph
        val result = goAsync()
        graph.appScope.launch {
            try {
                graph.router.send(RouteRequest(roomId, Role.TV, Command.PowerOff, Source.MACRO))
                graph.settings.clearSleepTimer()
            } finally { result.finish() }
        }
    }
}
