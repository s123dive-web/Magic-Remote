package `in`.s123dive.oneremote.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** The kinds of device this app knows how to drive. */
@Serializable
enum class DeviceType(val displayName: String) {
    AIRTEL_HD("Airtel Digital TV HD box"),
    LG_WEBOS("LG webOS TV"),
    SONY_BRAVIA("Sony Bravia"),
    ATOMBERG_FAN("Atomberg BLDC fan"),
}

/** How a command physically leaves the phone. */
@Serializable
enum class TransportKind(val badge: String) {
    WIFI("Wi-Fi"),
    IR("IR"),
}

/**
 * One configured device. There may be any number of these, of any type, across rooms.
 *
 * [transports] is in priority order (Wi-Fi first, IR fallback). [config] is
 * type-specific JSON (IP, codeset id, PSK, client-key, ...). Secrets live here and
 * therefore only ever in DataStore; backup export warns before including them.
 */
@Serializable
data class DeviceInstance(
    val id: String,
    val roomId: String,
    val name: String,
    val type: DeviceType,
    val transports: List<TransportKind>,
    val config: JsonObject = JsonObject(emptyMap()),
    val mac: String? = null,
)
