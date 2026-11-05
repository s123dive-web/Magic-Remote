package `in`.s123dive.oneremote.sony

import `in`.s123dive.oneremote.core.Capability
import `in`.s123dive.oneremote.core.Command
import `in`.s123dive.oneremote.core.SendResult
import `in`.s123dive.oneremote.core.Transport
import `in`.s123dive.oneremote.core.TransportKind
import `in`.s123dive.oneremote.core.capability
import `in`.s123dive.oneremote.net.WakeOnLan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Sony Bravia over the LAN: ScalarWebAPI for power / volume / inputs / apps / text, IRCC for
 * every other key using the code map fetched from the TV at setup (never hard-coded).
 */
class SonyTransport(
    val client: SonyClient,
    private var irccCodes: Map<String, String>,
    private val textInputSupported: Boolean?,
    private val mac: () -> String?,
    private val localIpv4: () -> String?,
    private val onIrccCodes: (Map<String, String>) -> Unit = {},
) : Transport {
    override val kind = TransportKind.WIFI

    override val capabilities: Set<Capability>
        get() = buildSet {
            addAll(listOf(Capability.POWER, Capability.VOLUME, Capability.VOLUME_ABSOLUTE, Capability.INPUT, Capability.APP_LAUNCH))
            if (irccCodes.isEmpty() || irccName("CH_UP") != null) add(Capability.CHANNEL)
            if (irccCodes.isEmpty() || irccName("DIGIT:1") != null) add(Capability.DIGITS)
            if (irccCodes.isEmpty() || irccName("OK") != null) add(Capability.NAVIGATION)
            if (irccCodes.isEmpty() || irccName("BACK") != null) add(Capability.MENU)
            if (irccCodes.isEmpty() || irccName("PLAY") != null) add(Capability.PLAYBACK)
            if (irccCodes.isEmpty() || irccName("RED") != null) add(Capability.COLOUR_KEYS)
            if (textInputSupported == true) add(Capability.TEXT_INPUT)
            if (mac() != null) add(Capability.WAKE_ON_LAN)
        }

    override fun supports(cmd: Command): Boolean = when (cmd) {
        is Command.Text -> textInputSupported == true
        Command.Guide, Command.Info, Command.Menu, Command.Exit, Command.Home, Command.Back -> irccCodes.isEmpty() || irccName(cmd.key) != null
        else -> cmd.capability in capabilities
    }

    private fun irccName(key: String): String? = SonyApi.IRCC_NAMES[key]?.firstOrNull { irccCodes.containsKey(it) }

    override fun describe(cmd: Command): String = when (cmd) {
        Command.Power -> "system/setPowerStatus toggle"
        Command.PowerOn -> "WoL ${mac() ?: "(no MAC)"} + setPowerStatus true"
        Command.PowerOff -> "system/setPowerStatus false"
        Command.VolUp -> "audio/setAudioVolume +1"
        Command.VolDown -> "audio/setAudioVolume -1"
        Command.Mute -> "audio/setAudioMute toggle"
        is Command.Channel -> "IRCC digits ${cmd.n} + Confirm"
        is Command.InputSelect -> "avContent/setPlayContent ${cmd.id}"
        is Command.LaunchApp -> "appControl/setActiveApp ${cmd.id}"
        is Command.Text -> "appControl/setTextForm (${cmd.s.length} chars)"
        else -> irccName(cmd.key)?.let { "IRCC $it" } ?: "IRCC ${SonyApi.IRCC_NAMES[cmd.key]?.firstOrNull() ?: cmd.key} (not in TV's list)"
    }

    @Volatile private var downUntil = 0L
    private val isDown get() = System.currentTimeMillis() < downUntil

    private fun <T> breaker(outcome: SonyClient.Outcome<T>): SonyClient.Outcome<T> {
        downUntil = if (outcome is SonyClient.Outcome.Unreachable) System.currentTimeMillis() + 15_000 else 0L
        return outcome
    }

    override suspend fun send(cmd: Command): SendResult {
        val payload = describe(cmd)
        if (isDown && cmd != Command.PowerOn && cmd != Command.Power) {
            return SendResult.Failed(payload, "TV not reachable over Wi-Fi (retry in ${(downUntil - System.currentTimeMillis()) / 1000} s)")
        }
        return when (cmd) {
            Command.PowerOn -> powerOn(payload)
            Command.PowerOff -> map(client.setPowerStatus(false), payload)
            Command.Power -> when (val s = client.powerStatus()) {
                is SonyClient.Outcome.Ok -> if (s.value == "active") map(client.setPowerStatus(false), payload) else map(client.setPowerStatus(true), payload)
                is SonyClient.Outcome.Unreachable -> powerOn(payload)
                else -> SendResult.Failed(payload, s.reason)
            }
            Command.VolUp -> map(client.setAudioVolume("+1"), payload)
            Command.VolDown -> map(client.setAudioVolume("-1"), payload)
            Command.Mute -> when (val v = client.volumeInformation()) {
                is SonyClient.Outcome.Ok -> map(client.setAudioMute(!(v.value?.mute ?: false)), payload)
                else -> ircc("MUTE", payload) // older firmware: fall back to the IRCC key
            }
            is Command.Channel -> channel(cmd.n, payload)
            is Command.InputSelect -> map(client.setPlayContent(cmd.id), payload)
            is Command.LaunchApp -> map(client.setActiveApp(cmd.id), payload)
            is Command.Text -> if (textInputSupported == true) map(client.setTextForm(cmd.s), payload) else SendResult.Unsupported("this TV does not support setTextForm")
            else -> ircc(cmd.key, payload)
        }
    }

    private suspend fun ensureCodes(): String? {
        if (irccCodes.isNotEmpty()) return null
        return when (val r = client.remoteControllerInfo()) {
            is SonyClient.Outcome.Ok -> { irccCodes = r.value; onIrccCodes(r.value); null }
            else -> r.reason
        }
    }

    private suspend fun ircc(key: String, payload: String): SendResult {
        ensureCodes()?.let { return SendResult.Failed(payload, "could not fetch IRCC code list: $it") }
        val name = irccName(key) ?: return SendResult.Unsupported("TV's IRCC list has no key for $key")
        val code = irccCodes[name] ?: return SendResult.Unsupported("no IRCC code for $name")
        return map(client.ircc(code), "IRCC $name")
    }

    private suspend fun channel(n: Int, payload: String): SendResult {
        for (d in n.toString()) {
            val r = ircc("DIGIT:$d", payload)
            if (r !is SendResult.Ok) return r
            delay(200)
        }
        val ok = ircc("OK", payload)
        return if (ok is SendResult.Ok) SendResult.Ok(payload) else ok
    }

    private suspend fun powerOn(payload: String): SendResult {
        val m = mac()
        if (m != null) withContext(Dispatchers.IO) {
            runCatching { WakeOnLan.send(m, extraTargets = listOfNotNull(localIpv4()?.let { WakeOnLan.subnetBroadcast(it) })) }
        }
        val r = client.setPowerStatus(true)
        return when {
            r is SonyClient.Outcome.Ok -> SendResult.Ok(payload)
            m != null && r is SonyClient.Outcome.Unreachable -> SendResult.Ok("$payload (WoL sent; TV not answering yet)")
            m == null && r is SonyClient.Outcome.Unreachable -> SendResult.Failed(payload, "TV unreachable and no MAC for Wake-on-LAN")
            else -> SendResult.Failed(payload, r.reason)
        }
    }

    private fun <T> map(outcome0: SonyClient.Outcome<T>, payload: String): SendResult = when (val outcome = breaker(outcome0)) {
        is SonyClient.Outcome.Ok -> SendResult.Ok(payload)
        is SonyClient.Outcome.Unsupported -> SendResult.Unsupported(outcome.reason)
        else -> SendResult.Failed(payload, outcome.reason)
    }

    override suspend fun isReachable(): Boolean = !isDown && breaker(client.powerStatus()) is SonyClient.Outcome.Ok

    suspend fun inputs(): List<SonyApi.Item> = (client.inputs() as? SonyClient.Outcome.Ok)?.value ?: emptyList()
    suspend fun apps(): List<SonyApi.Item> = (client.apps() as? SonyClient.Outcome.Ok)?.value ?: emptyList()
}
