package `in`.s123dive.oneremote.system

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import `in`.s123dive.oneremote.OneRemoteApplication
import `in`.s123dive.oneremote.core.Command
import `in`.s123dive.oneremote.core.Role
import `in`.s123dive.oneremote.core.RouteRequest
import `in`.s123dive.oneremote.core.RouteResult
import `in`.s123dive.oneremote.core.Source
import kotlinx.coroutines.launch

/** Base for the Quick Settings tiles: one command to the last room's TV, result shown in the subtitle. */
abstract class RemoteTileService(private val cmd: Command, private val label: String) : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply { state = Tile.STATE_INACTIVE; this.label = this@RemoteTileService.label; updateTile() }
    }

    override fun onClick() {
        val graph = (application as OneRemoteApplication).graph
        val state = graph.repository.current()
        val roomId = graph.lastRoomId() ?: state.rooms.firstOrNull { it.tvId != null }?.id
        val tile = qsTile
        if (roomId == null) { tile?.apply { if (android.os.Build.VERSION.SDK_INT >= 29) subtitle = "no TV set up"; updateTile() }; return }
        tile?.apply { this.state = Tile.STATE_ACTIVE; updateTile() }
        graph.appScope.launch {
            val r = graph.router.send(RouteRequest(roomId, Role.TV, cmd, Source.TILE))
            qsTile?.apply {
                this.state = Tile.STATE_INACTIVE
                if (android.os.Build.VERSION.SDK_INT >= 29) subtitle = when (r) { is RouteResult.Sent -> "via ${r.transport.badge}"; is RouteResult.Failed -> "failed"; is RouteResult.Unsupported -> "unsupported" }
                updateTile()
            }
        }
    }
}

class TvPowerTileService : RemoteTileService(Command.Power, "TV Power")
class MuteTileService : RemoteTileService(Command.Mute, "TV Mute")
