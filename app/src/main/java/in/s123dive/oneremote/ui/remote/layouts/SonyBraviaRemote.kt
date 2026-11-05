package `in`.s123dive.oneremote.ui.remote.layouts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Input
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.s123dive.oneremote.core.Command
import `in`.s123dive.oneremote.ui.remote.PadActions

/** Sony Bravia RMT-TX series: power · input · TV — NETFLIX — digits · jump · info — guide · options · home — pad with ⊕ — back — VOL · 🔇 · CH — colour keys — transport. */
@Composable
fun SonyBraviaRemote(a: PadActions, onApp: (String) -> Unit) {
    val c = RemoteColors
    RemoteBody(corner = 24.dp, width = 210.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            RoundKey(a, Command.Power, "Power", size = 34.dp, fill = c.keyDark, textColor = c.green, icon = Icons.Default.PowerSettingsNew)
            RoundKey(a, Command.Input, "Input", size = 34.dp, fill = c.keyDark, icon = Icons.Default.Input)
        }
        ActionKey("NETFLIX", onClick = { onApp("netflix") }, width = 70.dp, height = 22.dp, fill = c.red, textColor = Color.White, fontSize = 8.sp)
        listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9)).forEach { row ->
            KeyRow(8.dp) { row.forEach { d -> Key(a, Command.Digit(d), "$d", width = 50.dp, height = 26.dp, fontSize = 12.sp, bold = true) } }
        }
        KeyRow(8.dp) {
            Key(a, Command.Back, "Jump", width = 50.dp, height = 26.dp, fontSize = 9.sp)
            Key(a, Command.Digit(0), "0", width = 50.dp, height = 26.dp, fontSize = 12.sp, bold = true)
            Key(a, Command.Info, "Info", width = 50.dp, height = 26.dp, fontSize = 9.sp)
        }
        KeyRow(8.dp) {
            Key(a, Command.Guide, "Guide", width = 50.dp, height = 24.dp, fontSize = 9.sp)
            Key(a, Command.Menu, "Options", width = 50.dp, height = 24.dp, fontSize = 9.sp)
            Key(a, Command.Home, "Home", width = 50.dp, height = 24.dp, icon = Icons.Default.Home)
        }
        Box(Modifier.size(150.dp).clip(CircleShape).background(c.keyDark), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Key(a, Command.Up, "▲", width = 50.dp, height = 34.dp, fill = Color.Transparent, fontSize = 13.sp, repeat = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Key(a, Command.Left, "◀", width = 34.dp, height = 50.dp, fill = Color.Transparent, fontSize = 13.sp, repeat = true)
                    RoundKey(a, Command.Ok, "⊕", size = 56.dp, fill = c.key, fontSize = 16.sp)
                    Key(a, Command.Right, "▶", width = 34.dp, height = 50.dp, fill = Color.Transparent, fontSize = 13.sp, repeat = true)
                }
                Key(a, Command.Down, "▼", width = 50.dp, height = 34.dp, fill = Color.Transparent, fontSize = 13.sp, repeat = true)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Key(a, Command.Back, "Back", width = 50.dp, height = 24.dp, shape = RoundedCornerShape(12.dp), fontSize = 9.sp)
            Key(a, Command.Exit, "Exit", width = 50.dp, height = 24.dp, shape = RoundedCornerShape(12.dp), fontSize = 9.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Rocker(a, Command.VolUp, Command.VolDown, "VOL")
            Column(modifier = Modifier.width(50.dp), horizontalAlignment = Alignment.CenterHorizontally) { RoundKey(a, Command.Mute, "Mute", size = 40.dp, icon = Icons.AutoMirrored.Filled.VolumeOff) }
            Rocker(a, Command.ChUp, Command.ChDown, "CH")
        }
        KeyRow(10.dp) {
            Key(a, Command.Red, "", width = 30.dp, height = 12.dp, shape = RoundedCornerShape(6.dp), fill = c.red)
            Key(a, Command.Green, "", width = 30.dp, height = 12.dp, shape = RoundedCornerShape(6.dp), fill = c.green)
            Key(a, Command.Yellow, "", width = 30.dp, height = 12.dp, shape = RoundedCornerShape(6.dp), fill = c.yellow)
            Key(a, Command.Blue, "", width = 30.dp, height = 12.dp, shape = RoundedCornerShape(6.dp), fill = c.blue)
        }
        KeyRow(6.dp) {
            Key(a, Command.Rew, "<<", width = 34.dp, height = 24.dp, icon = Icons.Default.FastRewind)
            Key(a, Command.Play, ">", width = 34.dp, height = 24.dp, icon = Icons.Default.PlayArrow)
            Key(a, Command.Pause, "||", width = 34.dp, height = 24.dp, icon = Icons.Default.Pause)
            Key(a, Command.Stop, "■", width = 34.dp, height = 24.dp, icon = Icons.Default.Stop)
            Key(a, Command.Fwd, ">>", width = 34.dp, height = 24.dp, icon = Icons.Default.FastForward)
        }
        Text("SONY", color = c.grey, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
    }
}
