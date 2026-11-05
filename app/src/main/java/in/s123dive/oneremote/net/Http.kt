package `in`.s123dive.oneremote.net

import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/** Shared OkHttp clients: every LAN call has a 3 s budget per phase of the request. */
object Http {
    const val TIMEOUT_SEC = 3L
    /** LG TVs take seconds to finish a TLS handshake on 3001; the WebSocket open gets a longer budget. */
    const val WS_CONNECT_SEC = 8L

    /** Embedded WebSocket servers (LG) mishandle permessage-deflate; never offer it. */
    private val noWsCompression = okhttp3.Interceptor { chain ->
        val req = chain.request()
        if (req.header("Sec-WebSocket-Extensions") != null) chain.proceed(req.newBuilder().removeHeader("Sec-WebSocket-Extensions").build()) else chain.proceed(req)
    }

    val plain: OkHttpClient = OkHttpClient.Builder()
        .addNetworkInterceptor(noWsCompression)
        .connectTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
        .callTimeout(TIMEOUT_SEC + 1, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    /**
     * A client that accepts the self-signed certificate of exactly one host (LG TVs on wss:3001).
     * Any other hostname fails verification, so the trust relaxation cannot leak to other traffic.
     */
    fun trustingHost(host: String, forWebSocket: Boolean = false): OkHttpClient {
        val tm = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                if (chain.isEmpty()) throw java.security.cert.CertificateException("empty chain from $host")
            }
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        val ctx = SSLContext.getInstance("TLS").apply { init(null, arrayOf(tm), SecureRandom()) }
        // LG's embedded TLS stack: TLS 1.2 with every cipher the platform has (OkHttp's modern list is too narrow).
        val spec = okhttp3.ConnectionSpec.Builder(okhttp3.ConnectionSpec.COMPATIBLE_TLS)
            .tlsVersions(okhttp3.TlsVersion.TLS_1_2)
            .allEnabledCipherSuites()
            .build()
        val b = plain.newBuilder()
            .sslSocketFactory(ctx.socketFactory, tm)
            .connectionSpecs(listOf(spec, okhttp3.ConnectionSpec.CLEARTEXT))
            .hostnameVerifier { name, _ -> name == host }
        if (forWebSocket) b.connectTimeout(WS_CONNECT_SEC, TimeUnit.SECONDS).readTimeout(0, TimeUnit.MILLISECONDS).callTimeout(0, TimeUnit.MILLISECONDS).pingInterval(15, TimeUnit.SECONDS)
        return b.build()
    }

    /** Plain client tuned for long-lived WebSockets (no read timeout, keepalive pings). */
    val webSocket: OkHttpClient = plain.newBuilder()
        .connectTimeout(WS_CONNECT_SEC, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()
}
