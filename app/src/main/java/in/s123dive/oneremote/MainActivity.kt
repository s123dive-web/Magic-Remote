package `in`.s123dive.oneremote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import `in`.s123dive.oneremote.ui.OneRemoteApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { OneRemoteApp() }
    }

    override fun onResume() {
        super.onResume()
        (application as OneRemoteApplication).graph.capabilities.refresh()
    }
}
