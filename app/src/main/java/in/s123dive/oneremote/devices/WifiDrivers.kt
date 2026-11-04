package `in`.s123dive.oneremote.devices

import `in`.s123dive.oneremote.core.DeviceInstance
import `in`.s123dive.oneremote.core.DeviceType
import `in`.s123dive.oneremote.core.Transport
import `in`.s123dive.oneremote.lg.LgClient
import `in`.s123dive.oneremote.lg.LgTransport
import `in`.s123dive.oneremote.sony.SonyClient
import `in`.s123dive.oneremote.sony.SonyTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Builds LG / Sony Wi-Fi transports from an instance's config. Facts the TV reports during a
 * session (client-key, IRCC map, MAC, versions) are written back through [persist] so the next
 * launch does not re-prompt or re-fetch.
 */
class WifiDrivers(
    private val scope: CoroutineScope,
    private val localIpv4: () -> String?,
    private val persist: suspend (instanceId: String, transform: (DeviceConfig) -> DeviceConfig) -> Unit,
) {
    fun registerInto(factories: WifiTransportFactories) {
        factories.register(DeviceType.LG_WEBOS) { instance, config -> lg(instance, config) }
        factories.register(DeviceType.SONY_BRAVIA) { instance, config -> sony(instance, config) }
    }

    private fun lg(instance: DeviceInstance, config: DeviceConfig): Transport? {
        val ip = config.ip ?: return null
        val client = LgClient(ip, config.clientKey, config.secure, onPaired = { key, secure, info ->
            scope.launch {
                persist(instance.id) { c ->
                    var n = c.with(DeviceConfig.CLIENT_KEY, key).with(DeviceConfig.SECURE, secure)
                    info.modelName?.let { n = n.with(DeviceConfig.MODEL, it) }
                    info.webosVersion?.let { n = n.with(DeviceConfig.WEBOS_VERSION, it) }
                    info.firmware?.let { n = n.with(DeviceConfig.FIRMWARE, it) }
                    if (c.mac == null) info.macAddress?.let { n = n.with(DeviceConfig.MAC, it) }
                    n
                }
            }
        })
        return LgTransport(client, mac = { instance.mac ?: config.mac ?: client.info.macAddress }, localIpv4 = localIpv4)
    }

    private fun sony(instance: DeviceInstance, config: DeviceConfig): Transport? {
        val ip = config.ip ?: return null
        val client = SonyClient(ip, config.psk)
        return SonyTransport(
            client, config.irccCodes, config.textInputSupported,
            mac = { instance.mac ?: config.mac }, localIpv4 = localIpv4,
            onIrccCodes = { codes -> scope.launch { persist(instance.id) { it.withIrccCodes(codes) } } },
        )
    }
}
