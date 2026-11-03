package `in`.s123dive.oneremote.core

/** Where a send came from, for the log. */
enum class Source { SCREEN, NOTIFICATION, TILE, WIDGET, VOLUME_KEYS, VOICE, MACRO, INTENT, DEBUG }

data class RouteRequest(
    val roomId: String,
    val role: Role,
    val cmd: Command,
    val source: Source = Source.SCREEN,
)

/** The outcome of routing one command, including which transport carried it. */
sealed interface RouteResult {
    data class Sent(val transport: TransportKind, val payload: String, val ms: Long) : RouteResult
    /** Nothing in the room / no driver / no transport can carry this command. */
    data class Unsupported(val reason: String) : RouteResult
    /** Every transport that supports the command failed. */
    data class Failed(val reason: String, val lastTransport: TransportKind?) : RouteResult

    val summary: String
        get() = when (this) {
            is Sent -> "OK"
            is Unsupported -> "UNSUPPORTED: $reason"
            is Failed -> "FAILED: $reason"
        }
}

/**
 * The single choke point for every command, whatever its origin (screen, notification,
 * tile, widget, volume keys, voice, macro, external intent). Resolves
 * (room, role) -> instance -> driver -> transport chain, tries transports in priority
 * order (Wi-Fi first, IR fallback when available), and logs every attempt.
 */
class Router(
    private val state: () -> AppState,
    private val registry: DeviceRegistry,
    private val log: SendLog,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun send(req: RouteRequest): RouteResult {
        val app = state()
        val room = app.room(req.roomId)
            ?: return unsupported(req, app, "no room '${req.roomId}'")
        val instanceId = room.instanceIdFor(req.role)
            ?: return unsupported(req, app, "room '${room.name}' has no ${req.role.name.lowercase()}")
        val instance = app.device(instanceId)
            ?: return unsupported(req, app, "device '$instanceId' missing")
        val device = registry.get(instance)
            ?: return unsupported(req, app, "no driver for ${instance.type.displayName} yet")

        val candidates = device.transports.filter { it.supports(req.cmd) }
        if (candidates.isEmpty()) {
            val have = device.transports.joinToString("/") { it.kind.badge }.ifEmpty { "none" }
            return unsupported(req, app, "${instance.name} (${have}) cannot ${req.cmd.key}")
        }

        var last: RouteResult.Failed? = null
        for (transport in candidates) {
            val started = clock()
            val payload = transport.describe(req.cmd)
            val result = try {
                transport.send(req.cmd)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                SendResult.Failed(payload, e.message ?: e::class.simpleName ?: "error")
            }
            val ms = clock() - started
            when (result) {
                is SendResult.Ok -> {
                    log(req, room.name, transport.kind.badge, result.payload, "OK", ms)
                    return RouteResult.Sent(transport.kind, result.payload, ms)
                }
                is SendResult.Failed -> {
                    log(req, room.name, transport.kind.badge, result.payload, "FAILED: ${result.reason}", ms)
                    last = RouteResult.Failed(result.reason, transport.kind)
                }
                is SendResult.Unsupported -> {
                    log(req, room.name, transport.kind.badge, payload, "UNSUPPORTED: ${result.reason}", ms)
                }
            }
        }
        return last ?: RouteResult.Unsupported("no transport accepted ${req.cmd.key}")
    }

    /**
     * Diagnostics (Debug console carrier test etc.) also go through here so that
     * no code path sends from the UI directly and everything is logged.
     */
    suspend fun diagnostic(label: String, transport: TransportKind, payload: String, block: suspend () -> SendResult): RouteResult {
        val started = clock()
        val result = try {
            block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            SendResult.Failed(payload, e.message ?: e::class.simpleName ?: "error")
        }
        val ms = clock() - started
        val entry = { text: String ->
            LogEntry(clock(), "-", Source.DEBUG.name, label, transport.badge, payload, text, ms)
        }
        return when (result) {
            is SendResult.Ok -> { log.add(entry("OK")); RouteResult.Sent(transport, result.payload, ms) }
            is SendResult.Failed -> { log.add(entry("FAILED: ${result.reason}")); RouteResult.Failed(result.reason, transport) }
            is SendResult.Unsupported -> { log.add(entry("UNSUPPORTED: ${result.reason}")); RouteResult.Unsupported(result.reason) }
        }
    }

    private fun unsupported(req: RouteRequest, app: AppState, reason: String): RouteResult.Unsupported {
        val roomName = app.room(req.roomId)?.name ?: req.roomId
        log(req, roomName, "-", "-", "UNSUPPORTED: $reason", 0)
        return RouteResult.Unsupported(reason)
    }

    private fun log(req: RouteRequest, room: String, transport: String, payload: String, result: String, ms: Long) {
        log.add(LogEntry(clock(), room, "${req.role}/${req.source.name.lowercase()}", req.cmd.key, transport, payload, result, ms))
    }
}
