package `in`.s123dive.oneremote.system

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.provider.Settings
import `in`.s123dive.oneremote.R
import `in`.s123dive.oneremote.ui.bubble.BubbleActivity

/**
 * Floating mini remote. Android 11+: a Bubble (conversation notification with bubble metadata,
 * no overlay permission needed; the user may still have to allow bubbles for the app).
 * Android 8–10: a draggable overlay window from [OverlayService], which needs SYSTEM_ALERT_WINDOW,
 * requested only when the user taps the button.
 */
object BubbleLauncher {
    const val CHANNEL = "bubble"
    const val SHORTCUT_ID = "oneremote-bubble"
    const val NOTIFICATION_ID = 1002

    sealed interface Outcome { data object Shown : Outcome; data class NeedsPermission(val intent: Intent, val message: String) : Outcome; data class Unavailable(val reason: String) : Outcome }

    fun show(context: Context): Outcome {
        if (Build.VERSION.SDK_INT >= 30) return showBubble(context)
        if (!Settings.canDrawOverlays(context)) {
            val i = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
            return Outcome.NeedsPermission(i, "Allow \"Display over other apps\" for the floating remote, then try again.")
        }
        OverlayService.start(context)
        return Outcome.Shown
    }

    fun hide(context: Context) {
        if (Build.VERSION.SDK_INT >= 30) (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIFICATION_ID)
        else OverlayService.stop(context)
    }

    @androidx.annotation.RequiresApi(30)
    private fun showBubble(context: Context): Outcome {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Floating remote", NotificationManager.IMPORTANCE_HIGH).apply { setAllowBubbles(true); description = "Bubble with a mini remote" })
        }
        val person = Person.Builder().setName("OneRemote").setImportant(true).build()
        val sm = context.getSystemService(ShortcutManager::class.java)
        val shortcut = ShortcutInfo.Builder(context, SHORTCUT_ID)
            .setShortLabel("Remote")
            .setLongLived(true)
            .setPerson(person)
            .setIcon(Icon.createWithResource(context, R.drawable.ic_power))
            .setIntent(Intent(context, `in`.s123dive.oneremote.MainActivity::class.java).setAction(Intent.ACTION_VIEW))
            .build()
        sm.pushDynamicShortcut(shortcut)
        val target = PendingIntent.getActivity(context, 300, Intent(context, BubbleActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val bubble = Notification.BubbleMetadata.Builder(target, Icon.createWithResource(context, R.drawable.ic_power))
            .setDesiredHeight(360)
            .setAutoExpandBubble(true)
            .setSuppressNotification(true)
            .build()
        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("OneRemote")
            .setContentText("Floating remote")
            .setShortcutId(SHORTCUT_ID)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setStyle(Notification.MessagingStyle(person).addMessage("Tap to open the mini remote", System.currentTimeMillis(), person))
            .setBubbleMetadata(bubble)
            .setOnlyAlertOnce(true)
            .build()
        nm.notify(NOTIFICATION_ID, n)
        val allowed = nm.areBubblesAllowed()
        return if (allowed) Outcome.Shown else Outcome.NeedsPermission(
            Intent(Settings.ACTION_APP_NOTIFICATION_BUBBLE_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            "Bubbles are off for OneRemote. Allow them in the notification settings, then tap again.",
        )
    }
}
