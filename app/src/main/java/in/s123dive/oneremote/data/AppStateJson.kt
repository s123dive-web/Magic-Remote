package `in`.s123dive.oneremote.data

import `in`.s123dive.oneremote.core.AppState
import kotlinx.serialization.json.Json

/**
 * The one JSON configuration used for persistence and backup. Lenient on read so a
 * newer build's extra fields never brick an older one; explicit `type` discriminator
 * for the Command / MacroStep hierarchies.
 */
object AppStateJson {
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
        classDiscriminator = "type"
        isLenient = false
    }

    fun encode(state: AppState): String = json.encodeToString(AppState.serializer(), state)

    fun decode(text: String): AppState = json.decodeFromString(AppState.serializer(), text)
}
