package `in`.s123dive.oneremote.core

/** Outcome of one attempt on one transport. */
sealed interface SendResult {
    /** [payload] is a short human-readable summary, e.g. `NEC 0x04/0x08` or `ssap://audio/volumeUp`. */
    data class Ok(val payload: String) : SendResult
    /** The transport could not deliver (unreachable, timeout, IO error). The router may fall back. */
    data class Failed(val payload: String, val reason: String) : SendResult
    /** The transport does not carry this command at all. */
    data class Unsupported(val reason: String) : SendResult
}

/** One way of reaching a device: an IR codeset, a WebSocket session, an HTTP API. */
interface Transport {
    val kind: TransportKind
    val capabilities: Set<Capability>
    fun supports(cmd: Command): Boolean = cmd.capability in capabilities
    /** Short payload summary for logs, computed without sending. */
    fun describe(cmd: Command): String
    suspend fun send(cmd: Command): SendResult
    suspend fun isReachable(): Boolean
}

/** A live driver for a [DeviceInstance]. */
interface Device {
    val instance: DeviceInstance
    /** Transports in priority order; the first that supports a command and succeeds wins. */
    val transports: List<Transport>
    val capabilities: Set<Capability> get() = transports.flatMapTo(mutableSetOf()) { it.capabilities }
    suspend fun isReachable(): Boolean = transports.any { it.isReachable() }
}

/** Produces drivers for instances. Returns null when no driver exists for the type yet. */
fun interface DeviceRegistry {
    fun get(instance: DeviceInstance): Device?
}
