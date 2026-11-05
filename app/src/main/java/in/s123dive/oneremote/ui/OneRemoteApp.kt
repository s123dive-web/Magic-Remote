package `in`.s123dive.oneremote.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.Mic
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
import `in`.s123dive.oneremote.ui.setup.FindDevicesScreen
import `in`.s123dive.oneremote.ui.setup.KeyFinderScreen
import `in`.s123dive.oneremote.ui.setup.LgPairScreen
import `in`.s123dive.oneremote.ui.setup.SonyPairScreen
import `in`.s123dive.oneremote.ui.setup.WifiDeviceSection
import `in`.s123dive.oneremote.ui.remote.LgExtras
import `in`.s123dive.oneremote.ui.remote.SonyExtras
import `in`.s123dive.oneremote.ui.theme.OneRemoteTheme
import `in`.s123dive.oneremote.ui.rooms.RoomsScreen
import `in`.s123dive.oneremote.ui.macros.MacrosScreen
import `in`.s123dive.oneremote.ui.settings.BackupScreen
import `in`.s123dive.oneremote.ui.scene.SceneAutomationRow

object Routes {
    const val SCENE = "scene"
    const val SETTINGS = "settings"
    const val DEBUG = "debug"
    const val ROOMS = "rooms"
    const val BACKUP = "backup"
    fun macros(roomId: String?) = "macros?room=${roomId ?: ""}"
    const val ADD = "add"
    fun add(roomId: String?) = "add?room=${roomId ?: ""}"
    fun scan(type: DeviceType, roomId: String?) = "scan/${type.name}?room=${roomId ?: ""}"
    fun expert(type: DeviceType, roomId: String?, instanceId: String?) = "expert/${type.name}?room=${roomId ?: ""}&instance=${instanceId ?: ""}"
    fun remote(instanceId: String) = "remote/$instanceId"
    fun find(type: DeviceType, roomId: String?) = "find/${type.name}?room=${roomId ?: ""}"
    fun pair(type: DeviceType, ip: String, roomId: String?, name: String?, instanceId: String?) =
        "pair/${type.name}?ip=$ip&room=${roomId ?: ""}&name=${java.net.URLEncoder.encode(name ?: "", "UTF-8")}&instance=${instanceId ?: ""}"
    fun device(instanceId: String) = "device/$instanceId"
    fun keyFinder(instanceId: String) = "keyfinder/$instanceId"
}

