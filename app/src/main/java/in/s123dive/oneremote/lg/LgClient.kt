package `in`.s123dive.oneremote.lg

import `in`.s123dive.oneremote.net.Http
import kotlinx.coroutines.CompletableDeferred
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
    private val counter = AtomicInteger(1)
    private var registered = CompletableDeferred<SsapMessages.Incoming>()
    private var secure = preferSecure

    sealed interface Result<out T> {
        data class Ok<T>(val value: T) : Result<T>
        data class Err(val reason: String) : Result<Nothing>
    }

    /** Connects and registers if needed. Safe to call repeatedly. */
    suspend fun connect(): Result<LgInfo> = mutex.withLock {
        if (isConnected) return Result.Ok(info)
        _state.value = State.Connecting
        val order = if (preferSecure) listOf(true, false) else listOf(false, true)
        var lastError = "no attempt"
        for (useSecure in order) {
            val opened = open(useSecure)
            if (opened != null) { lastError = opened; continue }
            secure = useSecure
            val r = handshake()
            if (r is Result.Ok) { _state.value = State.Connected(info); return r }
            lastError = (r as Result.Err).reason
            closeSocketQuietly()
            if (!lastError.startsWith("connection")) break // protocol-level failure: switching port will not help
        }
        _state.value = State.Failed(lastError)
        return Result.Err(lastError)
    }

    /** Returns null on success, else the reason. */
    private suspend fun open(useSecure: Boolean): String? {
        val url = if (useSecure) "wss://$host:3001" else "ws://$host:3000"
        val client = if (useSecure) Http.trustingHost(host, forWebSocket = true) else Http.webSocket
        val opened = CompletableDeferred<String?>()
        val ws = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { opened.complete(null) }
            override fun onMessage(webSocket: WebSocket, text: String) { handle(text) }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                opened.complete("connection to $url failed: ${t.message ?: t::class.simpleName}")
                dropped("socket failure: ${t.message}")
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { dropped("closed $code $reason") }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, reason) }
        })
        val err = withTimeoutOrNull(Http.TIMEOUT_SEC * 1000 + 500) { opened.await() } ?: "connection to $url timed out"
        if (err != null) { ws.cancel(); return err }
        socket = ws
        return null
    }

    private suspend fun handshake(): Result<LgInfo> {
        val ws = socket ?: return Result.Err("no socket")
        registered = CompletableDeferred()
        var gathered = LgInfo(secure = secure)
        // hello (optional on older firmware)
        val hello = CompletableDeferred<SsapMessages.Incoming>().also { pending["hello"] = it }
        ws.send(SsapMessages.hello())
        withTimeoutOrNull(2000) { hello.await() }?.payload?.let { p ->
            gathered = gathered.copy(webosVersion = p["deviceOSVersion"]?.jsonPrimitive?.content ?: gathered.webosVersion)
        }
        pending.remove("hello")
        // getSystemInfo before registration (newer firmware requires it)
        val sys = CompletableDeferred<SsapMessages.Incoming>().also { pending["get_sys_info"] = it }
        ws.send(SsapMessages.request("get_sys_info", "ssap://system/getSystemInfo"))
        withTimeoutOrNull(2000) { sys.await() }?.payload?.let { p ->
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

    private fun dropped(reason: String) {
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
}
