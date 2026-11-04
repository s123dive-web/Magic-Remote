package `in`.s123dive.oneremote.sony

import `in`.s123dive.oneremote.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException

/**
 * HTTP client for one Sony Bravia (PSK auth only). Every call has a 3 s budget and returns a
 * typed outcome so the UI can say exactly what went wrong ("wrong PSK", "IP Control not enabled").
 */
class SonyClient(val host: String, private val psk: String?) {
    sealed interface Outcome<out T> {
        data class Ok<T>(val value: T) : Outcome<T>
        /** 403/401 or JSON error 403: the PSK is wrong (or auth is not set to PSK on the TV). */
        data class WrongPsk(val detail: String) : Outcome<Nothing>
        /** Could not connect: TV off, wrong IP, or IP Control disabled. */
        data class Unreachable(val detail: String) : Outcome<Nothing>
        data class PoweredOff(val detail: String) : Outcome<Nothing>
        data class Unsupported(val detail: String) : Outcome<Nothing>
        data class Error(val detail: String) : Outcome<Nothing>

        val reason: String get() = when (this) {
            is Ok -> "ok"
            is WrongPsk -> "wrong PSK ($detail)"
            is Unreachable -> "IP Control not enabled or TV unreachable ($detail)"
            is PoweredOff -> "TV is off ($detail)"
            is Unsupported -> "not supported by this TV ($detail)"
            is Error -> detail
        }
    }

    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val xmlType = "text/xml; charset=UTF-8".toMediaType()
    @Volatile private var irccPath = SonyApi.IRCC_PATH

    private fun headers(b: Request.Builder): Request.Builder {
        psk?.let { b.header("X-Auth-PSK", it) }
        return b.header("Cache-Control", "no-cache")
    }

    suspend fun rpc(service: String, method: String, params: JsonElement? = null, version: String = "1.0"): Outcome<SonyApi.Reply> = withContext(Dispatchers.IO) {
        val body = SonyApi.rpc(method, params, version).toRequestBody(jsonType)
        val req = headers(Request.Builder().url("http://$host/sony/$service").post(body)).build()
        try {
            Http.plain.newCall(req).execute().use { resp ->
                val text = resp.body?.string() ?: ""
                when (resp.code) {
                    200 -> {
                        val reply = SonyApi.parseReply(text)
                        when {
                            reply.ok -> Outcome.Ok(reply)
                            reply.isAuthError -> Outcome.WrongPsk("JSON error ${reply.errorCode}")
                            reply.isPoweredOff -> Outcome.PoweredOff(reply.errorMessage ?: "${reply.errorCode}")
                            reply.isUnsupported -> Outcome.Unsupported("$method: ${reply.errorMessage}")
                            else -> Outcome.Error("$method error ${reply.errorCode}: ${reply.errorMessage}")
                        }
                    }
                    401, 403 -> Outcome.WrongPsk("HTTP ${resp.code}")
                    404 -> Outcome.Unsupported("HTTP 404 for /sony/$service")
                    else -> Outcome.Error("HTTP ${resp.code}: ${text.take(120)}")
                }
            }
        } catch (e: ConnectException) { Outcome.Unreachable(e.message ?: "connect failed")
        } catch (e: SocketTimeoutException) { Outcome.Unreachable("timeout")
        } catch (e: IOException) { Outcome.Unreachable(e.message ?: e::class.simpleName ?: "io error") }
    }

    /** IRCC-IP: sends one base64 code via SOAP. Falls back to the upper-case path some firmware uses. */
    suspend fun ircc(code: String): Outcome<Unit> = withContext(Dispatchers.IO) {
        val first = irccOnce(code)
        if (first is Outcome.Unsupported && first.detail == NOT_FOUND) {
            irccPath = if (irccPath == "ircc") "IRCC" else "ircc"
            val second = irccOnce(code)
            if (second is Outcome.Unsupported && second.detail == NOT_FOUND) Outcome.Unsupported("IRCC endpoint not found (/sony/ircc and /sony/IRCC)") else second
        } else first
    }

    private fun irccOnce(code: String): Outcome<Unit> {
        val req = headers(Request.Builder().url("http://$host/sony/$irccPath"))
            .header("SOAPACTION", SonyApi.SOAP_ACTION)
            .post(SonyApi.irccSoap(code).toRequestBody(xmlType)).build()
        return try {
            Http.plain.newCall(req).execute().use { resp ->
                when (resp.code) {
                    200 -> Outcome.Ok(Unit)
                    401, 403 -> Outcome.WrongPsk("HTTP ${resp.code}")
                    404 -> Outcome.Unsupported(NOT_FOUND)
                    500 -> Outcome.Error("IRCC rejected the code (HTTP 500 ${resp.body?.string()?.let { Regex("<errorCode>(\\d+)</errorCode>").find(it)?.groupValues?.get(1) } ?: ""})")
                    else -> Outcome.Error("IRCC HTTP ${resp.code}")
                }
            }
        } catch (e: ConnectException) { Outcome.Unreachable(e.message ?: "connect failed")
        } catch (e: SocketTimeoutException) { Outcome.Unreachable("timeout")
        } catch (e: IOException) { Outcome.Unreachable(e.message ?: "io error") }
    }

