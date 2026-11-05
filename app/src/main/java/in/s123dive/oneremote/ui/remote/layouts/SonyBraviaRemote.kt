package `in`.s123dive.oneremote.ui.remote.layouts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Input
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
 * Sony Bravia (RMT-TX series) layout: power · input / Netflix / digits with jump · info /
 * guide · options · home / pad with centre select · back / VOL rocker · mute · CH rocker /
 * colour keys / transport keys.
 */
@Composable
fun SonyBraviaRemote(a: PadActions, onApp: (String) -> Unit) {
    RemoteShell("SONY BRAVIA") {
        KeyRow(120.dp) {
            PKey(a, Command.Power, "Power", icon = Icons.Default.PowerSettingsNew, color = Color(0xFF43A047), size = 50.dp)
            PKey(a, Command.Input, "Input", icon = Icons.Default.Input, size = 50.dp)
        }
        androidx.compose.material3.OutlinedButton(onClick = { onApp("netflix") }, colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE50914))) { androidx.compose.material3.Text("NETFLIX") }
        Digits(
            a,
            bottomLeft = { PKey(a, Command.Back, "Jump", size = 56.dp) },
            bottomRight = { PKey(a, Command.Info, "Info", size = 56.dp) },
        )
        KeyRow(18.dp) {
            PKey(a, Command.Guide, "Guide", size = 52.dp, round = false)
            PKey(a, Command.Menu, "Options", size = 52.dp, round = false)
            PKey(a, Command.Home, "Home", icon = Icons.Default.Home, size = 52.dp, round = false)
        }
        DPad(a, okLabel = "+")
        KeyRow(120.dp) {
            PKey(a, Command.Back, "Back", icon = Icons.AutoMirrored.Filled.ArrowBack, size = 50.dp)
            PKey(a, Command.Exit, "Exit", size = 50.dp)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Rocker(a, Command.VolUp, Command.VolDown, "VOL")
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp)) {
                PKey(a, Command.Mute, "Mute", icon = Icons.AutoMirrored.Filled.VolumeMute, size = 56.dp)
            }
            Rocker(a, Command.ChUp, Command.ChDown, "CH")
        }
        ColourKeys(a)
        KeyRow(10.dp) {
            PKey(a, Command.Rew, "<<", icon = Icons.Default.FastRewind, size = 46.dp)
            PKey(a, Command.Play, ">", icon = Icons.Default.PlayArrow, size = 46.dp)
            PKey(a, Command.Pause, "||", icon = Icons.Default.Pause, size = 46.dp)
            PKey(a, Command.Stop, "■", icon = Icons.Default.Stop, size = 46.dp)
            PKey(a, Command.Fwd, ">>", icon = Icons.Default.FastForward, size = 46.dp)
        }
    }
}
