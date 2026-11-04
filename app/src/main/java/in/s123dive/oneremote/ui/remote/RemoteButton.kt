package `in`.s123dive.oneremote.ui.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTapGestures
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A remote key. Haptic tick on press; when [repeat] is true, holding re-sends every 150 ms after
 * 400 ms (volume / channel). [enabled]=false greys it out and routes the tap to [onDisabled] so
 * the UI can say why instead of doing nothing. [onLongPress] is used for routing overrides.
 */
@Composable
fun RemoteButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    repeat: Boolean = false,
    haptics: Boolean = true,
    accent: Boolean = false,
    onDisabled: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }
    val bg = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        pressed -> MaterialTheme.colorScheme.primary
        accent -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        pressed -> MaterialTheme.colorScheme.onPrimary
        accent -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier
            .defaultMinSize(minWidth = 56.dp, minHeight = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .pointerInput(enabled, repeat, onClick, onLongPress) {
                detectTapGestures(
                    onLongPress = if (onLongPress != null && enabled) { { onLongPress() } } else null,
                    onPress = {
                        if (!enabled) { onDisabled?.invoke(); return@detectTapGestures }
                        pressed = true
                        if (haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick()
                        coroutineScope {
                            val repeater = if (repeat) launch {
                                delay(400)
                                while (true) { onClick(); if (haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); delay(150) }
                            } else null
                            tryAwaitRelease()
                            repeater?.cancel()
                        }
                        pressed = false
                    },
                )
            }
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) Icon(icon, contentDescription = label, tint = fg)
        else Text(label, color = fg, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 1)
    }
}
