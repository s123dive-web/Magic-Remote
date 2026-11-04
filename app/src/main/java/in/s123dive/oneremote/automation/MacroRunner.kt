package `in`.s123dive.oneremote.automation

import `in`.s123dive.oneremote.core.Macro
import `in`.s123dive.oneremote.core.MacroStep
import `in`.s123dive.oneremote.core.RouteRequest
import `in`.s123dive.oneremote.core.RouteResult
import `in`.s123dive.oneremote.core.Router
import `in`.s123dive.oneremote.core.Source
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Runs a macro step by step through the Router (so every send is logged), one macro at a time. */
class MacroRunner(private val router: Router, private val delayFn: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) }) {
    data class Progress(val macro: Macro, val step: Int, val results: List<RouteResult>) {
        val done: Boolean get() = step >= macro.steps.size
        val failures: Int get() = results.count { it !is RouteResult.Sent }
    }

    private val _running = MutableStateFlow<Progress?>(null)
    val running: StateFlow<Progress?> = _running

    suspend fun run(macro: Macro, source: Source = Source.MACRO): List<RouteResult> {
        if (_running.value?.done == false) return listOf(RouteResult.Failed("another macro is running", null))
        val results = ArrayList<RouteResult>()
        _running.value = Progress(macro, 0, results.toList())
        try {
            for ((i, step) in macro.steps.withIndex()) {
                when (step) {
                    is MacroStep.Delay -> delayFn(step.ms)
                    is MacroStep.Send -> results += router.send(RouteRequest(macro.roomId, step.role, step.cmd, source))
                }
                _running.value = Progress(macro, i + 1, results.toList())
            }
        } finally {
            _running.value = Progress(macro, macro.steps.size, results.toList())
        }
        return results
    }
}
