package `in`.s123dive.oneremote.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import `in`.s123dive.oneremote.AppGraph
import `in`.s123dive.oneremote.OneRemoteApplication
import `in`.s123dive.oneremote.core.DeviceType
import `in`.s123dive.oneremote.ui.debug.DebugConsoleScreen
import `in`.s123dive.oneremote.ui.remote.DeviceRemoteScreen
import `in`.s123dive.oneremote.ui.scene.SceneScreen
import `in`.s123dive.oneremote.ui.settings.SettingsScreen
import `in`.s123dive.oneremote.ui.setup.AddDeviceScreen
import `in`.s123dive.oneremote.ui.setup.CodesetScanScreen
import `in`.s123dive.oneremote.ui.setup.DeviceSettingsScreen
import `in`.s123dive.oneremote.ui.setup.ExpertCodesScreen
import `in`.s123dive.oneremote.ui.theme.OneRemoteTheme

object Routes {
    const val SCENE = "scene"
    const val SETTINGS = "settings"
    const val DEBUG = "debug"
    const val ADD = "add"
    fun add(roomId: String?) = "add?room=${roomId ?: ""}"
    fun scan(type: DeviceType, roomId: String?) = "scan/${type.name}?room=${roomId ?: ""}"
    fun expert(type: DeviceType, roomId: String?, instanceId: String?) = "expert/${type.name}?room=${roomId ?: ""}&instance=${instanceId ?: ""}"
    fun remote(instanceId: String) = "remote/$instanceId"
    fun device(instanceId: String) = "device/$instanceId"
}

@Composable
fun appGraph(): AppGraph = (LocalContext.current.applicationContext as OneRemoteApplication).graph

private fun String?.orNullIfBlank() = this?.takeIf { it.isNotBlank() }

@Composable
fun OneRemoteApp() {
    OneRemoteTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val nav = rememberNavController()
            NavHost(navController = nav, startDestination = Routes.SCENE) {
                composable(Routes.SCENE) {
                    SceneScreen(
                        onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                        onAddDevice = { roomId -> nav.navigate(Routes.add(roomId)) },
                        onOpenRemote = { id -> nav.navigate(Routes.remote(id)) },
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(onBack = { nav.popBackStack() }, onOpenDebug = { nav.navigate(Routes.DEBUG) })
                }
                composable(Routes.DEBUG) { DebugConsoleScreen(onBack = { nav.popBackStack() }) }
                composable("add?room={room}") { entry ->
                    val room = entry.arguments?.getString("room").orNullIfBlank()
                    AddDeviceScreen(roomId = room, onBack = { nav.popBackStack() }, onPick = { type ->
                        nav.navigate(Routes.scan(type, room))
                    })
                }
                composable("scan/{type}?room={room}") { entry ->
                    val type = DeviceType.valueOf(entry.arguments?.getString("type") ?: DeviceType.AIRTEL_HD.name)
                    val room = entry.arguments?.getString("room").orNullIfBlank()
                    CodesetScanScreen(
                        type = type, roomId = room,
                        onBack = { nav.popBackStack() },
                        onDone = { id -> nav.navigate(Routes.remote(id)) { popUpTo(Routes.SCENE) } },
                        onExpert = { nav.navigate(Routes.expert(type, room, null)) },
                    )
                }
                composable("expert/{type}?room={room}&instance={instance}") { entry ->
                    val type = DeviceType.valueOf(entry.arguments?.getString("type") ?: DeviceType.AIRTEL_HD.name)
                    val room = entry.arguments?.getString("room").orNullIfBlank()
                    val instance = entry.arguments?.getString("instance").orNullIfBlank()
                    ExpertCodesScreen(
                        type = type, roomId = room, instanceId = instance,
                        onBack = { nav.popBackStack() },
                        onDone = { id -> nav.navigate(Routes.remote(id)) { popUpTo(Routes.SCENE) } },
                    )
                }
                composable("remote/{id}") { entry ->
                    val id = entry.arguments?.getString("id") ?: return@composable
                    DeviceRemoteScreen(instanceId = id, onBack = { nav.popBackStack() }, onDeviceSettings = { nav.navigate(Routes.device(id)) })
                }
                composable("device/{id}") { entry ->
                    val id = entry.arguments?.getString("id") ?: return@composable
                    DeviceSettingsScreen(
                        instanceId = id,
                        onBack = { nav.popBackStack() },
                        onExpert = { type -> nav.navigate(Routes.expert(type, null, id)) },
                        onRemoved = { nav.navigate(Routes.SCENE) { popUpTo(Routes.SCENE) { inclusive = true } } },
                    )
                }
            }
        }
    }
}
