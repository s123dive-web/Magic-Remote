package `in`.s123dive.oneremote.ui.bubble

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import `in`.s123dive.oneremote.OneRemoteApplication
import `in`.s123dive.oneremote.ui.theme.OneRemoteTheme

/** Content of the Android 11+ bubble (resizeable, embeddable). */
class BubbleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = (application as OneRemoteApplication).graph
        setContent { OneRemoteTheme { Surface(Modifier.fillMaxSize()) { MiniRemote(graph) } } }
    }
}
