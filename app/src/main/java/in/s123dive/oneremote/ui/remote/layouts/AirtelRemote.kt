package `in`.s123dive.oneremote.ui.remote.layouts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import `in`.s123dive.oneremote.core.Command
import `in`.s123dive.oneremote.ui.remote.PadActions

/**
 * The Airtel Digital TV DTH remote, key for key:
 * tv · mute · stb / myRec · menu · a/v · iTV · fav · movies / red-arrow pad with ok /
 * VOL rocker · back · exit · CH rocker / colour keys / transport rows / digits with help · info.
 * Keys this app cannot drive on the box (tv, a/v, iTV, fav, movies, rec, help) are shown greyed
 * with their physical labels so the layout matches; tapping one explains why.
 */
@Composable
fun AirtelRemote(a: PadActions) {
    val red = Color(0xFFE53935)
    RemoteShell("airtel") {
        KeyRow(18.dp) {
            PKey(a, Command.PowerOn, "tv", size = 46.dp)
            PKey(a, Command.Mute, "mute", icon = Icons.AutoMirrored.Filled.VolumeMute, color = red, size = 46.dp)
            PKey(a, Command.Power, "stb", icon = Icons.Default.PowerSettingsNew, color = red, size = 46.dp)
        }
        KeyRow(12.dp) {
            PKey(a, Command.Play, "myRec", size = 50.dp, round = false)
            PKey(a, Command.Menu, "menu", size = 50.dp, round = false)
            PKey(a, Command.Input, "a/v", size = 50.dp, round = false)
            PKey(a, Command.Guide, "iTV", size = 50.dp, round = false)
        }
        KeyRow(12.dp) {
            PKey(a, Command.Info, "fav", size = 50.dp, round = false)
            PKey(a, Command.Home, "movies", size = 50.dp, round = false)
        }
        DPad(a, tint = red, okLabel = "ok")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Rocker(a, Command.VolUp, Command.VolDown, "VOL")
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.width(72.dp)) {
                PKey(a, Command.Back, "back", size = 56.dp)
                PKey(a, Command.Exit, "exit", size = 56.dp)
            }
            Rocker(a, Command.ChUp, Command.ChDown, "CH")
        }
        ColourKeys(a)
        KeyRow(14.dp) {
            PKey(a, Command.Rew, "<<", icon = Icons.Default.FastRewind, size = 50.dp, round = false)
            PKey(a, Command.Play, ">", icon = Icons.Default.PlayArrow, size = 50.dp, round = false)
            PKey(a, Command.Fwd, ">>", icon = Icons.Default.FastForward, size = 50.dp, round = false)
        }
        KeyRow(14.dp) {
            PKey(a, Command.Red, "rec", color = red, size = 50.dp, round = false)
            PKey(a, Command.Pause, "||", icon = Icons.Default.Pause, size = 50.dp, round = false)
            PKey(a, Command.Stop, "■", icon = Icons.Default.Stop, size = 50.dp, round = false)
        }
        Digits(
            a,
            bottomLeft = { PKey(a, Command.Info, "help", size = 56.dp) },
            bottomRight = { PKey(a, Command.Info, "info", size = 56.dp) },
        )
    }
}
