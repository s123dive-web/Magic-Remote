package `in`.s123dive.oneremote.ui.remote.layouts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.s123dive.oneremote.core.Command
import `in`.s123dive.oneremote.ui.remote.PadActions

/**
 * Atomberg fan remote, one-hand layout: the keys used most (speeds, power) sit at the bottom
 * where the thumb rests. Highlights are local state only: IR is one-way, so the app shows the
 * last speed / boost / timer it sent, not what the fan is actually doing.
 */
@Composable
fun AtombergRemote(a: PadActions) {
    val c = RemoteColors
    var speed by rememberSaveable { mutableIntStateOf(0) }
    var boost by rememberSaveable { mutableStateOf(false) }
    var timerStep by rememberSaveable { mutableIntStateOf(0) } // 0 off, then 1h, 2h, 4h, 6h
    var on by rememberSaveable { mutableStateOf(true) }
    val timerLabels = listOf("off", "1h", "2h", "4h", "6h")
    val tracked = PadActions(
        supports = a.supports,
        send = { cmd ->
            a.send(cmd)
            when (cmd) {
                is Command.FanSpeed -> { speed = cmd.n; boost = false; on = true }
                Command.Boost -> { boost = !boost; on = true }
                Command.Timer -> timerStep = (timerStep + 1) % timerLabels.size
                Command.Power -> { on = !on; if (!on) { boost = false; timerStep = 0 } }
                else -> {}
            }
        },
        onUnsupported = a.onUnsupported, onLongPress = a.onLongPress, haptics = a.haptics,
    )
    RemoteBody(corner = 28.dp, width = 230.dp) {
        Text("atomberg", color = c.grey, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(if (!on) "off" else "speed ${if (speed == 0) "–" else speed}${if (boost) " · boost" else ""}${if (timerStep > 0) " · timer ${timerLabels[timerStep]}" else ""}", color = c.text, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Key(tracked, Command.Light, "LED", width = 60.dp, height = 44.dp, shape = RoundedCornerShape(22.dp), fontSize = 11.sp, bold = true)
            Key(tracked, Command.Sleep, "SLEEP", width = 60.dp, height = 44.dp, shape = RoundedCornerShape(22.dp), fontSize = 10.sp, bold = true)
            Key(tracked, Command.Timer, "TIMER", width = 60.dp, height = 44.dp, shape = RoundedCornerShape(22.dp), fontSize = 10.sp, bold = true, fill = if (timerStep > 0) Color(0xFF2E5C8A) else c.key, sub = timerLabels[timerStep])
        }
        Key(tracked, Command.Boost, "BOOST", width = 200.dp, height = 48.dp, shape = RoundedCornerShape(24.dp), fontSize = 13.sp, bold = true, fill = if (boost) Color(0xFF8A3A2E) else c.key)
        Text("speed", color = c.grey, fontSize = 10.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            (1..5).forEach { n ->
                Key(tracked, Command.FanSpeed(n), "$n", width = 38.dp, height = 48.dp, shape = RoundedCornerShape(12.dp), fontSize = 16.sp, bold = true,
                    fill = if (speed == n && on && !boost) MaterialTheme.colorScheme.primary else c.key,
                    textColor = if (speed == n && on && !boost) Color(0xFF003544) else c.text)
            }
        }
        Key(tracked, Command.Power, "Power", width = 92.dp, height = 92.dp, shape = CircleShape, fill = if (on) c.red else c.keyDark, textColor = Color.White, icon = Icons.Default.PowerSettingsNew)
    }
}
