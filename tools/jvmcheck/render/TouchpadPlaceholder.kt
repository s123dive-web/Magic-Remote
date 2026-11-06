package `in`.s123dive.oneremote.ui.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color

/** Stand-in for TvExtras.Touchpad (which pulls in the app graph); same footprint, drawn as a plain pad. */
@Composable
fun Touchpad(a: PadActions, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(16)).background(Color(0xFF1E222B)), contentAlignment = Alignment.Center) {
        Text("Touchpad", color = Color(0xFF9AA0AA))
    }
}
