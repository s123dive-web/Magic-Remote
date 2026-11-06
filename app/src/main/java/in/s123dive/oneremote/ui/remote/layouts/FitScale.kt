package `in`.s123dive.oneremote.ui.remote.layouts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Draws [content] at its fixed design size and scales it uniformly to fit the space it is given,
 * so a tall remote fits a phone screen (OnePlus Open outer or inner display) without scrolling.
 *
 * The content is measured with the exact design size and placed at the origin of a node whose
 * reported size is the scaled size, then scaled with a graphics layer from the top-left corner.
 * (Letting a plain Box hold an oversized child does not work: Compose centres the overflow, so the
 * remote ends up shifted up and left before the scale is applied.) Touch input is transformed with
 * the layer, so keys stay tappable.
 */
@Composable
fun FitScale(designWidth: Dp, designHeight: Dp, modifier: Modifier = Modifier, maxScale: Float = 1.35f, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val scale = min(maxWidth / designWidth, maxHeight / designHeight).coerceIn(0.35f, maxScale)
        Layout(
            content = {
                Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale; transformOrigin = TransformOrigin(0f, 0f) }) { content() }
            },
        ) { measurables, _ ->
            val w = designWidth.roundToPx()
            val h = designHeight.roundToPx()
            val placeable = measurables.first().measure(Constraints.fixed(w, h))
            layout((w * scale).roundToInt(), (h * scale).roundToInt()) { placeable.place(0, 0) }
        }
    }
}
