package `in`.s123dive.oneremote

import android.content.Context
import `in`.s123dive.oneremote.devices.DeviceConfig
import `in`.s123dive.oneremote.devices.DeviceFactory
import `in`.s123dive.oneremote.devices.WifiDrivers
import `in`.s123dive.oneremote.net.Discovery
import `in`.s123dive.oneremote.automation.MacroRunner
import `in`.s123dive.oneremote.automation.Macros
import `in`.s123dive.oneremote.automation.SleepTimer
import `in`.s123dive.oneremote.system.RemoteWidget
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import `in`.s123dive.oneremote.devices.WifiTransportFactories
import `in`.s123dive.oneremote.core.Router
import `in`.s123dive.oneremote.core.SendLog
import `in`.s123dive.oneremote.data.AppRepository
import `in`.s123dive.oneremote.data.AppStateStore
import `in`.s123dive.oneremote.data.SettingsStore
import `in`.s123dive.oneremote.ir.AndroidIrPort
import `in`.s123dive.oneremote.ir.IrPort
import `in`.s123dive.oneremote.ir.IrTransmitter
import `in`.s123dive.oneremote.system.CapabilityDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Hand-wired object graph (no DI framework: one module, a dozen singletons).
 * Built once in [OneRemoteApplication.onCreate]; everything else reads from here.
 */
class AppGraph(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val log = SendLog()
    val repository = AppRepository(AppStateStore.create(context, log), appScope)
    val settings = SettingsStore(context)
    val irPort: IrPort = AndroidIrPort(context)
    val ir = IrTransmitter(irPort)
    val capabilities = CapabilityDetector(context, irPort.capability)

    /** Wi-Fi drivers register per device type; IR is built from the instance's codeset. */
    val wifiFactories = WifiTransportFactories()
    val registry = DeviceFactory(ir, wifiFactories)
    val router = Router(repository::current, registry, log)
    val discovery = Discovery(context)
    val macros = MacroRunner(router)
    val sleepTimer = SleepTimer(context)

    /** Snapshot of the settings for callers that cannot suspend (receivers, tiles, widgets). */
    val settingsSnapshot: StateFlow<SettingsStore.Settings> = settings.settings.stateIn(appScope, SharingStarted.Eagerly, SettingsStore.Settings())
    fun lastRoomId(): String? = settingsSnapshot.value.lastRoomId ?: repository.current().rooms.firstOrNull()?.id

    init {
        WifiDrivers(
            scope = appScope,
            localIpv4 = { capabilities.state.value.ipv4 },
            persist = { id, transform ->
                repository.update { s -> s.copy(devices = s.devices.map { d -> if (d.id == id) d.copy(config = transform(DeviceConfig(d.config)).json) else d }) }
            },
        ).registerInto(wifiFactories)
        // Built-in macros follow the rooms; widgets follow rooms + favourites.
        appScope.launch {
            repository.state.collect { state ->
                val withBuiltIns = Macros.ensureBuiltIns(state)
                if (withBuiltIns !== state) repository.update { Macros.ensureBuiltIns(it) }
                runCatching { RemoteWidget.refresh(context) }
            }
        }
    }
}
