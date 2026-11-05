package `in`.s123dive.oneremote.ui.remote.layouts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import `in`.s123dive.oneremote.core.Command
import `in`.s123dive.oneremote.ui.remote.PadActions
import `in`.s123dive.oneremote.ui.remote.RemoteButton

/** Dark rounded body that frames a physical-style remote. */
@Composable
fun RemoteShell(brand: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(36.dp))
            .background(Color(0xFF15191E))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        content()
        Text(brand, style = MaterialTheme.typography.labelSmall, color = Color(0xFF8A94A0), modifier = Modifier.padding(top = 6.dp))
    }
}

/** A physical key: round or pill, optional colour (power red, colour keys). */
@Composable
fun PKey(
    a: PadActions,
    cmd: Command,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    repeat: Boolean = false,
    color: Color? = null,
    size: Dp = 52.dp,
    round: Boolean = true,
) {
    val enabled = a.supports(cmd)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(size)
                .clip(if (round) CircleShape else RoundedCornerShape(14.dp))
                .background(if (color != null && enabled) color.copy(alpha = 0.25f) else Color.Transparent),
        ) {
            RemoteButton(
                label = label, icon = icon, repeat = repeat, haptics = a.haptics, enabled = enabled,
                accent = color != null,
                onClick = { a.send(cmd) }, onDisabled = { a.onUnsupported(cmd) },
                onLongPress = a.onLongPress?.let { lp -> { lp(cmd) } },
                modifier = Modifier.size(size),
            )
        }
    }
}

/** Vol / Ch rocker: + on top, label in the middle, − below. */
@Composable
fun Rocker(a: PadActions, up: Command, down: Command, label: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(26.dp)).background(Color(0xFF232A31)), horizontalAlignment = Alignment.CenterHorizontally) {
        PKey(a, up, "+", repeat = true, size = 50.dp)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFFB0BAC4))
        PKey(a, down, "−", repeat = true, size = 50.dp)
    }
}

/** Four-way pad with OK in the middle; [tint] colours the arrows (Airtel uses red). */
@Composable
fun DPad(a: PadActions, tint: Color = Color(0xFFE1E3E6), okLabel: String = "OK", size: Dp = 210.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(Color(0xFF232A31)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            PKey(a, Command.Up, "^", color = tint, repeat = true, size = 54.dp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                PKey(a, Command.Left, "<", color = tint, repeat = true, size = 54.dp)
                PKey(a, Command.Ok, okLabel, color = Color(0xFF7CD4FD), size = 64.dp)
                PKey(a, Command.Right, ">", color = tint, repeat = true, size = 54.dp)
            }
            PKey(a, Command.Down, "v", color = tint, repeat = true, size = 54.dp)
        }
    }
}

@Composable
fun KeyRow(spacing: Dp = 10.dp, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) { content() }
}

@Composable
fun Digits(a: PadActions, bottomLeft: @Composable () -> Unit, bottomRight: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9)).forEach { row ->
            KeyRow { row.forEach { d -> PKey(a, Command.Digit(d), "$d", size = 56.dp) } }
        }
        KeyRow { bottomLeft(); PKey(a, Command.Digit(0), "0", size = 56.dp); bottomRight() }
    }
}

@Composable
fun ColourKeys(a: PadActions) {
    KeyRow(14.dp) {
        PKey(a, Command.Red, "", color = Color(0xFFE53935), size = 34.dp)
        PKey(a, Command.Green, "", color = Color(0xFF43A047), size = 34.dp)
        PKey(a, Command.Yellow, "", color = Color(0xFFFDD835), size = 34.dp)
        PKey(a, Command.Blue, "", color = Color(0xFF1E88E5), size = 34.dp)
    }
}

@Composable fun Gap(h: Dp = 4.dp) = Spacer(Modifier.height(h))
@Composable fun HGap(w: Dp = 8.dp) = Spacer(Modifier.width(w))
