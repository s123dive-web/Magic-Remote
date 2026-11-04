package `in`.s123dive.oneremote.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Small user preferences (not device config): Preferences DataStore. */
class SettingsStore(private val context: Context) {
    data class Settings(
        val haptics: Boolean = true,
        val notificationRemote: Boolean = false,
        val volumeKeys: Boolean = true,
        val lastRoomId: String? = null,
        val tvZoneCollapsed: Boolean = false,
        val sleepTimerAt: Long? = null,
        val sleepTimerRoom: String? = null,
        val voiceLanguage: String? = null,
    )

    val settings: Flow<Settings> = context.settingsDataStore.data.map { p ->
        Settings(
            haptics = p[HAPTICS] ?: true,
            notificationRemote = p[NOTIFICATION_REMOTE] ?: false,
            volumeKeys = p[VOLUME_KEYS] ?: true,
            lastRoomId = p[LAST_ROOM],
            tvZoneCollapsed = p[TV_COLLAPSED] ?: false,
            sleepTimerAt = p[SLEEP_AT],
            sleepTimerRoom = p[SLEEP_ROOM],
            voiceLanguage = p[VOICE_LANGUAGE],
        )
    }

    suspend fun setHaptics(enabled: Boolean) = context.settingsDataStore.edit { it[HAPTICS] = enabled }
    suspend fun setNotificationRemote(enabled: Boolean) = context.settingsDataStore.edit { it[NOTIFICATION_REMOTE] = enabled }
    suspend fun setVolumeKeys(enabled: Boolean) = context.settingsDataStore.edit { it[VOLUME_KEYS] = enabled }
    suspend fun setLastRoom(roomId: String) = context.settingsDataStore.edit { it[LAST_ROOM] = roomId }
    suspend fun setTvZoneCollapsed(collapsed: Boolean) = context.settingsDataStore.edit { it[TV_COLLAPSED] = collapsed }
    suspend fun setSleepTimer(roomId: String, at: Long) = context.settingsDataStore.edit { it[SLEEP_AT] = at; it[SLEEP_ROOM] = roomId }
    suspend fun setVoiceLanguage(tag: String) = context.settingsDataStore.edit { it[VOICE_LANGUAGE] = tag }
    suspend fun clearSleepTimer() = context.settingsDataStore.edit { it.remove(SLEEP_AT); it.remove(SLEEP_ROOM) }

    private companion object {
        val HAPTICS = booleanPreferencesKey("haptics")
        val NOTIFICATION_REMOTE = booleanPreferencesKey("notification_remote")
        val VOLUME_KEYS = booleanPreferencesKey("volume_keys")
        val LAST_ROOM = stringPreferencesKey("last_room")
        val TV_COLLAPSED = booleanPreferencesKey("tv_zone_collapsed")
        val SLEEP_AT = longPreferencesKey("sleep_at")
        val SLEEP_ROOM = stringPreferencesKey("sleep_room")
        val VOICE_LANGUAGE = stringPreferencesKey("voice_language")
    }
}
