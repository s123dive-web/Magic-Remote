package `in`.s123dive.oneremote.sony

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Pure builders and parsers for Sony's ScalarWebAPI (JSON-RPC) and IRCC-IP (SOAP). Mirrors pybravia. */
object SonyApi {
    val json = Json { ignoreUnknownKeys = true }

    const val SERVICE_SYSTEM = "system"
    const val SERVICE_AUDIO = "audio"
    const val SERVICE_AV_CONTENT = "avContent"
    const val SERVICE_APP_CONTROL = "appControl"
    const val IRCC_PATH = "ircc"
    const val SOAP_ACTION = "\"urn:schemas-sony-com:service:IRCC:1#X_SendIRCC\""

    fun rpc(method: String, params: JsonElement? = null, version: String = "1.0", id: Int = 1): String =
        json.encodeToString(JsonObject.serializer(), buildJsonObject {
            put("method", JsonPrimitive(method))
            put("id", JsonPrimitive(id))
            put("params", when (params) { null -> JsonArray(emptyList()); is JsonArray -> params; else -> buildJsonArray { add(params) } })
            put("version", JsonPrimitive(version))
        })

    fun irccSoap(code: String): String =
        "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
            "<s:Body><u:X_SendIRCC xmlns:u=\"urn:schemas-sony-com:service:IRCC:1\"><IRCCCode>$code</IRCCCode></u:X_SendIRCC></s:Body></s:Envelope>"

    /** A JSON-RPC reply: either a result array or an error [code, message]. */
    data class Reply(val result: JsonArray?, val errorCode: Int?, val errorMessage: String?) {
        val ok: Boolean get() = errorCode == null && result != null
        val isAuthError: Boolean get() = errorCode == 403 || errorCode == 401
        val isPoweredOff: Boolean get() = errorCode == 40005 || errorMessage?.contains("not power-on", true) == true || errorMessage?.contains("Display Is Turned off", true) == true
        val isUnsupported: Boolean get() = errorCode == 12 || errorCode == 15 || errorCode == 501 || errorMessage?.contains("No Such Method", true) == true
        val first: JsonElement? get() = result?.firstOrNull()
    }

    fun parseReply(text: String): Reply = runCatching {
        val o = json.parseToJsonElement(text).jsonObject
        val err = o["error"] as? JsonArray
        if (err != null) Reply(null, err.getOrNull(0)?.jsonPrimitive?.intOrNull, err.getOrNull(1)?.jsonPrimitive?.content)
        else Reply(o["result"] as? JsonArray ?: JsonArray(emptyList()), null, null)
    }.getOrElse { Reply(null, -1, "unparseable reply: ${text.take(80)}") }

    data class SystemInfo(val model: String?, val generation: String?, val name: String?, val mac: String?, val serial: String?, val area: String?)

    fun parseSystemInformation(reply: Reply): SystemInfo? {
        val o = reply.first as? JsonObject ?: return null
        fun s(k: String) = o[k]?.jsonPrimitive?.content
        return SystemInfo(model = s("model"), generation = s("generation"), name = s("name"), mac = s("macAddr")?.uppercase(), serial = s("serial"), area = s("area"))
    }

    /** `getRemoteControllerInfo` -> [ {bundled, type}, [ {name, value}, ... ] ] */
    fun parseRemoteControllerInfo(reply: Reply): Map<String, String> {
        val list = reply.result?.getOrNull(1) as? JsonArray ?: return emptyMap()
        return list.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val n = o["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val v = o["value"]?.jsonPrimitive?.content ?: return@mapNotNull null
            n to v
        }.toMap()
    }

    data class Volume(val target: String, val volume: Int?, val mute: Boolean?, val min: Int?, val max: Int?)

    fun parseVolumeInformation(reply: Reply, preferred: String = "speaker"): Volume? {
        val list = reply.first as? JsonArray ?: return null
        val all = list.mapNotNull { e -> (e as? JsonObject)?.let { o -> Volume(o["target"]?.jsonPrimitive?.content ?: "", o["volume"]?.jsonPrimitive?.intOrNull, o["mute"]?.jsonPrimitive?.booleanOrNull, o["minVolume"]?.jsonPrimitive?.intOrNull, o["maxVolume"]?.jsonPrimitive?.intOrNull) } }
        return all.firstOrNull { it.target == preferred } ?: all.firstOrNull()
    }

