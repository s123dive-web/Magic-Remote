package `in`.s123dive.oneremote.lg

import `in`.s123dive.oneremote.net.Http
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** What the TV told us about itself during the handshake. */
data class LgInfo(
    val modelName: String? = null,
    val webosVersion: String? = null,
    val firmware: String? = null,
    val secure: Boolean = false,
    val macAddress: String? = null,
)

/**
 * One LG webOS TV over SSAP. Connects lazily (ws:3000, then wss:3001 trusting only this host),
 * runs hello → getSystemInfo → register (the order newer firmware wants), waits for the user to
 * accept the on-screen prompt the first time, and hands the client-key back for persistence.
 * Requests are matched by id with a 3 s timeout. A dropped socket reconnects on the next send.
 */
class LgClient(
    val host: String,
    private var clientKey: String?,
    private var preferSecure: Boolean,
    private val onPaired: (clientKey: String, secure: Boolean, info: LgInfo) -> Unit,
    private val pairingTimeoutMs: Long = 60_000,
) : AutoCloseable {
    sealed interface State {
        data object Disconnected : State
        data object Connecting : State
        /** The TV is showing its "allow this remote?" prompt. */
        data object WaitingForPrompt : State
        data class Connected(val info: LgInfo) : State
        data class Failed(val reason: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Disconnected)
    val state: StateFlow<State> = _state
    var info: LgInfo = LgInfo(); private set
    val isConnected: Boolean get() = _state.value is State.Connected && socket != null

    private val mutex = Mutex()
    private var socket: WebSocket? = null
    private var pointerSocket: WebSocket? = null
    private var pointerOpen = CompletableDeferred<Boolean>()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<SsapMessages.Incoming>>()
    private companion object { const val BREAKER_MS = 15_000L }
    private val counter = AtomicInteger(1)
    private var registered = CompletableDeferred<SsapMessages.Incoming>()
    private var secure = preferSecure

    sealed interface Result<out T> {
        data class Ok<T>(val value: T) : Result<T>
        data class Err(val reason: String) : Result<Nothing>
    }

    /** After a failed connect the transport stays "down" this long so the Router falls to IR instantly. */
    var downUntil: Long = 0L; private set
    val isDown: Boolean get() = System.currentTimeMillis() < downUntil
    fun secondsUntilRetry(): Long = ((downUntil - System.currentTimeMillis()) / 1000).coerceAtLeast(0)

    /**
     * Connects and registers if needed. Safe to call repeatedly. ws:3000 and wss:3001 are tried
     * in parallel (newer firmware only answers on 3001; older only on 3000) and the first socket
     * that opens is used, so a dead port costs no time.
     */
    suspend fun connect(): Result<LgInfo> = mutex.withLock {
        if (isConnected) return Result.Ok(info)
        _state.value = State.Connecting
        val errors = ArrayList<String>()
        val opened = openFirst(errors)
        if (opened == null) {
            val lastError = errors.joinToString(" | ")
            downUntil = System.currentTimeMillis() + BREAKER_MS
            _state.value = State.Failed(lastError)
            return Result.Err(lastError)
        }
        secure = opened
        val r = handshake()
        if (r is Result.Ok) { downUntil = 0L; _state.value = State.Connected(info); return r }
        val reason = "${if (opened) "wss:3001" else "ws:3000"}: ${(r as Result.Err).reason}"
        closeSocketQuietly()
        downUntil = System.currentTimeMillis() + BREAKER_MS
        _state.value = State.Failed(reason)
        return Result.Err(reason)
    }

    /**
     * Opens both ports at once and returns the moment one is open (true = wss), cancelling the
     * other attempt. LG closes an unregistered socket after a few seconds, so the handshake must
     * start right away rather than wait for the losing port to time out.
     */
    private suspend fun openFirst(errors: MutableList<String>): Boolean? = kotlinx.coroutines.coroutineScope {
        val winner = CompletableDeferred<Boolean?>()
        val jobs = listOf(false, true).map { useSecure ->
            launch {
                val err = open(useSecure, onOpen = { winner.complete(useSecure) })
                if (err != null) synchronized(errors) { errors += err }
            }
        }
        launch { jobs.forEach { it.join() }; winner.complete(null) }
        val result = winner.await()
        if (result != null) jobs.forEach { it.cancel() } // loser is cancelled; open() cancels its socket
        result
    }

    /** Returns null on success (socket installed), else the reason. Cancellation closes the attempt. */
    private suspend fun open(useSecure: Boolean, onOpen: (WebSocket) -> Unit): String? {
        val url = if (useSecure) "wss://$host:3001" else "ws://$host:3000"
        val client = if (useSecure) Http.trustingHost(host, forWebSocket = true) else Http.webSocket
        val opened = CompletableDeferred<String?>()
        val ws = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                synchronized(this@LgClient) {
                    if (socket == null) { socket = webSocket; lastDrop = null; opened.complete(null); onOpen(webSocket) }
                    else { webSocket.cancel(); opened.complete("$url: another port already won") }
                }
            }
            override fun onMessage(webSocket: WebSocket, text: String) { if (webSocket === socket) handle(text) }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val http = response?.let { " (HTTP ${it.code} ${it.message})" } ?: ""
                opened.complete("$url: ${t::class.simpleName}: ${t.message}$http")
                if (webSocket === socket) dropped("${t::class.simpleName}: ${t.message}")
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { if (webSocket === socket) dropped("TV closed the socket ($code ${reason.ifBlank { "no reason" }})") }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, reason) }
        })
        try {
            val err = withTimeoutOrNull(Http.WS_CONNECT_SEC * 1000 + 2000) { opened.await() } ?: "$url: no TLS/HTTP reply in ${Http.WS_CONNECT_SEC + 2} s"
            if (err != null) ws.cancel()
            return err
        } catch (e: kotlinx.coroutines.CancellationException) {
            if (ws !== socket) ws.cancel()
            throw e
        }
    }

    private suspend fun handshake(): Result<LgInfo> {
        val ws = socket ?: return Result.Err("socket dropped right after opening: ${lastDrop ?: "unknown"}")
        registered = CompletableDeferred()
        var gathered = LgInfo(secure = secure)
        // hello (optional on older firmware)
        val hello = CompletableDeferred<SsapMessages.Incoming>().also { pending["hello"] = it }
        ws.send(SsapMessages.hello())
        withTimeoutOrNull(1000) { hello.await() }?.payload?.let { p ->
            gathered = gathered.copy(webosVersion = p["deviceOSVersion"]?.jsonPrimitive?.content ?: gathered.webosVersion)
        }
        pending.remove("hello")
        // getSystemInfo before registration (newer firmware requires it)
        val sys = CompletableDeferred<SsapMessages.Incoming>().also { pending["get_sys_info"] = it }
        ws.send(SsapMessages.request("get_sys_info", "ssap://system/getSystemInfo"))
        withTimeoutOrNull(1000) { sys.await() }?.payload?.let { p ->
            gathered = gathered.copy(modelName = p["modelName"]?.jsonPrimitive?.content ?: gathered.modelName)
        }
        pending.remove("get_sys_info")
        // register
        val reg = CompletableDeferred<SsapMessages.Incoming>().also { pending["register_0"] = it }
        ws.send(SsapMessages.register(clientKey))
        val first = withTimeoutOrNull(5000) { reg.await() } ?: return Result.Err("TV did not answer the register request")
        val final = when {
            first.type == "registered" -> first
            first.pairingPrompt -> {
                _state.value = State.WaitingForPrompt
                withTimeoutOrNull(pairingTimeoutMs) { registered.await() } ?: return Result.Err("pairing prompt not accepted on the TV within ${pairingTimeoutMs / 1000} s")
            }
            first.type == "error" -> return Result.Err("TV refused registration: ${first.errorText}")
            else -> return Result.Err("unexpected register reply type ${first.type}")
        }
        pending.remove("register_0")
        if (final.type == "error") return Result.Err("pairing rejected: ${final.errorText}")
        val key = final.clientKey ?: clientKey ?: return Result.Err("no client-key in registration reply")
        clientKey = key
        // webOS version + MAC after registration
        requestRaw("ssap://com.webos.service.update/getCurrentSWInformation")?.let { p ->
            val major = p["major_ver"]?.jsonPrimitive?.content; val minor = p["minor_ver"]?.jsonPrimitive?.content
            gathered = gathered.copy(
                firmware = listOfNotNull(major, minor).joinToString(".").ifBlank { null } ?: gathered.firmware,
                webosVersion = gathered.webosVersion ?: p["product_name"]?.jsonPrimitive?.content,
            )
        }
        requestRaw("ssap://com.webos.service.connectionmanager/getinfo")?.let { p ->
            gathered = gathered.copy(macAddress = macFromConnectionInfo(p))
        }
        info = gathered
        onPaired(key, secure, gathered)
        return Result.Ok(gathered)
    }

    private fun macFromConnectionInfo(p: JsonObject): String? {
        for (section in listOf("wiredInfo", "wifiInfo", "wired", "wifi")) {
            val obj = p[section] as? JsonObject ?: continue
            val mac = obj["macAddress"]?.jsonPrimitive?.content?.takeIf { it.length >= 12 && !it.startsWith("00:00:00") }
            val connected = obj["state"]?.jsonPrimitive?.content != "disconnected"
            if (mac != null && connected) return mac.uppercase()
        }
        return null
    }

    private fun handle(text: String) {
        val msg = SsapMessages.parse(text) ?: return
        if (msg.type == "registered") { registered.complete(msg); pending["register_0"]?.complete(msg); return }
        if (msg.type == "error" && msg.id == "register_0" && pending["register_0"]?.isCompleted == true) { registered.complete(msg); return }
        msg.id?.let { pending[it]?.complete(msg) }
    }

    @Volatile private var lastDrop: String? = null

    private fun dropped(reason: String) {
        lastDrop = reason
        socket = null
        pointerSocket = null
        pending.values.forEach { it.complete(SsapMessages.Incoming(null, "error", null, reason)) }
        pending.clear()
        if (_state.value !is State.Connecting) _state.value = State.Disconnected
    }

    private fun closeSocketQuietly() { runCatching { socket?.close(1000, "bye") }; socket = null }

    /** A request on the main socket, reconnecting once if needed. */
    suspend fun request(uri: String, payload: JsonObject? = null, timeoutMs: Long = Http.TIMEOUT_SEC * 1000): Result<JsonObject> {
        if (!isConnected) { val c = connect(); if (c is Result.Err) return c }
        val ws = socket ?: return Result.Err("not connected")
        val id = "req_${counter.getAndIncrement()}"
        val deferred = CompletableDeferred<SsapMessages.Incoming>().also { pending[id] = it }
        if (!ws.send(SsapMessages.request(id, uri, payload))) { pending.remove(id); return Result.Err("send failed (socket closed)") }
        val reply = withTimeoutOrNull(timeoutMs) { deferred.await() }
        pending.remove(id)
        return when {
            reply == null -> Result.Err("no reply to $uri in ${timeoutMs} ms")
            reply.type == "error" -> Result.Err(reply.errorText ?: "error")
            reply.returnValue == false -> Result.Err(reply.errorText ?: "returnValue=false")
            else -> Result.Ok(reply.payload ?: JsonObject(emptyMap()))
        }
    }

    private suspend fun requestRaw(uri: String): JsonObject? = (request(uri) as? Result.Ok)?.value

    /** Sends a pointer-socket message (button / move / click / scroll), opening the socket on first use. */
    suspend fun pointer(message: String): Result<Unit> {
        if (pointerSocket == null) {
            val r = request("ssap://com.webos.service.networkinput/getPointerInputSocket")
            val path = (r as? Result.Ok)?.value?.get("socketPath")?.jsonPrimitive?.content
                ?: return Result.Err("pointer socket unavailable: ${(r as? Result.Err)?.reason}")
            val client = if (path.startsWith("wss")) Http.trustingHost(host, forWebSocket = true) else Http.webSocket
            pointerOpen = CompletableDeferred()
            val ws = client.newWebSocket(Request.Builder().url(path).build(), object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) { pointerOpen.complete(true) }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { pointerOpen.complete(false); pointerSocket = null }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { pointerSocket = null }
            })
            val ok = withTimeoutOrNull(Http.TIMEOUT_SEC * 1000) { pointerOpen.await() } ?: false
            if (!ok) { ws.cancel(); return Result.Err("pointer socket did not open") }
            pointerSocket = ws
        }
        val ws = pointerSocket ?: return Result.Err("pointer socket closed")
        return if (ws.send(message)) Result.Ok(Unit) else { pointerSocket = null; Result.Err("pointer send failed") }
    }

    override fun close() {
        runCatching { pointerSocket?.close(1000, "bye") }; pointerSocket = null
        closeSocketQuietly()
        _state.value = State.Disconnected
    }

    @Volatile private var connecting = false

    /** Starts a connect in the background (one at a time); key presses use IR meanwhile. */
    fun connectInBackground(scope: kotlinx.coroutines.CoroutineScope) {
        if (connecting || isConnected || isDown) return
        connecting = true
        scope.launch { try { connect() } finally { connecting = false } }
    }

    /** Lets the pair screen retry immediately instead of waiting out the breaker. */
    fun resetBreaker() { downUntil = 0L }
}
