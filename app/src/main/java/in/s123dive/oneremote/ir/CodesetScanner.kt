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
 * Sends one candidate code after another until the user says the box reacted. Works for a list
 * of code sets (their Power code) and for brute-force sweeps. Because the user's tap lags the
 * send, [recent] returns the last few candidates so the UI can re-send them one by one and
 * confirm which one it really was.
 */
class CodesetScanner(
    val candidates: List<NecSweep.Candidate>,
    private val send: suspend (NecSweep.Candidate) -> SendResult,
    private val intervalMs: Long = 1000,
) {
    data class State(
        val index: Int = 0,
        val total: Int,
        val running: Boolean = false,
        val finished: Boolean = false,
        val locked: NecSweep.Candidate? = null,
        val lastResult: String? = null,
    ) {
        val progress: Float get() = if (total == 0) 0f else index.toFloat() / total
    }

    private val _state = MutableStateFlow(State(total = candidates.size))
    val state: StateFlow<State> = _state
    val current: NecSweep.Candidate? get() = candidates.getOrNull(_state.value.index)
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true || candidates.isEmpty()) return
        _state.update { it.copy(running = true, finished = false, locked = null) }
        job = scope.launch {
            while (isActive) {
                val s = _state.value
                val c = candidates.getOrNull(s.index)
                if (c == null) { _state.update { it.copy(running = false, finished = true) }; return@launch }
                val result = send(c)
                _state.update { it.copy(lastResult = describe(result)) }
                if (result is SendResult.Failed && result.reason.contains("no IR blaster")) { pause(); return@launch }
                delay(intervalMs)
                if (!_state.value.running) return@launch
                _state.update { it.copy(index = it.index + 1) }
            }
        }
    }

    fun pause() { _state.update { it.copy(running = false) }; job?.cancel(); job = null }

    fun seek(index: Int) { _state.update { it.copy(index = index.coerceIn(0, candidates.size), finished = false) } }

    /** The box reacted around now: stop and hand back the candidate under the cursor. */
    fun lock(): NecSweep.Candidate? {
        val c = current ?: return null
        pause()
        _state.update { it.copy(locked = c) }
        return c
    }

    /** The last [n] candidates up to the cursor, newest first, for the verification step. */
    fun recent(n: Int = 4): List<NecSweep.Candidate> {
        val end = _state.value.index.coerceAtMost(candidates.lastIndex)
        return (end downTo (end - n + 1).coerceAtLeast(0)).mapNotNull { candidates.getOrNull(it) }
    }

    /** Remaining time at the configured interval. */
    fun etaSeconds(): Long = ((candidates.size - _state.value.index) * intervalMs) / 1000

    private fun describe(r: SendResult) = when (r) {
        is SendResult.Ok -> "sent ${r.payload}"
        is SendResult.Failed -> "failed: ${r.reason}"
        is SendResult.Unsupported -> "unsupported: ${r.reason}"
    }

    companion object {
        /** Candidates for a list of code sets: each one's Power code. */
        fun fromCodesets(codesets: List<Codeset>): List<NecSweep.Candidate> =
            codesets.mapNotNull { cs -> cs.code(Command.Power)?.let { NecSweep.Candidate(cs.name, it, cs.id) } }
    }
}
