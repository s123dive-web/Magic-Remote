package `in`.s123dive.oneremote.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import `in`.s123dive.oneremote.core.AppState
import `in`.s123dive.oneremote.core.LogEntry
import `in`.s123dive.oneremote.core.SendLog
import kotlinx.serialization.SerializationException
import java.io.InputStream
import java.io.OutputStream

/** DataStore serializer: the AppState JSON document on disk. */
object AppStateSerializer : Serializer<AppState> {
    override val defaultValue: AppState = AppState()

    override suspend fun readFrom(input: InputStream): AppState {
        val text = input.readBytes().decodeToString()
        if (text.isBlank()) return defaultValue
        return try {
            AppStateJson.decode(text)
        } catch (e: SerializationException) {
            throw CorruptionException("app_state.json unreadable", e)
        } catch (e: IllegalArgumentException) {
            throw CorruptionException("app_state.json invalid", e)
        }
    }

    override suspend fun writeTo(t: AppState, output: OutputStream) {
        output.write(AppStateJson.encode(t).encodeToByteArray())
    }
}

object AppStateStore {
    const val FILE_NAME = "app_state.json"

    /**
     * A corrupt file is replaced with an empty state, and the event is written to the
     * send log so the Debug console shows it instead of the data vanishing silently.
     */
    fun create(context: Context, log: SendLog): DataStore<AppState> =
        DataStoreFactory.create(
            serializer = AppStateSerializer,
            corruptionHandler = ReplaceFileCorruptionHandler { e ->
                log.add(
                    LogEntry(
                        time = System.currentTimeMillis(), room = "-", role = "STORE", cmd = "load",
                        transport = "-", payload = FILE_NAME,
                        result = "FAILED: corrupt, replaced with empty (${e.message})", ms = 0,
                    ),
                )
                AppState()
            },
            produceFile = { context.dataStoreFile(FILE_NAME) },
        )
}
