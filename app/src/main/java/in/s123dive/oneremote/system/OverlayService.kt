package `in`.s123dive.oneremote.system

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import `in`.s123dive.oneremote.R
import `in`.s123dive.oneremote.core.Command
import `in`.s123dive.oneremote.core.Role
import `in`.s123dive.oneremote.core.Source

/** Pre-Android-11 floating remote: a draggable overlay window; buttons broadcast to [RemoteActionReceiver]. */
class OverlayService : Service() {
    private var view: View? = null
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        RemoteNotificationService.ensureChannel(this)
        val n = Notification.Builder(this, RemoteNotificationService.CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle("Floating remote").setContentText("Tap × on the bubble to close").setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1003, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(1003, n)
        if (view == null) attach()
        return START_STICKY
    }

    private fun attach() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 40; y = 200 }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(Color.parseColor("#EE1C2228")); cornerRadius = 32f }
            setPadding(16, 12, 16, 12)
        }
        val title = TextView(this).apply { text = "OneRemote  ×"; setTextColor(Color.WHITE); setOnClickListener { stopSelf() } }
        root.addView(title)
        fun row(vararg items: Triple<Int, Command, Role>) {
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for ((icon, cmd, role) in items) r.addView(ImageButton(this).apply {
                setImageResource(icon); setColorFilter(Color.WHITE); setBackgroundColor(Color.TRANSPARENT)
                setOnClickListener { sendBroadcast(RemoteActionReceiver.intent(this@OverlayService, cmd, role, Source.SCREEN)) }
            })
            root.addView(r)
        }
        row(Triple(R.drawable.ic_power, Command.Power, Role.TV), Triple(R.drawable.ic_vol_down, Command.VolDown, Role.TV), Triple(R.drawable.ic_mute, Command.Mute, Role.TV), Triple(R.drawable.ic_vol_up, Command.VolUp, Role.TV))
        row(Triple(R.drawable.ic_ch_down, Command.ChDown, Role.STB), Triple(R.drawable.ic_ok, Command.Ok, Role.STB), Triple(R.drawable.ic_ch_up, Command.ChUp, Role.STB), Triple(R.drawable.ic_input, Command.Input, Role.TV))
        // drag by the title
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0
        title.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; startX = params.x; startY = params.y; false }
                MotionEvent.ACTION_MOVE -> { params.x = startX + (e.rawX - downX).toInt(); params.y = startY + (e.rawY - downY).toInt(); wm.updateViewLayout(root, params); true }
                MotionEvent.ACTION_UP -> { v.performClick().let { false } }
                else -> false
            }
        }
        wm.addView(root, params)
        view = root
    }

    override fun onDestroy() {
        view?.let { (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(it) }
        view = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "in.s123dive.oneremote.STOP_OVERLAY"
        fun start(context: Context) { val i = Intent(context, OverlayService::class.java); if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i) }
        fun stop(context: Context) { context.startService(Intent(context, OverlayService::class.java).setAction(ACTION_STOP)) }
    }
}
