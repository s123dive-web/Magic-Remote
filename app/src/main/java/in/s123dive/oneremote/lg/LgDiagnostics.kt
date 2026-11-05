package `in`.s123dive.oneremote.lg

import `in`.s123dive.oneremote.net.Http
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/**
 * Stage-by-stage probe of an LG TV so a failed pairing says exactly where it stops:
 * TCP 3000/3001, plain HTTP on 3000, TLS handshake on 3001 (protocol, cipher, cert, time),
 * then a real WebSocket open on each port.
 */
object LgDiagnostics {
    suspend fun run(host: String): List<String> = withContext(Dispatchers.IO) {
        val out = ArrayList<String>()
        for (port in intArrayOf(3000, 3001)) {
            val t = System.currentTimeMillis()
            out += try { Socket().use { it.connect(InetSocketAddress(host, port), 2000) }; "tcp $port: open (${System.currentTimeMillis() - t} ms)" }
            catch (e: Exception) { "tcp $port: ${e::class.simpleName}: ${e.message}" }
        }
        out += try {
            Http.plain.newCall(Request.Builder().url("http://$host:3000/").get().build()).execute().use { "http 3000 GET /: HTTP ${it.code} ${it.message}; server=${it.header("Server") ?: "-"}" }
        } catch (e: Exception) { "http 3000 GET /: ${e::class.simpleName}: ${e.message}" }
        out += tls(host, 3001)
        for (secure in booleanArrayOf(false, true)) out += wsOpen(host, secure)
        out
    }

    private fun tls(host: String, port: Int): String {
        val tm = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        val t = System.currentTimeMillis()
        return try {
            val ctx = SSLContext.getInstance("TLS").apply { init(null, arrayOf(tm), SecureRandom()) }
            val raw = Socket().apply { connect(InetSocketAddress(host, port), 3000) }
            (ctx.socketFactory.createSocket(raw, host, port, true) as SSLSocket).use { s ->
                s.soTimeout = 8000
                s.startHandshake()
                val cert = (s.session.peerCertificates.firstOrNull() as? X509Certificate)?.subjectX500Principal?.name ?: "?"
                "tls $port: ${s.session.protocol} ${s.session.cipherSuite} cert=$cert (${System.currentTimeMillis() - t} ms)"
            }
        } catch (e: Exception) { "tls $port: ${e::class.simpleName}: ${e.message} (${System.currentTimeMillis() - t} ms)" }
    }

    private suspend fun wsOpen(host: String, secure: Boolean): String {
        val url = if (secure) "wss://$host:3001" else "ws://$host:3000"
        val client = if (secure) Http.trustingHost(host, forWebSocket = true) else Http.webSocket
        val done = CompletableDeferred<String>()
        val t = System.currentTimeMillis()
        val ws = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { done.complete("ws open OK HTTP ${response.code} (${System.currentTimeMillis() - t} ms)"); webSocket.close(1000, "diag") }
            override fun onFailure(webSocket: WebSocket, t2: Throwable, response: Response?) { done.complete("ws failed: ${t2::class.simpleName}: ${t2.message}${response?.let { " HTTP ${it.code} ${it.message}" } ?: ""} (${System.currentTimeMillis() - t} ms)") }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { done.complete("ws closed by TV $code $reason (${System.currentTimeMillis() - t} ms)") }
        })
        val r = withTimeoutOrNull(12_000) { done.await() } ?: "ws: no reply in 12 s"
        ws.cancel()
        return "$url: $r"
    }
}
