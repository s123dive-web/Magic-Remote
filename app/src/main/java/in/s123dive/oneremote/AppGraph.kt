package `in`.s123dive.oneremote

import android.content.Context
import `in`.s123dive.oneremote.core.DeviceRegistry
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

    /** Phase 0 ships no device drivers; the Router reports "no driver … yet" explicitly. */
    val registry = DeviceRegistry { null }
    val router = Router(repository::current, registry, log)
}
