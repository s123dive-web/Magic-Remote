package androidx.datastore.preferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlin.properties.ReadOnlyProperty
fun preferencesDataStore(name: String): ReadOnlyProperty<android.content.Context, DataStore<Preferences>> = ReadOnlyProperty { _, _ -> error("stub") }
