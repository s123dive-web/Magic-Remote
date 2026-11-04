package `in`.s123dive.oneremote.ir

import `in`.s123dive.oneremote.core.Command
import `in`.s123dive.oneremote.core.SendResult
import `in`.s123dive.oneremote.ir.codesets.Codeset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Walks a list of candidate codesets, sending each one's Power code every [intervalMs]
 * until the user taps "It responded" ([lock]) or the list ends. The phone cannot learn IR,
 * so this is how an unknown box gets its codeset.
 */
class CodesetScanner(
    private val candidates: List<Codeset>,
    private val send: suspend (Codeset, IrCode) -> SendResult,
    private val intervalMs: Long = 1000,
) {
    data class State(
        val index: Int = 0,
        val total: Int,
        val running: Boolean = false,
        val finished: Boolean = false,
        val locked: Codeset? = null,
        val lastResult: String? = null,
    ) {
        val progress: Float get() = if (total == 0) 0f else index.toFloat() / total
    }

    private val _state = MutableStateFlow(State(total = candidates.size))
    val state: StateFlow<State> = _state
    val current: Codeset? get() = candidates.getOrNull(_state.value.index)
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true || candidates.isEmpty()) return
        _state.update { it.copy(running = true, finished = false, locked = null) }
        job = scope.launch {
            while (isActive) {
                val s = _state.value
                val cs = candidates.getOrNull(s.index)
                if (cs == null) { _state.update { it.copy(running = false, finished = true) }; return@launch }
                val code = cs.code(Command.Power)
                val result = if (code == null) SendResult.Unsupported("no Power code in ${cs.id}") else send(cs, code)
                _state.update { it.copy(lastResult = describe(result)) }
                delay(intervalMs)
                if (!_state.value.running) return@launch
                _state.update { it.copy(index = it.index + 1) }
            }
        }
    }

    fun pause() { _state.update { it.copy(running = false) }; job?.cancel(); job = null }

    /** Jump to a candidate (used by Skip / Previous). */
    fun seek(index: Int) { _state.update { it.copy(index = index.coerceIn(0, candidates.size), finished = false) } }

    /** The box reacted to the last Power code: that codeset wins. */
    fun lock(): Codeset? {
        val cs = current ?: return null
        pause()
        _state.update { it.copy(locked = cs) }
        return cs
    }

    private fun describe(r: SendResult) = when (r) {
        is SendResult.Ok -> "sent ${r.payload}"
        is SendResult.Failed -> "failed: ${r.reason}"
        is SendResult.Unsupported -> "unsupported: ${r.reason}"
    }
}
