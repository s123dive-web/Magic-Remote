package `in`.s123dive.oneremote.ui.remote.layouts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import kotlin.math.min

/**
 * Draws [content] at its fixed design size and scales it uniformly to fit the space it is given,
 * so a tall remote fits a phone screen (OnePlus Open outer or inner display) without scrolling.
 * Touch input is scaled with the graphics layer, so keys stay tappable.
 */
@Composable
fun FitScale(designWidth: Dp, designHeight: Dp, modifier: Modifier = Modifier, maxScale: Float = 1.35f, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val scale = min(maxWidth / designWidth, maxHeight / designHeight).coerceIn(0.35f, maxScale)
        Box(Modifier.size(designWidth * scale, designHeight * scale)) {
            Box(
                Modifier
                    .requiredSize(designWidth, designHeight)
                    .graphicsLayer { scaleX = scale; scaleY = scale; transformOrigin = TransformOrigin(0f, 0f) },
            ) { content() }
        }
    }
}
