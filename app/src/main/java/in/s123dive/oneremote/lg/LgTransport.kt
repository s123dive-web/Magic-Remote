package `in`.s123dive.oneremote.lg

import `in`.s123dive.oneremote.core.Capability
import `in`.s123dive.oneremote.core.Command
import `in`.s123dive.oneremote.core.SendResult
import `in`.s123dive.oneremote.core.Transport
import `in`.s123dive.oneremote.core.TransportKind
import `in`.s123dive.oneremote.core.capability
import `in`.s123dive.oneremote.net.Http
import `in`.s123dive.oneremote.net.WakeOnLan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Maps [Command]s onto SSAP requests and pointer-socket buttons. */
class LgTransport(
    val client: LgClient,
    private val mac: () -> String?,
    private val localIpv4: () -> String?,
) : Transport, AutoCloseable {
    override val kind = TransportKind.WIFI

    override val capabilities: Set<Capability>
        get() = setOf(
            Capability.POWER, Capability.VOLUME, Capability.CHANNEL, Capability.DIGITS, Capability.NAVIGATION,
            Capability.MENU, Capability.PLAYBACK, Capability.COLOUR_KEYS, Capability.INPUT, Capability.TEXT_INPUT,
            Capability.POINTER, Capability.APP_LAUNCH, Capability.VOLUME_ABSOLUTE,
        ) + (if (mac() != null) setOf(Capability.WAKE_ON_LAN) else emptySet())

    override fun supports(cmd: Command): Boolean = when (cmd) {
        Command.Input -> false // no SSAP call opens the input picker; IR 0x0B does, so let the Router fall back
        else -> cmd.capability in capabilities
    }

    /** Pointer-socket button names (pywebostv InputControl). */
    private fun button(cmd: Command): String? = when (cmd) {
        Command.Ok -> "ENTER"; Command.Back -> "BACK"; Command.Home -> "HOME"; Command.Menu -> "MENU"; Command.Exit -> "EXIT"
        Command.Info -> "INFO"; Command.Guide -> "GUIDE"
        Command.Up -> "UP"; Command.Down -> "DOWN"; Command.Left -> "LEFT"; Command.Right -> "RIGHT"
        Command.Mute -> "MUTE"; Command.VolUp -> "VOLUMEUP"; Command.VolDown -> "VOLUMEDOWN"
        Command.ChUp -> "CHANNELUP"; Command.ChDown -> "CHANNELDOWN"
        Command.Red -> "RED"; Command.Green -> "GREEN"; Command.Yellow -> "YELLOW"; Command.Blue -> "BLUE"
        Command.Play -> "PLAY"; Command.Pause -> "PAUSE"; Command.Stop -> "STOP"; Command.Rew -> "REWIND"; Command.Fwd -> "FASTFORWARD"
        is Command.Digit -> cmd.n.toString()
        else -> null
    }

    override fun describe(cmd: Command): String = when (cmd) {
        Command.Power -> "ssap://system/turnOff | WoL"
        Command.PowerOff -> "ssap://system/turnOff"
        Command.PowerOn -> "WoL ${mac() ?: "(no MAC)"}"
        Command.VolUp -> "ssap://audio/volumeUp"
        Command.VolDown -> "ssap://audio/volumeDown"
        Command.ChUp -> "ssap://tv/channelUp"
        Command.ChDown -> "ssap://tv/channelDown"
        is Command.Channel -> "ssap://tv/openChannel ${cmd.n}"
        is Command.InputSelect -> "ssap://tv/switchInput ${cmd.id}"
        is Command.LaunchApp -> "ssap://system.launcher/launch ${cmd.id}"
        is Command.Text -> "ssap://com.webos.service.ime/insertText (${cmd.s.length} chars)"
        is Command.PointerMove -> "pointer move ${cmd.dx},${cmd.dy}"
        Command.PointerClick -> "pointer click"
        is Command.Scroll -> "pointer scroll ${cmd.dx},${cmd.dy}"
        else -> button(cmd)?.let { "pointer button $it" } ?: "unsupported ${cmd.key}"
    }

    override suspend fun send(cmd: Command): SendResult {
        val payload = describe(cmd)
        if (!client.isConnected && client.isDown && cmd != Command.PowerOn) {
            return SendResult.Failed(payload, "TV not reachable over Wi-Fi (retry in ${client.secondsUntilRetry()} s)")
        }
        return when (cmd) {
            Command.PowerOn -> wake(payload)
            Command.PowerOff -> rpc("ssap://system/turnOff", null, payload)
            Command.Power -> if (isReachable()) rpc("ssap://system/turnOff", null, payload) else wake(payload)
            Command.VolUp -> rpc("ssap://audio/volumeUp", null, payload)
            Command.VolDown -> rpc("ssap://audio/volumeDown", null, payload)
            Command.ChUp -> rpc("ssap://tv/channelUp", null, payload)
            Command.ChDown -> rpc("ssap://tv/channelDown", null, payload)
            is Command.Channel -> channel(cmd.n, payload)
            is Command.InputSelect -> rpc("ssap://tv/switchInput", SsapMessages.obj("inputId" to cmd.id), payload)
            is Command.LaunchApp -> rpc("ssap://system.launcher/launch", SsapMessages.obj("id" to cmd.id), payload)
            is Command.Text -> text(cmd.s, payload)
            is Command.PointerMove -> pointer(SsapMessages.pointerMove(cmd.dx, cmd.dy), payload)
            Command.PointerClick -> pointer(SsapMessages.pointerClick(), payload)
            is Command.Scroll -> pointer(SsapMessages.pointerScroll(cmd.dx, cmd.dy), payload)
            else -> button(cmd)?.let { pointer(SsapMessages.pointerButton(it), payload) } ?: SendResult.Unsupported("LG Wi-Fi has no action for ${cmd.key}")
        }
    }

    private suspend fun rpc(uri: String, body: JsonObject?, payload: String): SendResult = when (val r = client.request(uri, body)) {
        is LgClient.Result.Ok -> SendResult.Ok(payload)
        is LgClient.Result.Err -> SendResult.Failed(payload, r.reason)
    }

    private suspend fun pointer(message: String, payload: String): SendResult = when (val r = client.pointer(message)) {
        is LgClient.Result.Ok -> SendResult.Ok(payload)
        is LgClient.Result.Err -> SendResult.Failed(payload, r.reason)
    }

    private suspend fun channel(n: Int, payload: String): SendResult {
        val direct = client.request("ssap://tv/openChannel", SsapMessages.obj("channelNumber" to n.toString()))
        if (direct is LgClient.Result.Ok) return SendResult.Ok(payload)
        // Not on a tuner channel list: type the digits like the physical remote.
        for (d in n.toString()) { val r = client.pointer(SsapMessages.pointerButton(d.toString())); if (r is LgClient.Result.Err) return SendResult.Failed(payload, r.reason) }
        return when (val r = client.pointer(SsapMessages.pointerButton("ENTER"))) {
            is LgClient.Result.Ok -> SendResult.Ok("$payload (digits fallback)")
            is LgClient.Result.Err -> SendResult.Failed(payload, r.reason)
        }
    }

    private suspend fun text(s: String, payload: String): SendResult {
        val enter = s.endsWith("\n")
        val body = s.trimEnd('\n')
        if (body.isNotEmpty()) {
            val r = client.request("ssap://com.webos.service.ime/insertText", SsapMessages.obj("text" to body, "replace" to 0))
            if (r is LgClient.Result.Err) return SendResult.Failed(payload, r.reason)
        }
        if (enter) {
            val r = client.request("ssap://com.webos.service.ime/sendEnterKey")
            if (r is LgClient.Result.Err) return SendResult.Failed(payload, r.reason)
        }
        return SendResult.Ok(payload)
    }

    private suspend fun wake(payload: String): SendResult {
        val m = mac() ?: return SendResult.Failed(payload, "no MAC address for Wake-on-LAN (set it in device settings)")
        return withContext(Dispatchers.IO) {
            try {
                WakeOnLan.send(m, extraTargets = listOfNotNull(localIpv4()?.let { WakeOnLan.subnetBroadcast(it) }))
                SendResult.Ok(payload)
            } catch (e: Exception) { SendResult.Failed(payload, "WoL failed: ${e.message}") }
        }
    }

    override suspend fun isReachable(): Boolean {
        if (client.isConnected) return true
        if (client.isDown) return false
        return withTimeoutOrNull(Http.WS_CONNECT_SEC * 1000 + 3000) { client.connect() is LgClient.Result.Ok } ?: false
    }

    // ---- data for the UI (inputs / apps) ----
    data class Item(val id: String, val title: String, val icon: String? = null)

    suspend fun inputs(): List<Item> = (client.request("ssap://tv/getExternalInputList") as? LgClient.Result.Ok)?.value
        ?.get("devices")?.let { it as? JsonArray }?.mapNotNull { e ->
            val o = e.jsonObject
            val id = o["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            Item(id, o["label"]?.jsonPrimitive?.content ?: id, o["icon"]?.jsonPrimitive?.content)
        } ?: emptyList()

    suspend fun apps(): List<Item> = (client.request("ssap://com.webos.applicationManager/listLaunchPoints") as? LgClient.Result.Ok)?.value
        ?.get("launchPoints")?.let { it as? JsonArray }?.mapNotNull { e ->
            val o = e.jsonObject
            val id = o["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            Item(id, o["title"]?.jsonPrimitive?.content ?: id, o["icon"]?.jsonPrimitive?.content)
        } ?: emptyList()

    override fun close() = client.close()

}
