package `in`.s123dive.oneremote.system

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.widget.RemoteViews
import `in`.s123dive.oneremote.MainActivity
import `in`.s123dive.oneremote.R
import `in`.s123dive.oneremote.core.Command
import `in`.s123dive.oneremote.core.Role
import `in`.s123dive.oneremote.core.Source

/**
 * The notification remote: a foreground service whose notification carries Power, Vol±, Mute,
 * Ch±, Input and Mic buttons for the last-used room's TV (channels go to the box when present).
 */
class RemoteNotificationService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        ensureChannel(this)
        val notification = build(this)
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    companion object {
        const val CHANNEL = "remote"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "in.s123dive.oneremote.STOP_NOTIFICATION"

        fun start(context: Context) {
            val i = Intent(context, RemoteNotificationService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i)
        }

        fun stop(context: Context) { context.startService(Intent(context, RemoteNotificationService::class.java).setAction(ACTION_STOP)) }

        fun ensureChannel(context: Context) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "Notification remote", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Always-available TV buttons"; setShowBadge(false)
                })
            }
        }

        fun build(context: Context): Notification {
            val views = RemoteViews(context.packageName, R.layout.notification_remote)
            fun bind(id: Int, code: Int, cmd: Command?, role: Role = Role.TV) {
                views.setOnClickPendingIntent(id, RemoteActionReceiver.pending(context, code, cmd, role, Source.NOTIFICATION))
            }
            bind(R.id.btn_power, 1, Command.Power)
            bind(R.id.btn_vol_down, 2, Command.VolDown)
            bind(R.id.btn_mute, 3, Command.Mute)
            bind(R.id.btn_vol_up, 4, Command.VolUp)
            bind(R.id.btn_ch_down, 5, Command.ChDown, Role.STB)
            bind(R.id.btn_ch_up, 6, Command.ChUp, Role.STB)
            bind(R.id.btn_input, 7, Command.Input)
            val mic = Intent(context, `in`.s123dive.oneremote.voice.VoiceActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            views.setOnClickPendingIntent(R.id.btn_mic, PendingIntent.getActivity(context, 8, mic, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            val open = PendingIntent.getActivity(context, 9, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            return Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("OneRemote")
                .setCustomContentView(views)
                .setCustomBigContentView(views)
                .setStyle(Notification.DecoratedCustomViewStyle())
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .build()
        }
    }
}
