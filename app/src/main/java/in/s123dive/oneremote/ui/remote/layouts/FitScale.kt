package `in`.s123dive.oneremote.ui.remote.layouts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Lays [content] out at its fixed design width and its own natural height, then scales it uniformly
 * so the whole remote fits the space it is given (OnePlus Open cover or inner display, any phone)
 * without scrolling, centred horizontally under the header.
 *
 * The height is measured, not guessed, so a remote always fills the available height when height is
 * the limit. The scale is applied at placement time with a layer whose origin is the top-left corner,
 * and the node reports the scaled size, so nothing overflows onto the tab row. Touch input is
 * transformed with the layer, so keys stay tappable where they are drawn.
 */
@Composable
fun FitScale(designWidth: Dp, modifier: Modifier = Modifier, maxScale: Float = 1.35f, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Layout(content = content) { measurables, constraints ->
            val w = designWidth.roundToPx()
            val placeables = measurables.map { it.measure(Constraints(minWidth = w, maxWidth = w)) }
            val h = placeables.maxOfOrNull { it.height }?.coerceAtLeast(1) ?: 1
            val fitW = if (constraints.hasBoundedWidth) constraints.maxWidth.toFloat() / w else maxScale
            val fitH = if (constraints.hasBoundedHeight) constraints.maxHeight.toFloat() / h else maxScale
            val scale = min(fitW, fitH).coerceIn(0.35f, maxScale)
            layout((w * scale).roundToInt(), (h * scale).roundToInt()) {
                placeables.forEach { p ->
                    p.placeWithLayer(0, 0) { scaleX = scale; scaleY = scale; transformOrigin = TransformOrigin(0f, 0f) }
                }
            }
        }
    }
}
