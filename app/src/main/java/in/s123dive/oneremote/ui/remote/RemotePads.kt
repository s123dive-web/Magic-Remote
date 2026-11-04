package `in`.s123dive.oneremote.ui.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Input
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import `in`.s123dive.oneremote.core.Command

/** How a pad talks to the outside: send, know what's supported, explain what isn't. */
class PadActions(
    val supports: (Command) -> Boolean,
    val send: (Command) -> Unit,
    val onUnsupported: (Command) -> Unit,
    val onLongPress: ((Command) -> Unit)? = null,
    val haptics: Boolean = true,
)

@Composable
fun Key(a: PadActions, cmd: Command, label: String, modifier: Modifier = Modifier, icon: ImageVector? = null, repeat: Boolean = false, accent: Boolean = false) {
    RemoteButton(
        label = label, icon = icon, modifier = modifier, repeat = repeat, accent = accent, haptics = a.haptics,
        enabled = a.supports(cmd), onClick = { a.send(cmd) }, onDisabled = { a.onUnsupported(cmd) },
        onLongPress = a.onLongPress?.let { lp -> { lp(cmd) } },
    )
}

@Composable
fun PowerVolumeRow(a: PadActions, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Key(a, Command.Power, "Power", Modifier.weight(1f), Icons.Default.PowerSettingsNew, accent = true)
        Key(a, Command.Input, "Input", Modifier.weight(1f), Icons.Default.Input)
        Key(a, Command.VolDown, "Vol−", Modifier.weight(1f), Icons.AutoMirrored.Filled.VolumeDown, repeat = true)
        Key(a, Command.Mute, "Mute", Modifier.weight(1f), Icons.AutoMirrored.Filled.VolumeMute)
        Key(a, Command.VolUp, "Vol+", Modifier.weight(1f), Icons.AutoMirrored.Filled.VolumeUp, repeat = true)
    }
}

@Composable
fun DigitPad(a: PadActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9)).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { d -> Key(a, Command.Digit(d), "$d", Modifier.weight(1f)) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Key(a, Command.ChDown, "Ch−", Modifier.weight(1f), repeat = true)
            Key(a, Command.Digit(0), "0", Modifier.weight(1f))
            Key(a, Command.ChUp, "Ch+", Modifier.weight(1f), repeat = true)
        }
    }
}

@Composable
fun NavPad(a: PadActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Key(a, Command.Guide, "Guide", Modifier.weight(1f))
            Key(a, Command.Up, "Up", Modifier.weight(1f), Icons.Default.KeyboardArrowUp, repeat = true)
            Key(a, Command.Info, "Info", Modifier.weight(1f), Icons.Default.Info)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Key(a, Command.Left, "Left", Modifier.weight(1f), Icons.Default.KeyboardArrowLeft, repeat = true)
            Key(a, Command.Ok, "OK", Modifier.weight(1f).height(64.dp), accent = true)
            Key(a, Command.Right, "Right", Modifier.weight(1f), Icons.Default.KeyboardArrowRight, repeat = true)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Key(a, Command.Back, "Back", Modifier.weight(1f), Icons.AutoMirrored.Filled.ArrowBack)
            Key(a, Command.Down, "Down", Modifier.weight(1f), Icons.Default.KeyboardArrowDown, repeat = true)
            Key(a, Command.Exit, "Exit", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Key(a, Command.Home, "Home", Modifier.weight(1f), Icons.Default.Home)
            Key(a, Command.Menu, "Menu", Modifier.weight(1f), Icons.Default.Menu)
            Key(a, Command.Input, "Input", Modifier.weight(1f), Icons.Default.Input)
        }
    }
}

@Composable
fun PlaybackRow(a: PadActions, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Key(a, Command.Rew, "Rew", Modifier.weight(1f), Icons.Default.FastRewind)
        Key(a, Command.Play, "Play", Modifier.weight(1f), Icons.Default.PlayArrow)
        Key(a, Command.Pause, "Pause", Modifier.weight(1f), Icons.Default.Pause)
        Key(a, Command.Stop, "Stop", Modifier.weight(1f), Icons.Default.Stop)
        Key(a, Command.Fwd, "Fwd", Modifier.weight(1f), Icons.Default.FastForward)
    }
}

@Composable
fun ColourRow(a: PadActions, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Key(a, Command.Red, "Red", Modifier.weight(1f).height(40.dp))
        Key(a, Command.Green, "Green", Modifier.weight(1f).height(40.dp))
        Key(a, Command.Yellow, "Yellow", Modifier.weight(1f).height(40.dp))
        Key(a, Command.Blue, "Blue", Modifier.weight(1f).height(40.dp))
    }
}

@Composable
fun Spacer8() = androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
