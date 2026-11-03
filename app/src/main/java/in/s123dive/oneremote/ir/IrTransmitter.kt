package `in`.s123dive.oneremote.ir

import `in`.s123dive.oneremote.core.SendResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The only object that calls the IR HAL. Sends are serialised (one at a time) because
 * `ConsumerIrManager.transmit` blocks and overlapping calls garble the output.
 * Checks capability first so failures are explicit, never silent.
 */
class IrTransmitter(
    private val port: IrPort,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()
    val capability: IrCapability get() = port.capability

    suspend fun send(carrierHz: Int, pattern: IntArray, payload: String): SendResult {
        val cap = port.capability
        if (!cap.hasIr) return SendResult.Failed(payload, "this phone has no IR blaster")
        if (!cap.supports(carrierHz)) {
            return SendResult.Failed(payload, "${carrierHz / 1000.0} kHz not supported (ranges: ${cap.describeRanges()})")
        }
        if (pattern.isEmpty()) return SendResult.Failed(payload, "empty pattern")
        return mutex.withLock {
            withContext(dispatcher) {
                try {
                    port.transmit(carrierHz, pattern)
                    SendResult.Ok(payload)
                } catch (e: Exception) {
                    SendResult.Failed(payload, "IR HAL error: ${e.message ?: e::class.simpleName}")
                }
            }
        }
    }
}
