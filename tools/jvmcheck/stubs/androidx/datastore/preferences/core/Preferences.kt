package androidx.datastore.preferences.core
import androidx.datastore.core.DataStore
abstract class Preferences { class Key<T>(val name: String); abstract operator fun <T> get(key: Key<T>): T? }
abstract class MutablePreferences : Preferences() { abstract operator fun <T> set(key: Key<T>, value: T); abstract fun remove(key: Key<*>) }
fun booleanPreferencesKey(name: String): Preferences.Key<Boolean> = Preferences.Key(name)
fun stringPreferencesKey(name: String): Preferences.Key<String> = Preferences.Key(name)
fun intPreferencesKey(name: String): Preferences.Key<Int> = Preferences.Key(name)
fun longPreferencesKey(name: String): Preferences.Key<Long> = Preferences.Key(name)
suspend fun DataStore<Preferences>.edit(transform: suspend (MutablePreferences) -> Unit): Preferences = error("stub")