/** Type-specific extras under the common pad (LG touchpad/text/apps, Sony apps/inputs). */
@Composable
fun deviceExtras(vm: `in`.s123dive.oneremote.ui.remote.RemoteViewModel, actions: `in`.s123dive.oneremote.ui.remote.PadActions) {
    when (vm.instance.value?.type) {
        DeviceType.LG_WEBOS -> LgExtras(vm, actions)
        DeviceType.SONY_BRAVIA -> SonyExtras(vm, actions)
        else -> {}
    }
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
                        onOpenRooms = { nav.navigate(Routes.ROOMS) },
                        onDeviceSettings = { id -> nav.navigate(Routes.device(id)) },
                        extras = ::deviceExtras,
                        macroRow = { roomId -> SceneAutomationRow(roomId, onEditMacros = { nav.navigate(Routes.macros(roomId)) }) },
                        micFab = { _ ->
                            val ctx = LocalContext.current
                            androidx.compose.material3.FloatingActionButton(onClick = { ctx.startActivity(android.content.Intent(ctx, `in`.s123dive.oneremote.voice.VoiceActivity::class.java)) }) {
                                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.Mic, contentDescription = "Voice")
                            }
                        },
                    )
                }
                composable("macros?room={room}") { entry ->
                    MacrosScreen(roomId = entry.arguments?.getString("room").orNullIfBlank(), onBack = { nav.popBackStack() })
                }
                composable(Routes.BACKUP) { BackupScreen(onBack = { nav.popBackStack() }) }
                composable(Routes.ROOMS) {
                    RoomsScreen(onBack = { nav.popBackStack() }, onAddDevice = { roomId -> nav.navigate(Routes.add(roomId)) }, onOpenDevice = { id -> nav.navigate(Routes.device(id)) })
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(onBack = { nav.popBackStack() }, onOpenDebug = { nav.navigate(Routes.DEBUG) }, onOpenBackup = { nav.navigate(Routes.BACKUP) }, onOpenMacros = { nav.navigate(Routes.macros(null)) })
                }
                composable(Routes.DEBUG) { DebugConsoleScreen(onBack = { nav.popBackStack() }) }
                composable("add?room={room}") { entry ->
                    val room = entry.arguments?.getString("room").orNullIfBlank()
                    AddDeviceScreen(roomId = room, onBack = { nav.popBackStack() }, onPick = { type ->
                        if (type == DeviceType.AIRTEL_HD) nav.navigate(Routes.scan(type, room)) else nav.navigate(Routes.find(type, room))
                    })
                }
                composable("find/{type}?room={room}") { entry ->
                    val type = DeviceType.valueOf(entry.arguments?.getString("type") ?: DeviceType.LG_WEBOS.name)
                    val room = entry.arguments?.getString("room").orNullIfBlank()
                    FindDevicesScreen(type = type, onBack = { nav.popBackStack() }, onPick = { ip, name -> nav.navigate(Routes.pair(type, ip, room, name, null)) })
                }
                composable("pair/{type}?ip={ip}&room={room}&name={name}&instance={instance}") { entry ->
                    val type = DeviceType.valueOf(entry.arguments?.getString("type") ?: DeviceType.LG_WEBOS.name)
                    val ip = entry.arguments?.getString("ip") ?: return@composable
                    val room = entry.arguments?.getString("room").orNullIfBlank()
                    val name = entry.arguments?.getString("name").orNullIfBlank()?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                    val instance = entry.arguments?.getString("instance").orNullIfBlank()
                    val done: (String) -> Unit = { id -> nav.navigate(Routes.remote(id)) { popUpTo(Routes.SCENE) } }
                    if (type == DeviceType.LG_WEBOS) LgPairScreen(ip, name, room, instance, onBack = { nav.popBackStack() }, onDone = done)
                    else SonyPairScreen(ip, name, room, instance, onBack = { nav.popBackStack() }, onDone = done)
                }
                composable("scan/{type}?room={room}") { entry ->
                    val type = DeviceType.valueOf(entry.arguments?.getString("type") ?: DeviceType.AIRTEL_HD.name)
                    val room = entry.arguments?.getString("room").orNullIfBlank()
                    CodesetScanScreen(
                        type = type, roomId = room,
                        onBack = { nav.popBackStack() },
                        onDone = { id -> nav.navigate(Routes.remote(id)) { popUpTo(Routes.SCENE) } },
                        onKeyFinder = { id -> nav.navigate(Routes.keyFinder(id)) { popUpTo(Routes.SCENE) } },
                        onExpert = { nav.navigate(Routes.expert(type, room, null)) },
                    )
                }
                composable("keyfinder/{id}") { entry ->
                    val id = entry.arguments?.getString("id") ?: return@composable
                    KeyFinderScreen(instanceId = id, onBack = { nav.popBackStack() }, onDone = { nav.navigate(Routes.remote(id)) { popUpTo(Routes.SCENE) } })
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
                    DeviceRemoteScreen(
                        instanceId = id, onBack = { nav.popBackStack() }, onDeviceSettings = { nav.navigate(Routes.device(id)) },
                        extra = ::deviceExtras,
                    )
                }
                composable("device/{id}") { entry ->
                    val id = entry.arguments?.getString("id") ?: return@composable
                    DeviceSettingsScreen(
                        instanceId = id,
                        onBack = { nav.popBackStack() },
                        onExpert = { type -> nav.navigate(Routes.expert(type, null, id)) },
                        onKeyFinder = { nav.navigate(Routes.keyFinder(id)) },
                        onRemoved = { nav.navigate(Routes.SCENE) { popUpTo(Routes.SCENE) { inclusive = true } } },
                        wifiSection = { vm, instanceId ->
                            WifiDeviceSection(vm, instanceId, onRepair = { type, ip ->
                                val name = vm.state.value.device(instanceId)?.name
                                nav.navigate(Routes.pair(type, ip, null, name, instanceId))
                            })
                        },
                    )
                }
            }
        }
    }
}
