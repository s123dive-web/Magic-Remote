package `in`.s123dive.oneremote.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

/** One line in the Debug console. Everything that leaves the phone produces one. */
@Serializable
data class LogEntry(
    val time: Long,
    val room: String,
    val role: String,
    val cmd: String,
    val transport: String,
    val payload: String,
    val result: String,
    val ms: Long,
) {
    val ok: Boolean get() = result == "OK"
}

/**
 * Fixed-size ring buffer of the most recent sends, observable from the UI.
 * Thread-safe; newest entry first in [entries].
 */
class SendLog(val capacity: Int = 500) {
    private val lock = Any()
    private val buffer = ArrayDeque<LogEntry>(capacity)
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries

    fun add(entry: LogEntry) {
        synchronized(lock) {
            if (buffer.size == capacity) buffer.removeFirst()
            buffer.addLast(entry)
            _entries.value = buffer.asReversed().toList()
        }
    }

    fun clear() = synchronized(lock) {
        buffer.clear()
        _entries.update { emptyList() }
    }

    /** Plain-text export, oldest first, one line per entry. */
    fun export(formatTime: (Long) -> String = { it.toString() }): String =
        synchronized(lock) {
            buffer.joinToString("\n") { e ->
                "${formatTime(e.time)}\t${e.room}\t${e.role}\t${e.cmd}\t${e.transport}\t${e.payload}\t${e.result}\t${e.ms}ms"
            }
        }
}