    /** The setup precheck: proves IP + PSK and returns model/firmware/MAC. */
    suspend fun systemInformation(): Outcome<SonyApi.SystemInfo> = when (val r = rpc(SonyApi.SERVICE_SYSTEM, "getSystemInformation")) {
        is Outcome.Ok -> SonyApi.parseSystemInformation(r.value)?.let { Outcome.Ok(it) } ?: Outcome.Error("getSystemInformation: empty result")
        is Outcome.WrongPsk -> r; is Outcome.Unreachable -> r; is Outcome.PoweredOff -> r; is Outcome.Unsupported -> r; is Outcome.Error -> r
    }

    suspend fun remoteControllerInfo(): Outcome<Map<String, String>> = when (val r = rpc(SonyApi.SERVICE_SYSTEM, "getRemoteControllerInfo")) {
        is Outcome.Ok -> Outcome.Ok(SonyApi.parseRemoteControllerInfo(r.value))
        is Outcome.WrongPsk -> r; is Outcome.Unreachable -> r; is Outcome.PoweredOff -> r; is Outcome.Unsupported -> r; is Outcome.Error -> r
    }

    suspend fun powerStatus(): Outcome<String> = when (val r = rpc(SonyApi.SERVICE_SYSTEM, "getPowerStatus")) {
        is Outcome.Ok -> Outcome.Ok(SonyApi.parsePowerStatus(r.value) ?: "unknown")
        is Outcome.WrongPsk -> r; is Outcome.Unreachable -> r; is Outcome.PoweredOff -> r; is Outcome.Unsupported -> r; is Outcome.Error -> r
    }

    suspend fun setPowerStatus(on: Boolean) = rpc(SonyApi.SERVICE_SYSTEM, "setPowerStatus", SonyApi.obj("status" to on))
    suspend fun volumeInformation(): Outcome<SonyApi.Volume?> = when (val r = rpc(SonyApi.SERVICE_AUDIO, "getVolumeInformation")) {
        is Outcome.Ok -> Outcome.Ok(SonyApi.parseVolumeInformation(r.value))
        is Outcome.WrongPsk -> r; is Outcome.Unreachable -> r; is Outcome.PoweredOff -> r; is Outcome.Unsupported -> r; is Outcome.Error -> r
    }
    suspend fun setAudioVolume(level: String, target: String = "speaker") = rpc(SonyApi.SERVICE_AUDIO, "setAudioVolume", SonyApi.obj("target" to target, "volume" to level))
    suspend fun setAudioMute(mute: Boolean) = rpc(SonyApi.SERVICE_AUDIO, "setAudioMute", SonyApi.obj("status" to mute))
    suspend fun setPlayContent(uri: String) = rpc(SonyApi.SERVICE_AV_CONTENT, "setPlayContent", SonyApi.obj("uri" to uri))
    suspend fun inputs(): Outcome<List<SonyApi.Item>> = when (val r = rpc(SonyApi.SERVICE_AV_CONTENT, "getCurrentExternalInputsStatus")) {
        is Outcome.Ok -> Outcome.Ok(SonyApi.parseInputs(r.value))
        is Outcome.WrongPsk -> r; is Outcome.Unreachable -> r; is Outcome.PoweredOff -> r; is Outcome.Unsupported -> r; is Outcome.Error -> r
    }
    suspend fun apps(): Outcome<List<SonyApi.Item>> = when (val r = rpc(SonyApi.SERVICE_APP_CONTROL, "getApplicationList")) {
        is Outcome.Ok -> Outcome.Ok(SonyApi.parseAppList(r.value))
        is Outcome.WrongPsk -> r; is Outcome.Unreachable -> r; is Outcome.PoweredOff -> r; is Outcome.Unsupported -> r; is Outcome.Error -> r
    }
    suspend fun setActiveApp(uri: String) = rpc(SonyApi.SERVICE_APP_CONTROL, "setActiveApp", SonyApi.obj("uri" to uri))
    suspend fun setTextForm(text: String) = rpc(SonyApi.SERVICE_APP_CONTROL, "setTextForm", kotlinx.serialization.json.JsonPrimitive(text))

    companion object {
        private const val NOT_FOUND = "404"
        /** Subnet-scan probe: is there a Bravia at this IP? (No PSK: a 403 or a JSON reply both mean "yes".) */
        suspend fun probe(ip: String): SonyApi.SystemInfo? {
            val c = SonyClient(ip, null)
            return when (val r = c.systemInformation()) {
                is Outcome.Ok -> r.value
                is Outcome.WrongPsk -> SonyApi.SystemInfo(null, null, null, null, null, null)
                else -> null
            }
        }
    }
}
