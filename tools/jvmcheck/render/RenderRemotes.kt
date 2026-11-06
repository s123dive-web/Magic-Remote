@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import `in`.s123dive.oneremote.ui.remote.PadActions
import `in`.s123dive.oneremote.ui.remote.layouts.AirtelRemote
import `in`.s123dive.oneremote.ui.remote.layouts.AtombergRemote
import `in`.s123dive.oneremote.ui.remote.layouts.FitScale
import `in`.s123dive.oneremote.ui.remote.layouts.LgMagicRemote
import `in`.s123dive.oneremote.ui.remote.layouts.SonyBraviaRemote
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.math.min

/** The FitScale that shipped up to build 14, kept here to show the bug it had. */
@Composable
fun OldFitScale(designWidth: Dp, designHeight: Dp, modifier: Modifier = Modifier, maxScale: Float = 1.35f, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val scale = min(maxWidth / designWidth, maxHeight / designHeight).coerceIn(0.35f, maxScale)
        Box(Modifier.size(designWidth * scale, designHeight * scale)) {
            Box(Modifier.requiredSize(designWidth, designHeight).graphicsLayer { scaleX = scale; scaleY = scale; transformOrigin = TransformOrigin(0f, 0f) }) { content() }
        }
    }
}

val actions = PadActions(supports = { true }, send = {}, onUnsupported = {})

data class Remote(val name: String, val w: Dp, val h: Dp, val body: @Composable () -> Unit)
val remotes = listOf(
    Remote("airtel", 250.dp, 760.dp) { AirtelRemote(actions, tv = actions) },
    Remote("lg", 220.dp, 820.dp) { LgMagicRemote(actions, onMic = {}, onApp = {}) },
    Remote("sony", 230.dp, 720.dp) { SonyBraviaRemote(actions, onApp = {}) },
    Remote("fan", 230.dp, 420.dp) { AtombergRemote(actions) },
)

/** Phone screen in dp: app bar + tabs + device header above the remote, like SceneScreen/DeviceRemotePage. */
@Composable
fun Phone(r: Remote, old: Boolean) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Column(Modifier.fillMaxSize().background(Color(0xFF11131A))) {
            Box(Modifier.fillMaxWidth().height(64.dp).background(Color(0xFF1E2230))) { Text("Home (app bar)", color = Color.White) }
            Box(Modifier.fillMaxWidth().height(48.dp).background(Color(0xFF6A1B1B))) { Text("TABS - must stay visible", color = Color.White) }
            Box(Modifier.fillMaxWidth().height(40.dp).background(Color(0xFF1B3A6A))) { Text("device header row", color = Color.White) }
            if (old) OldFitScale(r.w, r.h, Modifier.weight(1f)) { r.body() } else FitScale(r.w, Modifier.weight(1f)) { r.body() }
        }
    }
}

fun main(args: Array<String>) {
    val out = File(args.getOrElse(0) { "build/render" }).apply { mkdirs() }
    // OnePlus Open cover display ~ 412 x 900 dp, inner display ~ 840 x 900 dp; rendered at density 2.
    val screens = listOf("cover" to (412 to 860), "inner" to (840 to 860))
    for (r in remotes) for ((sname, wh) in screens) for (old in listOf(true, false)) {
        val d = 2f
        val scene = ImageComposeScene((wh.first * d).toInt(), (wh.second * d).toInt(), Density(d)) { Phone(r, old) }
        val img = scene.render()
        val f = File(out, "${r.name}-$sname-${if (old) "old" else "new"}.png")
        f.writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
        println("wrote $f")
    }
}
