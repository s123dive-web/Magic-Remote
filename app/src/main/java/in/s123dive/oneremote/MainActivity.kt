package `in`.s123dive.oneremote

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import `in`.s123dive.oneremote.core.Command
import `in`.s123dive.oneremote.core.Role
import `in`.s123dive.oneremote.core.RouteRequest
import `in`.s123dive.oneremote.core.Source
import `in`.s123dive.oneremote.ui.OneRemoteApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    private val graph get() = (application as OneRemoteApplication).graph
    private var longPressFired = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { OneRemoteApp() }
    }

    override fun onResume() {
        super.onResume()
        graph.capabilities.refresh()
    }

    /**
     * Phone volume keys drive the current room's TV while the app is in front:
     * tap = Vol±, long-press = Mute. Disabled in Settings, or when no room has a TV,
     * the keys fall through to the phone's own volume.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) return super.onKeyDown(keyCode, event)
        val room = currentTvRoom() ?: return super.onKeyDown(keyCode, event)
        if (event.repeatCount == 0) { event.startTracking(); longPressFired = false; return true }
        if (event.repeatCount % 3 == 0) send(room, if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) Command.VolUp else Command.VolDown)
        return true
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) return super.onKeyLongPress(keyCode, event)
        val room = currentTvRoom() ?: return super.onKeyLongPress(keyCode, event)
        longPressFired = true
        send(room, Command.Mute)
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) return super.onKeyUp(keyCode, event)
        val room = currentTvRoom() ?: return super.onKeyUp(keyCode, event)
        if (!longPressFired && !event.isCanceled && event.eventTime - event.downTime < 500) {
            send(room, if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) Command.VolUp else Command.VolDown)
        }
        return true
    }

    private fun currentTvRoom(): String? {
        val settings = runBlocking { graph.settings.settings.first() }
        if (!settings.volumeKeys) return null
        val state = graph.repository.current()
        val room = state.room(settings.lastRoomId ?: "") ?: state.rooms.firstOrNull { it.tvId != null } ?: return null
        return room.id.takeIf { room.tvId != null }
    }

    private fun send(roomId: String, cmd: Command) {
        graph.appScope.launch { graph.router.send(RouteRequest(roomId, Role.TV, cmd, Source.VOLUME_KEYS)) }
    }
}
