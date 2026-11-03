package `in`.s123dive.oneremote.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.fillMaxSize
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import `in`.s123dive.oneremote.AppGraph
import `in`.s123dive.oneremote.OneRemoteApplication
import `in`.s123dive.oneremote.ui.debug.DebugConsoleScreen
import `in`.s123dive.oneremote.ui.scene.SceneScreen
import `in`.s123dive.oneremote.ui.settings.SettingsScreen
import `in`.s123dive.oneremote.ui.theme.OneRemoteTheme

object Routes {
    const val SCENE = "scene"
    const val SETTINGS = "settings"
    const val DEBUG = "debug"
}

@Composable
fun appGraph(): AppGraph = (LocalContext.current.applicationContext as OneRemoteApplication).graph

@Composable
fun OneRemoteApp() {
    OneRemoteTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val nav = rememberNavController()
            NavHost(navController = nav, startDestination = Routes.SCENE) {
                composable(Routes.SCENE) {
                    SceneScreen(onOpenSettings = { nav.navigate(Routes.SETTINGS) })
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(onBack = { nav.popBackStack() }, onOpenDebug = { nav.navigate(Routes.DEBUG) })
                }
                composable(Routes.DEBUG) {
                    DebugConsoleScreen(onBack = { nav.popBackStack() })
                }
            }
        }
    }
}
