package `in`.s123dive.oneremote.system

import android.app.Activity
import android.os.Bundle

/** Invisible target for app shortcuts: forwards its extras to [RemoteActionReceiver] and finishes. */
class ActionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val i = android.content.Intent(this, RemoteActionReceiver::class.java).setAction(RemoteActionReceiver.ACTION)
        intent?.extras?.let { i.putExtras(it) }
        i.putExtra(RemoteActionReceiver.EXTRA_SOURCE, `in`.s123dive.oneremote.core.Source.WIDGET.name)
        sendBroadcast(i)
        finish()
    }
}
