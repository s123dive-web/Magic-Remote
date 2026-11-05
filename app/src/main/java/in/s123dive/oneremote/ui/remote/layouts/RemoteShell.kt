package `in`.s123dive.oneremote.ui.remote.layouts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import `in`.s123dive.oneremote.core.Command
import `in`.s123dive.oneremote.ui.remote.PadActions
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Palette shared by the replicas. */
object RemoteColors {
    val body = Color(0xFF111316)
    val bodyEdge = Color(0xFF2A2F35)
    val key = Color(0xFF2C3137)
    val keyDark = Color(0xFF1B1F23)
    val keyPressed = Color(0xFF4A535C)
    val text = Color(0xFFE6E8EA)
    val red = Color(0xFFE2231A)
    val green = Color(0xFF2EAD4B)
    val yellow = Color(0xFFF2D43B)
    val white = Color(0xFFF2F2F2)
    val blue = Color(0xFF2E8FE6)
    val grey = Color(0xFF9AA3AB)
}

/** Body of the remote: a tall capsule (LG) or a long rounded bar (Airtel) with a subtle edge. */
@Composable
fun RemoteBody(corner: Dp, width: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .width(width)
                .clip(RoundedCornerShape(corner))
                .background(Brush.verticalGradient(listOf(Color(0xFF1A1D21), RemoteColors.body, Color(0xFF0B0D0F))))
                .border(1.dp, RemoteColors.bodyEdge, RoundedCornerShape(corner))
                .padding(horizontal = 14.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { content() }
    }
}

/**
 * A physical key with a real shape: [fill] colour, [textColor], optional [icon], auto-repeat on
 * hold for [repeat]. Greyed (dimmed) when the device cannot carry [cmd]; tapping explains why.
 */
@Composable
fun Key(
    a: PadActions,
    cmd: Command,
    label: String,
    modifier: Modifier = Modifier,
    width: Dp = 46.dp,
    height: Dp = 30.dp,
    shape: Shape = RoundedCornerShape(8.dp),
    fill: Color = RemoteColors.key,
    textColor: Color = RemoteColors.text,
    fontSize: TextUnit = 11.sp,
    icon: ImageVector? = null,
    repeat: Boolean = false,
    sub: String? = null,
    bold: Boolean = false,
) {
    val haptic = LocalHapticFeedback.current
    val enabled = a.supports(cmd)
    var pressed by remember { mutableStateOf(false) }
    val bg = when { !enabled -> fill.copy(alpha = 0.35f); pressed -> RemoteColors.keyPressed; else -> fill }
    val fg = if (enabled) textColor else textColor.copy(alpha = 0.45f)
    Box(
        modifier
            .size(width, height)
            .clip(shape)
            .background(bg)
            .pointerInput(enabled, repeat) {
                detectTapGestures(
                    onLongPress = a.onLongPress?.let { lp -> { lp(cmd) } },
                    onPress = {
                        if (!enabled) { a.onUnsupported(cmd); return@detectTapGestures }
                        pressed = true
                        if (a.haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        a.send(cmd)
                        coroutineScope {
                            val job = if (repeat) launch { delay(400); while (true) { a.send(cmd); delay(150) } } else null
                            tryAwaitRelease(); job?.cancel()
                        }
                        pressed = false
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(height * 0.6f))
        else Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = fg, fontSize = fontSize, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium, textAlign = TextAlign.Center, maxLines = 1, lineHeight = fontSize)
            if (sub != null) Text(sub, color = fg.copy(alpha = 0.8f), fontSize = 7.sp, lineHeight = 7.sp, maxLines = 1)
        }
    }
}

/** A tappable label that is not a device command (app hotkeys, the voice key). */
@Composable
fun ActionKey(label: String, onClick: () -> Unit, width: Dp = 64.dp, height: Dp = 26.dp, fill: Color = RemoteColors.key, textColor: Color = RemoteColors.text, fontSize: TextUnit = 9.sp, shape: Shape = RoundedCornerShape(13.dp), icon: ImageVector? = null) {
    val haptic = LocalHapticFeedback.current
    Box(
        Modifier.size(width, height).clip(shape).background(fill).pointerInput(Unit) { detectTapGestures(onPress = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick(); tryAwaitRelease() }) },
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) Icon(icon, contentDescription = label, tint = textColor, modifier = Modifier.size(height * 0.6f))
        else Text(label, color = textColor, fontSize = fontSize, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** Vertical + / − rocker: one pill with the label between the halves. */
@Composable
fun Rocker(a: PadActions, up: Command, down: Command, label: String, labelColor: Color = RemoteColors.text, width: Dp = 44.dp, height: Dp = 112.dp) {
    Column(Modifier.size(width, height).clip(RoundedCornerShape(22.dp)).background(RemoteColors.keyDark), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
        Key(a, up, "+", width = width, height = 40.dp, shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp), fill = Color.Transparent, fontSize = 18.sp, repeat = true, bold = true)
        Text(label, color = labelColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Key(a, down, "−", width = width, height = 40.dp, shape = RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp), fill = Color.Transparent, fontSize = 18.sp, repeat = true, bold = true)
    }
}

@Composable
fun KeyRow(spacing: Dp = 8.dp, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) { content() }
}

@Composable fun Gap(h: Dp = 4.dp) = Spacer(Modifier.height(h))
@Composable fun HGap(w: Dp = 8.dp) = Spacer(Modifier.width(w))

/** Circular key, e.g. power or arrows. */
@Composable
fun RoundKey(a: PadActions, cmd: Command, label: String, size: Dp = 36.dp, fill: Color = RemoteColors.key, textColor: Color = RemoteColors.text, icon: ImageVector? = null, repeat: Boolean = false, fontSize: TextUnit = 11.sp) =
    Key(a, cmd, label, width = size, height = size, shape = CircleShape, fill = fill, textColor = textColor, icon = icon, repeat = repeat, fontSize = fontSize, bold = true)