    fun parsePowerStatus(reply: Reply): String? = (reply.first as? JsonObject)?.get("status")?.jsonPrimitive?.content

    data class Item(val uri: String, val title: String, val icon: String? = null, val label: String? = null)

    /** `getApplicationList` -> [[ {title, uri, icon}, ... ]] */
    fun parseAppList(reply: Reply): List<Item> = (reply.first as? JsonArray)?.mapNotNull { e ->
        val o = e as? JsonObject ?: return@mapNotNull null
        Item(o["uri"]?.jsonPrimitive?.content ?: return@mapNotNull null, o["title"]?.jsonPrimitive?.content ?: "", o["icon"]?.jsonPrimitive?.content)
    } ?: emptyList()

    /** `getCurrentExternalInputsStatus` -> [[ {uri, title, label, connection, icon}, ... ]] */
    fun parseInputs(reply: Reply): List<Item> = (reply.first as? JsonArray)?.mapNotNull { e ->
        val o = e as? JsonObject ?: return@mapNotNull null
        val uri = o["uri"]?.jsonPrimitive?.content ?: return@mapNotNull null
        val title = o["title"]?.jsonPrimitive?.content ?: uri
        val label = o["label"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        Item(uri, if (label != null) "$label ($title)" else title, o["icon"]?.jsonPrimitive?.content, label)
    } ?: emptyList()

    fun obj(vararg pairs: Pair<String, Any?>): JsonObject = buildJsonObject {
        for ((k, v) in pairs) when (v) {
            null -> {}
            is String -> put(k, JsonPrimitive(v))
            is Int -> put(k, JsonPrimitive(v))
            is Boolean -> put(k, JsonPrimitive(v))
            is JsonElement -> put(k, v)
            else -> put(k, JsonPrimitive(v.toString()))
        }
    }

    /** IRCC key name candidates per command, in preference order (names differ between firmware years). */
    val IRCC_NAMES: Map<String, List<String>> = mapOf(
        "POWER" to listOf("Power", "TvPower", "PowerOff"),
        "POWER_ON" to listOf("WakeUp", "PowerOn", "Power"),
        "POWER_OFF" to listOf("PowerOff", "Power"),
        "VOL_UP" to listOf("VolumeUp"), "VOL_DOWN" to listOf("VolumeDown"), "MUTE" to listOf("Mute"),
        "CH_UP" to listOf("ChannelUp"), "CH_DOWN" to listOf("ChannelDown"),
        "OK" to listOf("Confirm", "Enter", "DpadCenter"), "UP" to listOf("Up"), "DOWN" to listOf("Down"), "LEFT" to listOf("Left"), "RIGHT" to listOf("Right"),
        "BACK" to listOf("Return", "Back"), "HOME" to listOf("Home"), "MENU" to listOf("Options", "ActionMenu", "Menu"),
        "GUIDE" to listOf("Guide", "EPG", "GGuide"), "INFO" to listOf("Display", "Info"), "EXIT" to listOf("Exit"),
        "INPUT" to listOf("Input"),
        "LIST" to listOf("ChannelList", "List"), "FAV" to listOf("Favorites", "Fav"), "PREV" to listOf("Jump", "PrevCh", "Prev"),
        "SUBTITLE" to listOf("SubTitle", "ClosedCaption"), "AUDIO" to listOf("Audio", "SAP"), "SLEEP" to listOf("Sleep"), "HELP" to listOf("Help"),
        "RECORD" to listOf("Rec", "Record"),
        "PLAY" to listOf("Play"), "PAUSE" to listOf("Pause"), "STOP" to listOf("Stop"), "REW" to listOf("Rewind"), "FWD" to listOf("Forward", "FastForward"),
        "RED" to listOf("Red"), "GREEN" to listOf("Green"), "YELLOW" to listOf("Yellow"), "BLUE" to listOf("Blue"),
    ) + (0..9).associate { "DIGIT:$it" to listOf("Num$it") }
}
