package `in`.s123dive.oneremote.data

import `in`.s123dive.oneremote.core.AppState
import `in`.s123dive.oneremote.devices.DeviceConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Backup = the whole AppState plus a header. It includes LG client-keys and Sony PSKs, so the UI
 * warns before export. Import replaces or merges (merge keeps existing ids and adds the rest).
 */
object Backup {
    const val FORMAT = "oneremote-backup"

    @Serializable
    data class File(val format: String = FORMAT, val version: Int = 1, val exportedAt: Long, val state: AppState)

    fun export(state: AppState, now: Long = System.currentTimeMillis()): String =
        AppStateJson.json.encodeToString(File.serializer(), File(exportedAt = now, state = state))

    fun secretCount(state: AppState): Int = state.devices.count { d -> DeviceConfig.SECRET_KEYS.any { DeviceConfig(d.config).has(it) } }

    /** Same export with secrets blanked, for sharing a layout without credentials. */
    fun exportWithoutSecrets(state: AppState, now: Long = System.currentTimeMillis()): String {
        val stripped = state.copy(devices = state.devices.map { d -> d.copy(config = JsonObject(d.config.filterKeys { it !in DeviceConfig.SECRET_KEYS })) })
        return export(stripped, now)
    }

    sealed interface Parsed { data class Ok(val file: File) : Parsed; data class Err(val reason: String) : Parsed }

    fun parse(text: String): Parsed = runCatching {
        val f = AppStateJson.json.decodeFromString(File.serializer(), text)
        if (f.format != FORMAT) Parsed.Err("not a OneRemote backup (format '${f.format}')") else Parsed.Ok(f)
    }.getOrElse { Parsed.Err("unreadable backup: ${it.message?.take(100)}") }

    fun merge(current: AppState, incoming: AppState): AppState = AppState(
        rooms = current.rooms + incoming.rooms.filter { r -> current.rooms.none { it.id == r.id } },
        devices = current.devices + incoming.devices.filter { d -> current.devices.none { it.id == d.id } },
        macros = current.macros + incoming.macros.filter { m -> current.macros.none { it.id == m.id } },
    )
}
