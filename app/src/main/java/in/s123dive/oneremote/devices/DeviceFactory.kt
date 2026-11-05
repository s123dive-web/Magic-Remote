package `in`.s123dive.oneremote.devices

import `in`.s123dive.oneremote.core.Device
import `in`.s123dive.oneremote.core.DeviceInstance
import `in`.s123dive.oneremote.core.DeviceRegistry
import `in`.s123dive.oneremote.core.Transport
import `in`.s123dive.oneremote.core.TransportKind
import `in`.s123dive.oneremote.ir.IrTransmitter
import `in`.s123dive.oneremote.ir.IrTransport
import `in`.s123dive.oneremote.ir.codesets.Codesets

/** A device assembled from its instance's transports. */
class GenericDevice(override val instance: DeviceInstance, override val transports: List<Transport>) : Device

/** Builds the Wi-Fi transport for a type; LG/Sony drivers register here in their phases. */
fun interface WifiTransportFactory {
    fun create(instance: DeviceInstance, config: DeviceConfig): Transport?
}

/**
 * Turns a [DeviceInstance] into a live [Device]. Drivers are cached per instance and rebuilt
 * when the instance (config) changes, so a Wi-Fi client keeps its connection across sends.
 */
class DeviceFactory(
    private val ir: IrTransmitter,
    private val wifi: WifiTransportFactory = WifiTransportFactory { _, _ -> null },
) : DeviceRegistry {
    private val cache = HashMap<String, Pair<String, Device>>()

    private fun signature(instance: DeviceInstance) = "${instance.type}|${instance.transports}|${DeviceConfig(instance.config).driverSignature()}"

    @Synchronized
    override fun get(instance: DeviceInstance): Device? {
        cache[instance.id]?.let { (sig, device) -> if (sig == signature(instance)) return device }
        cache.remove(instance.id)?.second?.transports?.forEach { (it as? AutoCloseable)?.close() }
        val device = build(instance) ?: return null
        cache[instance.id] = signature(instance) to device
        return device
    }

    @Synchronized
    fun evict(instanceId: String) {
        cache.remove(instanceId)?.second?.transports?.forEach { (it as? AutoCloseable)?.close() }
    }

    private fun build(instance: DeviceInstance): Device? {
        val config = DeviceConfig(instance.config)
        val transports = ArrayList<Transport>()
        for (kind in instance.transports) {
            when (kind) {
                TransportKind.WIFI -> wifi.create(instance, config)?.let { transports += it }
                TransportKind.IR -> irTransport(instance, config)?.let { transports += it }
            }
        }
        return GenericDevice(instance, transports)
    }

    fun irTransport(instance: DeviceInstance, config: DeviceConfig): IrTransport? {
        if (!ir.capability.hasIr) return null
        val builtIn = config.codesetId?.let { Codesets.byId(it) }
        val codeset = config.effectiveCodeset(builtIn, instance.name, instance.type) ?: return null
        if (!ir.capability.supports(codeset.carrierHz)) return null
        return IrTransport(codeset, ir)
    }
}

/** Per-type registry of Wi-Fi transport factories (LG and Sony drivers add themselves). */
class WifiTransportFactories : WifiTransportFactory {
    private val byType = HashMap<`in`.s123dive.oneremote.core.DeviceType, WifiTransportFactory>()
    fun register(type: `in`.s123dive.oneremote.core.DeviceType, factory: WifiTransportFactory) { byType[type] = factory }
    override fun create(instance: DeviceInstance, config: DeviceConfig): Transport? = byType[instance.type]?.create(instance, config)
}
