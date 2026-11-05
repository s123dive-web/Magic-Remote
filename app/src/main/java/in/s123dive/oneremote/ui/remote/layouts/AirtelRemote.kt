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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.s123dive.oneremote.core.Command
import `in`.s123dive.oneremote.ui.remote.PadActions

/**
 * Replica of the Airtel Digital TV DTH remote (the long black one):
 * tv · mute · stb — myRec · menu · a/v · iTV — fav · movies — red-arrow pad with "ok" —
 * VOL rocker · back / exit · CH rocker — red · green · white · blue stepping diagonally —
 * ◀◀ ▶ ▶▶ — ●rec ‖ ■ — digits with letters — help · 0 · info — airtel.
 * "tv" powers the room's TV like the real key. Keys the box has no code for are dimmed.
 */
@Composable
fun AirtelRemote(a: PadActions, tv: PadActions) {
    val c = RemoteColors
    RemoteBody(corner = 26.dp, width = 230.dp) {
        // tv · mute · stb
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Key(tv, Command.Power, "tv", width = 40.dp, height = 24.dp, fill = c.keyDark, textColor = c.grey, fontSize = 10.sp)
            Key(a, Command.Mute, "mute", width = 40.dp, height = 24.dp, fill = c.keyDark, icon = Icons.AutoMirrored.Filled.VolumeOff, textColor = c.red)
            Key(a, Command.Power, "stb", width = 40.dp, height = 24.dp, fill = c.keyDark, textColor = c.red, fontSize = 10.sp, bold = true)
        }
        // myRec · menu · a/v · iTV
        KeyRow(6.dp) {
            Key(a, Command.Record, "myRec", width = 46.dp, height = 24.dp, fontSize = 9.sp)
            Key(a, Command.Menu, "menu", width = 46.dp, height = 24.dp, fontSize = 9.sp)
            Key(a, Command.Input, "a/v", width = 46.dp, height = 24.dp, fontSize = 9.sp)
            Key(a, Command.Guide, "iTV", width = 46.dp, height = 24.dp, fontSize = 9.sp)
        }
        KeyRow(6.dp) {
            Key(a, Command.Fav, "fav", width = 46.dp, height = 24.dp, fontSize = 9.sp)
            Key(a, Command.Home, "movies", width = 46.dp, height = 24.dp, fontSize = 9.sp)
        }
        // D-pad: dark rounded square, red chevrons, round ok
        Box(Modifier.size(170.dp).clip(RoundedCornerShape(28.dp)).background(c.keyDark), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Key(a, Command.Up, "^", width = 56.dp, height = 40.dp, shape = RoundedCornerShape(10.dp), fill = c.keyDark, textColor = c.red, fontSize = 20.sp, repeat = true, bold = true)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Key(a, Command.Left, "<", width = 40.dp, height = 56.dp, shape = RoundedCornerShape(10.dp), fill = c.keyDark, textColor = c.red, fontSize = 20.sp, repeat = true, bold = true)
                    RoundKey(a, Command.Ok, "ok", size = 58.dp, fill = c.key, textColor = c.red, fontSize = 14.sp)
                    Key(a, Command.Right, ">", width = 40.dp, height = 56.dp, shape = RoundedCornerShape(10.dp), fill = c.keyDark, textColor = c.red, fontSize = 20.sp, repeat = true, bold = true)
                }
                Key(a, Command.Down, "v", width = 56.dp, height = 40.dp, shape = RoundedCornerShape(10.dp), fill = c.keyDark, textColor = c.red, fontSize = 18.sp, repeat = true, bold = true)
            }
        }
        // VOL · back/exit · CH
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Rocker(a, Command.VolUp, Command.VolDown, "VOL", labelColor = c.red)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.width(60.dp)) {
                RoundKey(a, Command.Back, "back", size = 44.dp, textColor = c.red, fontSize = 9.sp)
                RoundKey(a, Command.Exit, "exit", size = 44.dp, textColor = c.red, fontSize = 9.sp)
            }
            Rocker(a, Command.ChUp, Command.ChDown, "CH", labelColor = c.red)
        }
        // colour keys stepping diagonally, as on the remote
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
            Row(Modifier.padding(start = 6.dp)) { Key(a, Command.Red, "", width = 34.dp, height = 16.dp, shape = RoundedCornerShape(4.dp), fill = c.red) }
            Row(Modifier.padding(start = 50.dp)) { Key(a, Command.Green, "", width = 34.dp, height = 16.dp, shape = RoundedCornerShape(4.dp), fill = c.green) }
            Row(Modifier.padding(start = 94.dp)) { Key(a, Command.Yellow, "", width = 34.dp, height = 16.dp, shape = RoundedCornerShape(4.dp), fill = c.white) }
            Row(Modifier.padding(start = 138.dp)) { Key(a, Command.Blue, "", width = 34.dp, height = 16.dp, shape = RoundedCornerShape(4.dp), fill = c.blue) }
        }
        KeyRow(8.dp) {
            Key(a, Command.Rew, "<<", width = 54.dp, height = 26.dp, icon = Icons.Default.FastRewind)
            Key(a, Command.Play, ">", width = 54.dp, height = 26.dp, icon = Icons.Default.PlayArrow)
            Key(a, Command.Fwd, ">>", width = 54.dp, height = 26.dp, icon = Icons.Default.FastForward)
        }
        KeyRow(8.dp) {
            Key(a, Command.Record, "● rec", width = 54.dp, height = 26.dp, textColor = c.red, fontSize = 9.sp)
            Key(a, Command.Pause, "||", width = 54.dp, height = 26.dp, icon = Icons.Default.Pause)
            Key(a, Command.Stop, "■", width = 54.dp, height = 26.dp, icon = Icons.Default.Stop)
        }
        // digits with letters
        val letters = mapOf(1 to ".,-", 2 to "abc", 3 to "def", 4 to "ghi", 5 to "jkl", 6 to "mno", 7 to "pqrs", 8 to "tuv", 9 to "wxyz", 0 to "⎵")
        listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9)).forEach { row ->
            KeyRow(8.dp) { row.forEach { d -> Key(a, Command.Digit(d), "$d", width = 54.dp, height = 30.dp, sub = letters[d], fontSize = 13.sp, bold = true) } }
        }
        KeyRow(8.dp) {
            Key(a, Command.Help, "help", width = 54.dp, height = 30.dp, fontSize = 9.sp)
            Key(a, Command.Digit(0), "0", width = 54.dp, height = 30.dp, sub = letters[0], fontSize = 13.sp, bold = true)
            Key(a, Command.Info, "info", width = 54.dp, height = 30.dp, fontSize = 9.sp)
        }
        Text("airtel", color = c.red, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
    }
}
