package `in`.s123dive.oneremote.lg

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Pure builders and parsers for the LG SSAP protocol. Mirrors lgtv2 / aiowebostv. */
object SsapMessages {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** The permissions this app asks for (the spec list plus the two every pointer client needs). */
    val PERMISSIONS = listOf(
        "LAUNCH", "LAUNCH_WEBAPP", "APP_TO_APP", "CLOSE", "TEST_OPEN", "TEST_PROTECTED",
        "CONTROL_AUDIO", "CONTROL_DISPLAY", "CONTROL_INPUT_JOYSTICK", "CONTROL_INPUT_MEDIA_RECORDING",
        "CONTROL_INPUT_MEDIA_PLAYBACK", "CONTROL_INPUT_TV", "CONTROL_POWER", "CONTROL_INPUT_TEXT",
        "CONTROL_MOUSE_AND_KEYBOARD", "READ_APP_STATUS", "READ_CURRENT_CHANNEL", "READ_INPUT_DEVICE_LIST",
        "READ_NETWORK_STATE", "READ_RUNNING_APPS", "READ_TV_CHANNEL_LIST", "READ_INSTALLED_APPS",
        "WRITE_NOTIFICATION_TOAST", "READ_POWER_STATE", "READ_COUNTRY_INFO", "READ_SETTINGS",
        "CONTROL_TV_SCREEN", "CONTROL_TV_STANBY", "CONTROL_WOL", "READ_LGE_SDX", "READ_NOTIFICATIONS",
        "SEARCH", "WRITE_SETTINGS", "WRITE_NOTIFICATION_ALERT", "READ_UPDATE_INFO", "UPDATE_FROM_REMOTE_APP",
        "READ_LGE_TV_INPUT_EVENTS", "READ_TV_CURRENT_TIME", "TEST_SECURE",
    )

    fun hello(): String = """{"id":"hello","type":"hello","payload":{}}"""

    fun register(clientKey: String?, id: String = "register_0"): String {
        val payload = buildJsonObject {
            put("forcePairing", false)
            put("pairingType", "PROMPT")
            if (clientKey != null) put("client-key", clientKey)
            put("manifest", buildJsonObject {
                put("manifestVersion", 1)
                put("appVersion", "1.1")
                put("signed", buildJsonObject {
                    put("created", "20140509")
                    put("appId", "com.lge.test")
                    put("vendorId", "com.lge")
                    put("localizedAppNames", buildJsonObject { put("", "LG Remote App"); put("ko-KR", "리모컨 앱"); put("zxx-XX", "ЛГ Rэмotэ AПП") })
                    put("localizedVendorNames", buildJsonObject { put("", "LG Electronics") })
                    put("permissions", JsonArray(listOf("TEST_SECURE", "CONTROL_INPUT_TEXT", "CONTROL_MOUSE_AND_KEYBOARD", "READ_INSTALLED_APPS", "READ_LGE_SDX", "READ_NOTIFICATIONS", "SEARCH", "WRITE_SETTINGS", "WRITE_NOTIFICATION_ALERT", "CONTROL_POWER", "READ_CURRENT_CHANNEL", "READ_RUNNING_APPS", "READ_UPDATE_INFO", "UPDATE_FROM_REMOTE_APP", "READ_LGE_TV_INPUT_EVENTS", "READ_TV_CURRENT_TIME").map { JsonPrimitive(it) }))
                    put("serial", "2f930e2d2cfe083771f68e4fe7bb07")
                })
                put("permissions", JsonArray(PERMISSIONS.map { JsonPrimitive(it) }))
                put("signatures", buildJsonArray {
                    add(buildJsonObject {
                        put("signatureVersion", 1)
                        put("signature", LG_SIGNATURE)
                    })
                })
            })
        }
        return json.encodeToString(JsonObject.serializer(), buildJsonObject { put("id", id); put("type", "register"); put("payload", payload) })
    }

    fun request(id: String, uri: String, payload: JsonObject? = null): String =
        json.encodeToString(JsonObject.serializer(), buildJsonObject {
            put("id", id); put("type", "request"); put("uri", uri)
            if (payload != null) put("payload", payload)
        })

    data class Incoming(val id: String?, val type: String, val payload: JsonObject?, val error: String?) {
        val returnValue: Boolean? get() = payload?.get("returnValue")?.jsonPrimitive?.booleanOrNull
        val clientKey: String? get() = payload?.get("client-key")?.jsonPrimitive?.content
        val pairingPrompt: Boolean get() = type == "response" && payload?.get("pairingType")?.jsonPrimitive?.content == "PROMPT"
        val errorText: String? get() = error ?: payload?.get("errorText")?.jsonPrimitive?.content
    }

    fun parse(text: String): Incoming? = runCatching {
        val o = json.parseToJsonElement(text).jsonObject
        Incoming(
            id = o["id"]?.jsonPrimitive?.content,
            type = o["type"]?.jsonPrimitive?.content ?: return null,
            payload = o["payload"] as? JsonObject,
            error = o["error"]?.jsonPrimitive?.content,
        )
    }.getOrNull()

    // ---- pointer input socket: key:value lines ending with a blank line ----
    fun pointerButton(name: String) = "type:button\nname:$name\n\n"
    fun pointerMove(dx: Int, dy: Int, down: Boolean = false) = "type:move\ndx:$dx\ndy:$dy\ndown:${if (down) 1 else 0}\n\n"
    fun pointerClick() = "type:click\n\n"
    fun pointerScroll(dx: Int, dy: Int) = "type:scroll\ndx:$dx\ndy:$dy\n\n"

    fun obj(vararg pairs: Pair<String, Any?>): JsonObject = buildJsonObject {
        for ((k, v) in pairs) when (v) {
            null -> {}
            is String -> put(k, v)
            is Int -> put(k, v)
            is Boolean -> put(k, v)
            is JsonElement -> put(k, v)
            else -> put(k, v.toString())
        }
    }

    /** The published LG test-app signature (same as lgtv2 / pywebostv); the TV accepts it for pairing. */
    private const val LG_SIGNATURE = "eyJhbGdvcml0aG0iOiJSU0EtU0hBMjU2Iiwia2V5SWQiOiJ0ZXN0LXNpZ25pbmctY2VydCIsInNpZ25hdHVyZVZlcnNpb24iOjF9.hrVRgjCwXVvE2OOSpDZ58hR+59aFNwYDyjQgKk3auukd7pcegmE2CzPCa0bJ0ZsRAcKkCTJrWo5iDzNhMBWRyaMOv5zWSrthlf7G128qvIlpMT0YNY+n/FaOHE73uLrS/g7swl3/qH/BGFG2Hu4RlL48eb3lLKqTt2xKHdCs6Cd4RMfJPYnzgvI4BNrFUKsjkcu+WD4OO2A27Pq1n50cMchmcaXadJhGrOqH5YmHdOCj5NSHzJYrsW0HPlpuAx/ECMeIZYDh6RMqaFM2DXzdKX9NmmyqzJ3o/0lkk/N97gfVRLW5hA29yeAwaCViZNCP8iC9aO0q9fQojoa7NQnAtw=="
}
