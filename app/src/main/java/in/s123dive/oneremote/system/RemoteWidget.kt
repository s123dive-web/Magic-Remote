package `in`.s123dive.oneremote.system

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import `in`.s123dive.oneremote.MainActivity
import `in`.s123dive.oneremote.OneRemoteApplication
import `in`.s123dive.oneremote.R
import `in`.s123dive.oneremote.core.Command
import `in`.s123dive.oneremote.core.Role
import `in`.s123dive.oneremote.core.Source

/** 2×2: Power, Vol±, Mute. */
class SmallWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) { ids.forEach { manager.updateAppWidget(it, RemoteWidget.small(context)) } }
}

/** 4×2: adds Ch±, OK and the first three favourite channels of the last room. */
class LargeWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) { ids.forEach { manager.updateAppWidget(it, RemoteWidget.large(context)) } }
}

object RemoteWidget {
    private fun RemoteViews.bind(context: Context, id: Int, code: Int, cmd: Command, role: Role = Role.TV) {
        setOnClickPendingIntent(id, RemoteActionReceiver.pending(context, 100 + code, cmd, role, Source.WIDGET))
    }

    fun small(context: Context): RemoteViews = RemoteViews(context.packageName, R.layout.widget_small).apply {
        bind(context, R.id.btn_power, 1, Command.Power)
        bind(context, R.id.btn_vol_down, 2, Command.VolDown)
        bind(context, R.id.btn_mute, 3, Command.Mute)
        bind(context, R.id.btn_vol_up, 4, Command.VolUp)
        setOnClickPendingIntent(R.id.title, PendingIntent.getActivity(context, 199, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
    }

    fun large(context: Context): RemoteViews = RemoteViews(context.packageName, R.layout.widget_large).apply {
        bind(context, R.id.btn_power, 1, Command.Power)
        bind(context, R.id.btn_vol_down, 2, Command.VolDown)
        bind(context, R.id.btn_mute, 3, Command.Mute)
        bind(context, R.id.btn_vol_up, 4, Command.VolUp)
        bind(context, R.id.btn_ch_down, 5, Command.ChDown, Role.STB)
        bind(context, R.id.btn_ok, 6, Command.Ok, Role.STB)
        bind(context, R.id.btn_ch_up, 7, Command.ChUp, Role.STB)
        val graph = (context.applicationContext as OneRemoteApplication).graph
        val state = graph.repository.current()
        val room = state.room(graph.lastRoomId() ?: "") ?: state.rooms.firstOrNull()
        val favs = room?.favourites.orEmpty()
        listOf(R.id.fav1, R.id.fav2, R.id.fav3).forEachIndexed { i, id ->
            val f = favs.getOrNull(i)
            setTextViewText(id, f?.name ?: "–")
            if (f != null) bind(context, id, 10 + i, Command.Channel(f.channel), Role.STB)
        }
        setTextViewText(R.id.title, room?.name ?: "OneRemote")
        setOnClickPendingIntent(R.id.title, PendingIntent.getActivity(context, 199, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
    }

    /** Re-renders every placed widget (called when rooms / favourites change). */
    fun refresh(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        manager.getAppWidgetIds(ComponentName(context, SmallWidgetProvider::class.java)).forEach { manager.updateAppWidget(it, small(context)) }
        manager.getAppWidgetIds(ComponentName(context, LargeWidgetProvider::class.java)).forEach { manager.updateAppWidget(it, large(context)) }
    }
}
