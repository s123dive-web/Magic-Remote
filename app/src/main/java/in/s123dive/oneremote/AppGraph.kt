package `in`.s123dive.oneremote

import android.content.Context
import `in`.s123dive.oneremote.devices.DeviceFactory
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
}
