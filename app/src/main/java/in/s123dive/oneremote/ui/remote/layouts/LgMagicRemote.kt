package `in`.s123dive.oneremote.ui.remote.layouts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Input
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.s123dive.oneremote.core.Command
import `in`.s123dive.oneremote.ui.remote.PadActions
import `in`.s123dive.oneremote.ui.remote.Touchpad

/**
 * Replica of the LG Magic Remote AN-MR19BA: power · input — 1 2 3 / 4 5 6 / 7 8 9 —
 * —/LIST · 0 (QUICK ACCESS) · AD/SAP — [+ 🔊 −] · [🔇 🎤] · [⊞ + −] — home · wheel ring with
 * arrows and centre wheel (OK) · settings — back · GUIDE — NETFLIX · ⟲ · prime video —
 * red green yellow blue — MOVIES · ▶ · ‖. The wheel area is also a pointer touchpad.
 */
@Composable
fun LgMagicRemote(a: PadActions, onMic: () -> Unit, onApp: (String) -> Unit) {
    val c = RemoteColors
    RemoteBody(corner = 60.dp, width = 190.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            RoundKey(a, Command.Power, "Power", size = 36.dp, fill = c.keyDark, textColor = c.red, icon = Icons.Default.PowerSettingsNew)
            RoundKey(a, Command.Input, "Input", size = 36.dp, fill = c.keyDark, icon = Icons.Default.Input)
        }
        listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9)).forEach { row ->
            KeyRow(8.dp) { row.forEach { d -> Key(a, Command.Digit(d), "$d", width = 44.dp, height = 24.dp, shape = RoundedCornerShape(12.dp), fontSize = 12.sp, bold = true) } }
        }
        KeyRow(8.dp) {
            Key(a, Command.Guide, "—/LIST", width = 44.dp, height = 24.dp, shape = RoundedCornerShape(12.dp), fontSize = 8.sp)
            Key(a, Command.Digit(0), "0", width = 44.dp, height = 24.dp, shape = RoundedCornerShape(12.dp), fontSize = 12.sp, bold = true, sub = "QUICK ACCESS")
            Key(a, Command.Info, "AD/SAP", width = 44.dp, height = 24.dp, shape = RoundedCornerShape(12.dp), fontSize = 8.sp)
        }
        // volume column · mute/mic · channel column
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Top) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Key(a, Command.VolUp, "+", width = 36.dp, height = 30.dp, shape = CircleShape, fontSize = 18.sp, repeat = true, bold = true)
                Key(a, Command.VolUp, "vol", width = 36.dp, height = 20.dp, shape = CircleShape, fill = Color.Transparent, icon = Icons.AutoMirrored.Filled.VolumeUp)
                Key(a, Command.VolDown, "−", width = 36.dp, height = 30.dp, shape = CircleShape, fontSize = 18.sp, repeat = true, bold = true)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RoundKey(a, Command.Mute, "Mute", size = 32.dp, icon = Icons.AutoMirrored.Filled.VolumeOff)
                ActionKey("Mic", onClick = onMic, width = 40.dp, height = 40.dp, shape = CircleShape, icon = Icons.Default.Mic, textColor = Color.White, fill = c.key)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Key(a, Command.Input, "tv", width = 36.dp, height = 20.dp, shape = CircleShape, fill = Color.Transparent, icon = Icons.Default.Tv)
                Key(a, Command.ChUp, "+", width = 36.dp, height = 30.dp, shape = CircleShape, fontSize = 18.sp, repeat = true, bold = true)
                Key(a, Command.ChDown, "−", width = 36.dp, height = 30.dp, shape = CircleShape, fontSize = 18.sp, repeat = true, bold = true)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            RoundKey(a, Command.Home, "Home", size = 32.dp, icon = Icons.Default.Home)
            RoundKey(a, Command.Menu, "Settings", size = 32.dp, icon = Icons.Default.Settings)
        }
        // the wheel: ring with arrows, centre wheel = OK; the ring is also the touchpad
        Box(Modifier.size(160.dp).clip(CircleShape).background(c.keyDark).border(2.dp, c.bodyEdge, CircleShape), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Key(a, Command.Up, "▲", width = 50.dp, height = 36.dp, fill = Color.Transparent, textColor = c.grey, fontSize = 14.sp, repeat = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Key(a, Command.Left, "◀", width = 36.dp, height = 50.dp, fill = Color.Transparent, textColor = c.grey, fontSize = 14.sp, repeat = true)
                    Box(Modifier.size(70.dp).clip(CircleShape).background(c.key).border(3.dp, Color(0xFF0D0F11), CircleShape), contentAlignment = Alignment.Center) {
                        RoundKey(a, Command.Ok, "OK", size = 56.dp, fill = c.key, textColor = c.text, fontSize = 11.sp)
                    }
                    Key(a, Command.Right, "▶", width = 36.dp, height = 50.dp, fill = Color.Transparent, textColor = c.grey, fontSize = 14.sp, repeat = true)
                }
                Key(a, Command.Down, "▼", width = 50.dp, height = 36.dp, fill = Color.Transparent, textColor = c.grey, fontSize = 14.sp, repeat = true)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Key(a, Command.Back, "↩", width = 40.dp, height = 26.dp, shape = RoundedCornerShape(13.dp), fontSize = 14.sp)
            Key(a, Command.Guide, "≡ GUIDE", width = 50.dp, height = 26.dp, shape = RoundedCornerShape(13.dp), fontSize = 8.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            ActionKey("NETFLIX", onClick = { onApp("netflix") }, width = 56.dp, height = 22.dp, fill = c.red, textColor = Color.White, fontSize = 8.sp)
            Key(a, Command.Exit, "⟲", width = 30.dp, height = 22.dp, shape = CircleShape, fontSize = 12.sp)
            ActionKey("prime video", onClick = { onApp("prime") }, width = 56.dp, height = 22.dp, fill = Color(0xFF1F6FB2), textColor = Color.White, fontSize = 8.sp)
        }
        KeyRow(10.dp) {
            Key(a, Command.Red, "", width = 26.dp, height = 12.dp, shape = RoundedCornerShape(6.dp), fill = c.red)
            Key(a, Command.Green, "", width = 26.dp, height = 12.dp, shape = RoundedCornerShape(6.dp), fill = c.green)
            Key(a, Command.Yellow, "", width = 26.dp, height = 12.dp, shape = RoundedCornerShape(6.dp), fill = c.yellow)
            Key(a, Command.Blue, "", width = 26.dp, height = 12.dp, shape = RoundedCornerShape(6.dp), fill = c.blue)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            ActionKey("MOVIES", onClick = { onApp("movies") }, width = 50.dp, height = 22.dp, fontSize = 8.sp)
            Key(a, Command.Play, "▶", width = 30.dp, height = 22.dp, shape = CircleShape, icon = Icons.Default.PlayArrow)
            Key(a, Command.Pause, "‖", width = 30.dp, height = 22.dp, shape = CircleShape, icon = Icons.Default.Pause)
        }
        Text("LG", color = c.grey, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
        Touchpad(a, Modifier.padding(top = 4.dp).height(90.dp))
    }
}
