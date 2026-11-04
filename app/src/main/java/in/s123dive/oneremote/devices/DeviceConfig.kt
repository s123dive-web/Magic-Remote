package `in`.s123dive.oneremote.devices

import `in`.s123dive.oneremote.data.AppStateJson
import `in`.s123dive.oneremote.ir.IrCode
import `in`.s123dive.oneremote.ir.codesets.Codeset
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Typed view over [`in`.s123dive.oneremote.core.DeviceInstance.config]. Keys are documented here
 * so every driver and screen agrees on them.
 */
class DeviceConfig(val json: JsonObject) {
    fun string(key: String): String? = (json[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content
    fun int(key: String): Int? = (json[key] as? JsonPrimitive)?.intOrNull
    fun bool(key: String): Boolean? = (json[key] as? JsonPrimitive)?.booleanOrNull
    fun has(key: String) = json.containsKey(key) && json[key] !is JsonNull

    fun with(key: String, value: String?): DeviceConfig = DeviceConfig(JsonObject(json + (key to (value?.let { JsonPrimitive(it) } ?: JsonNull))))
    fun with(key: String, value: Int): DeviceConfig = DeviceConfig(JsonObject(json + (key to JsonPrimitive(value))))
    fun with(key: String, value: Boolean): DeviceConfig = DeviceConfig(JsonObject(json + (key to JsonPrimitive(value))))
    fun with(key: String, value: JsonElement): DeviceConfig = DeviceConfig(JsonObject(json + (key to value)))
    fun without(key: String): DeviceConfig = DeviceConfig(JsonObject(json - key))

    // ---- IR ----
    val codesetId: String? get() = string(CODESET_ID)
    /** Expert-mode codes override / extend the built-in codeset. */
    val customCodes: Map<String, IrCode>
        get() = (json[CUSTOM_CODES] as? JsonObject)?.let {
            runCatching { AppStateJson.json.decodeFromJsonElement(MapSerializer(String.serializer(), IrCode.serializer()), it) }.getOrNull()
        } ?: emptyMap()
    fun withCustomCodes(codes: Map<String, IrCode>): DeviceConfig =
        with(CUSTOM_CODES, AppStateJson.json.encodeToJsonElement(MapSerializer(String.serializer(), IrCode.serializer()), codes))
    val digitDelayMs: Int? get() = int(DIGIT_DELAY_MS)

    /** The effective codeset: built-in (if any) overlaid with custom codes. */
    fun effectiveCodeset(builtIn: Codeset?, fallbackName: String, type: `in`.s123dive.oneremote.core.DeviceType): Codeset? {
        val custom = customCodes
        if (builtIn == null && custom.isEmpty()) return null
        val base = builtIn ?: Codeset("custom", "$fallbackName (expert)", type, emptyMap(), "expert mode")
        return base.copy(
            codes = base.codes + custom,
            digitDelayMs = digitDelayMs ?: base.digitDelayMs,
            source = if (custom.isEmpty()) base.source else base.source + " + ${custom.size} expert code(s)",
        )
    }

    // ---- Network ----
    val ip: String? get() = string(IP)
    val mac: String? get() = string(MAC)
    val model: String? get() = string(MODEL)
    val firmware: String? get() = string(FIRMWARE)
    // LG
    val clientKey: String? get() = string(CLIENT_KEY)
    val secure: Boolean get() = bool(SECURE) ?: false
    val webosVersion: String? get() = string(WEBOS_VERSION)
    // Sony
    val psk: String? get() = string(PSK)
    val textInputSupported: Boolean? get() = bool(TEXT_INPUT)
    val irccCodes: Map<String, String>
        get() = (json[IRCC_CODES] as? JsonObject)?.mapValues { it.value.jsonPrimitive.content } ?: emptyMap()
    fun withIrccCodes(codes: Map<String, String>) = with(IRCC_CODES, JsonObject(codes.mapValues { JsonPrimitive(it.value) }))
    val stringList: (String) -> List<String> = { key -> (json[key] as? JsonArray)?.map { it.jsonPrimitive.content } ?: emptyList() }

    /** Which TV input the STB is on, for the Watch TV macro (device-specific id). */
    val stbInput: String? get() = string(STB_INPUT)

    companion object {
        const val CODESET_ID = "codesetId"
        const val CUSTOM_CODES = "customCodes"
        const val DIGIT_DELAY_MS = "digitDelayMs"
        const val IP = "ip"
        const val MAC = "mac"
        const val MODEL = "model"
        const val FIRMWARE = "firmware"
        const val CLIENT_KEY = "clientKey"
        const val SECURE = "secure"
        const val WEBOS_VERSION = "webosVersion"
        const val PSK = "psk"
        const val TEXT_INPUT = "textInput"
        const val IRCC_CODES = "irccCodes"
        const val STB_INPUT = "stbInput"
        /** Keys whose values are secrets (backup export warns about these). */
        val SECRET_KEYS = setOf(CLIENT_KEY, PSK)

        fun empty() = DeviceConfig(JsonObject(emptyMap()))
    }
}
