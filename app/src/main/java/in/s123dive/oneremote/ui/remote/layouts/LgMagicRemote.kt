package `in`.s123dive.oneremote.ui.remote.layouts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Input
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import `in`.s123dive.oneremote.core.Command
import `in`.s123dive.oneremote.ui.remote.PadActions
import `in`.s123dive.oneremote.ui.remote.Touchpad

/**
 * LG Magic Remote AN-MR19BA layout: power · input / digits with LIST · 0 · AD/SAP /
 * VOL rocker · mute · mic · CH rocker / home · wheel (arrows + OK) · settings / back · guide /
 * Netflix · Prime Video / colour keys / movies · play · pause. The wheel is also a touchpad:
 * drag to move the pointer, tap to click, two fingers to scroll (Wi-Fi only).
 */
@Composable
fun LgMagicRemote(a: PadActions, onMic: () -> Unit, onApp: (String) -> Unit) {
    val red = Color(0xFFE53935)
    RemoteShell("LG Magic Remote") {
        KeyRow(120.dp) {
            PKey(a, Command.Power, "Power", icon = Icons.Default.PowerSettingsNew, color = red, size = 50.dp)
            PKey(a, Command.Input, "Input", icon = Icons.Default.Input, size = 50.dp)
        }
        Digits(
            a,
            bottomLeft = { PKey(a, Command.Guide, "LIST", size = 56.dp) },
            bottomRight = { PKey(a, Command.Info, "AD/SAP", size = 56.dp) },
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Rocker(a, Command.VolUp, Command.VolDown, "VOL")
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.width(72.dp)) {
                PKey(a, Command.Mute, "Mute", icon = Icons.AutoMirrored.Filled.VolumeMute, size = 52.dp)
                PKey(a, Command.PointerClick, "Mic", icon = Icons.Default.Mic, size = 52.dp, color = Color(0xFF7CD4FD)).also { }
            }
            Rocker(a, Command.ChUp, Command.ChDown, "CH")
        }
        KeyRow(18.dp) {
            PKey(a, Command.Home, "Home", icon = Icons.Default.Home, size = 50.dp)
            DPad(a, okLabel = "OK", size = 200.dp)
            PKey(a, Command.Menu, "Settings", icon = Icons.Default.Settings, size = 50.dp)
        }
        Touchpad(a)
        KeyRow(120.dp) {
            PKey(a, Command.Back, "Back", icon = Icons.AutoMirrored.Filled.ArrowBack, size = 50.dp)
            PKey(a, Command.Guide, "GUIDE", size = 50.dp)
        }
        KeyRow(16.dp) {
            AppKey("NETFLIX", Color(0xFFE50914)) { onApp("netflix") }
            PKey(a, Command.Exit, "Exit", size = 46.dp)
            AppKey("prime video", Color(0xFF00A8E1)) { onApp("prime") }
        }
        ColourKeys(a)
        KeyRow(16.dp) {
            AppKey("MOVIES", Color(0xFF8A94A0)) { onApp("movies") }
            PKey(a, Command.Play, "Play", icon = Icons.Default.PlayArrow, size = 46.dp)
            PKey(a, Command.Pause, "Pause", icon = Icons.Default.Pause, size = 46.dp)
        }
        androidx.compose.material3.TextButton(onClick = onMic) { androidx.compose.material3.Text("Voice (phone microphone)") }
    }
}

@Composable
private fun AppKey(label: String, color: Color, onClick: () -> Unit) {
    androidx.compose.material3.OutlinedButton(onClick = onClick, colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = color)) {
        androidx.compose.material3.Text(label, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
    }
}
